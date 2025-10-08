# Prereqs

* aws cli with proper IAM user
* TelegramBot token

## Create aws secret with telegram bot token

```bash
aws ssm put-parameter \
    --name "/telegram-bot/token" \
    --value "YOUR_BOT_TOKEN_HERE" \
    --type SecureString \
    --overwrite \
    --description "Telegram bot token for price alerts"
```

```bash
aws ssm put-parameter \
    --name "/telegram-bot/webhook-secret" \
    --value "YOUR_WEBHOOK_SECRET_HERE" \
    --type SecureString \
    --overwrite \
    --description "Telegram bot webhook secret for price alerts"
```


## Create DynamoDB table

```bash
# Create the table
aws dynamodb create-table \
    --table-name PriceAlerts \
    --attribute-definitions \
        AttributeName=alertId,AttributeType=S \
        AttributeName=chatId,AttributeType=S \
        AttributeName=status,AttributeType=S \
    --key-schema \
        AttributeName=alertId,KeyType=HASH \
    --global-secondary-indexes \
        '[ 
            { 
                "IndexName": "ChatIdIndex", 
                "KeySchema": [ 
                    {"AttributeName": "chatId", "KeyType": "HASH"} 
                ], 
                "Projection": { 
                    "ProjectionType": "ALL" 
                } 
            }, 
            { 
                "IndexName": "StatusIndex", 
                "KeySchema": [ 
                    {"AttributeName": "status", "KeyType": "HASH"} 
                ], 
                "Projection": { 
                    "ProjectionType": "ALL" 
                } 
            } 
        ]' \
    --billing-mode PAY_PER_REQUEST \
    --tags Key=Project,Value=TelegramPriceBot
```

## Create Lambda IAM Role

1. Create role
```bash
aws iam create-role \
    --role-name telegram-price-bot-lambda-role \
    --assume-role-policy-document file://lambda-trust-policy.json
```

2. Attach managed policies (quick way)

```bash
aws iam attach-role-policy \
    --role-name telegram-price-bot-lambda-role \
    --policy-arn arn:aws:iam::aws:policy/AWSLambda_FullAccess
```    

```bash
aws iam attach-role-policy \
    --role-name telegram-price-bot-lambda-role \
    --policy-arn arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess
```

```bash
aws iam attach-role-policy \
    --role-name telegram-price-bot-lambda-role \
    --policy-arn arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess
```

```bash
aws iam attach-role-policy \
    --role-name telegram-price-bot-lambda-role \
    --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
```

```bash
aws iam attach-role-policy \
  --role-name telegram-price-bot-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess
````

3. Get ARN

```bash
export ROLE_ARN=$(aws iam get-role --role-name telegram-price-bot-lambda-role --query 'Role.Arn' --output text)
```

4. Build and create/update Lambda functions

```bash
export JAR_FILE=target/telegram-price-bot-1.0.0.jar
```

```bash
aws lambda create-function \
    --function-name telegram-price-bot-frontend \
    --runtime java21 \
    --role $ROLE_ARN \
    --handler ru.ingvord.trading.telegram.TelegramWebhookHandler::handleRequest \
    --zip-file fileb://$JAR_FILE \
    --timeout 30 \
    --memory-size 512 \
    --environment Variables="{ALERTS_TABLE=PriceAlerts,WEBHOOK_SECRET_TOKEN_PARAM=/telegram-bot/webhook-secret,TELEGRAM_BOT_TOKEN_PARAM=/telegram-bot/token}" \
    --description "Telegram bot frontend for price alerts"
```

```bash
aws lambda create-function \
    --function-name telegram-price-bot \
    --runtime java21 \
    --role $ROLE_ARN \
    --handler ru.ingvord.trading.telegram.TelegramPriceBot::handleRequest \
    --zip-file fileb://$JAR_FILE \
    --timeout 30 \
    --memory-size 512 \
    --environment Variables="{ALERTS_TABLE=PriceAlerts,TELEGRAM_BOT_TOKEN_PARAM=/telegram-bot/token}" \
    --description "Telegram bot frontend for price alerts"
```

```bash
aws lambda update-function-code \
--function-name telegram-price-bot-frontend \
--zip-file fileb://$JAR_FILE
```

Suspend existing lambda:

```bash
aws lambda put-function-concurrency --function-name telegram-price-bot-frontend --reserved-concurrent-executions 0
```

5. Create a rule that triggers every 15 minutes

```bash
aws events put-rule \
    --name telegram-price-bot-checker-schedule \
    --schedule-expression "rate(15 minutes)" \
    --description "Trigger price checker every 15 minutes" \
    --state ENABLED
```

6. Get your account info and add permission

```bash
REGION=$(aws configure get region)
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws lambda add-permission \
    --function-name telegram-price-bot \
    --statement-id eventbridge-scheduled-invoke \
    --action lambda:InvokeFunction \
    --principal events.amazonaws.com \
    --source-arn "arn:aws:events:$REGION:$ACCOUNT_ID:rule/telegram-price-bot-checker-schedule"
```

```bash
aws events put-targets \
    --rule telegram-price-bot-checker-schedule \
    --targets "Id"="1","Arn"="arn:aws:lambda:$REGION:$ACCOUNT_ID:function:telegram-price-bot"
```

7. Create WAS api gateway

```bash
REGION=$(aws configure get region)
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

API_ID=$(aws apigatewayv2 create-api \
    --name telegram-price-bot-api \
    --protocol-type HTTP \
    --target arn:aws:lambda:$REGION:$ACCOUNT_ID:function:telegram-price-bot-frontend \
    --query 'ApiId' \
    --output text)
```

```bash
REGION=$(aws configure get region)
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
API_ID=$(aws apigatewayv2 get-apis --query 'Items[?Name==`telegram-price-bot-api`].ApiId' --output text)

# Add permission
aws lambda add-permission \
    --function-name telegram-price-bot-frontend \
    --statement-id apigateway-invoke-telegram \
    --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:$REGION:$ACCOUNT_ID:$API_ID/*/*"
```

8. Link Telegram webhook

```bash
BOT_TOKEN=$(aws ssm get-parameter \
    --name "/telegram-bot/token" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text)

WEBHOOK_TOKEN=$(aws ssm get-parameter \
    --name "/telegram-bot/webhook-secret" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text)

API_ID=$(aws apigatewayv2 get-apis --query 'Items[?Name==`telegram-price-bot-api`].ApiId' --output text)
API_ENDPOINT=$(aws apigatewayv2 get-api --api-id $API_ID --query 'ApiEndpoint' --output text)

echo "Setting webhook to: $API_ENDPOINT"

curl -X POST "https://api.telegram.org/bot$BOT_TOKEN/setWebhook?secret_token=$WEBHOOK_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"url\":\"$API_ENDPOINT\"}"
```