# Prereqs

* aws cli with proper IAM user
* TelegramBot token

## Create aws secret with telegram bot token

```
# Store token securely in Parameter Store
aws ssm put-parameter \
    --name "/telegram-bot/token" \
    --value "YOUR_BOT_TOKEN_HERE" \
    --type SecureString \
    --description "Telegram bot token for price alerts"
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
    --environment Variables="{ALERTS_TABLE=PriceAlerts,BOT_TOKEN_PARAM=/telegram-bot/token}" \
    --description "Telegram bot frontend for price alerts"
```

```bash
aws lambda update-function-code \
--function-name telegram-price-bot-frontend \
--zip-file fileb://$JAR_FILE
```