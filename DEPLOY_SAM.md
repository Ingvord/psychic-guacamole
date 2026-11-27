# Deploying with SAM (infrastructure as code)

`template.yaml` + `samconfig.toml` describe the stack `psychic-guacamole` in eu-central-1:

| Resource | Notes |
|---|---|
| `TelegramWebhookHandlerNative` | native image, handles Telegram updates (`POST /webhook`) |
| `PriceCheckerNative` | same native image, `rate(15 minutes)` schedule (`ScheduleState` parameter) |
| `HttpApi` | HTTP API in front of the webhook |
| 2 log groups | 30-day retention |

**Not managed by the stack** (referenced only): the DynamoDB table `PriceAlerts` (real user data; parameter
`AlertsTableName`) and the SSM SecureStrings `/telegram-bot/token` and `/telegram-bot/webhook-secret`
(parameters `TelegramTokenParam`, `WebhookSecretParam`). Functions receive the *paths* (`ssm:/telegram-bot/token`)
and resolve them at runtime, so no secret value ever lands in a function's environment.

Bybit needs no key. The checker needs outbound internet (no VPC).

## Day-to-day deploy
```bash
mvn test
sdk use java 21-graalce && mvn -Pnative package -DskipTests   # target/function.zip, used by both functions
sam validate --lint
sam deploy                                                    # shows a change set and asks to confirm
```
`sam deploy` uploads `function.zip` through S3 (managed bucket), so there is no direct-upload size limit.
Code reaches Lambda only through `sam deploy`; the Maven `deploy` step that used to call `aws lambda update-function-code` was removed.

## One-time migration from the hand-made setup (side by side)
The old hand-made resources stayed untouched until the end, so rollback was flipping two switches (they have since been deleted, see step 4):
- `telegram-price-bot-frontend-native`: the **live webhook** (native; API `telegram-price-bot-api`, route `ANY /`; its secret and
  token are the SSM references `/telegram-bot/webhook-secret` and `/telegram-bot/token`)
- `telegram-price-bot-frontend`: Java 21 webhook, only reached through that API's `$default` route (other paths)
- `telegram-price-bot-native`: the checker, triggered by the rule `telegram-price-bot-checker-schedule`
- `telegram-price-bot` (Java 21): not referenced by the API or the rule; check what uses it before deleting.

**0. Prerequisites and a record of the current state**
```bash
aws sts get-caller-identity && aws configure get region          # eu-central-1
aws ssm get-parameters --names /telegram-bot/token /telegram-bot/webhook-secret \
  --query 'Parameters[].Name'                                    # both must be listed; else pass the real paths
                                                                 # with --parameter-overrides TelegramTokenParam=...

# remember where Telegram points today: this is your rollback target
TOKEN=$(aws ssm get-parameter --name /telegram-bot/token --with-decryption --query Parameter.Value --output text)
printf 'url = "https://api.telegram.org/bot%s/getWebhookInfo"\n' "$TOKEN" | curl -s -K - | jq '.result | {url, pending_update_count, last_error_message}'
```

**1. First deploy, schedule disabled** (during the migration `samconfig.toml` had `ScheduleState=DISABLED`; it is `ENABLED` since the cutover)
```bash
sam deploy
```
Always review the change set before executing (`sam deploy --no-execute-changeset`, then execute it). `samconfig.toml` pins
`template.yaml`, because a stale `.aws-sam/build` would otherwise be deployed instead.
The first change set must only create new resources (two functions, roles, API, log groups, a disabled rule).
Nothing touches `PriceAlerts`.

**2. Test the new stack without switching anything**
```bash
URL=$(aws cloudformation describe-stacks --stack-name psychic-guacamole \
  --query "Stacks[0].Outputs[?OutputKey=='WebhookUrl'].OutputValue" --output text)
SECRET=$(aws ssm get-parameter --name /telegram-bot/webhook-secret --with-decryption --query Parameter.Value --output text)

# the reply comes from the real bot to YOUR chat; Telegram's registered webhook is unchanged
curl -s -X POST "$URL" -H "X-Telegram-Bot-Api-Secret-Token: $SECRET" -H 'Content-Type: application/json' \
  -d '{"update_id":1,"message":{"chat":{"id":<YOUR_CHAT_ID>},"text":"/price qqq"}}'
sam logs --stack-name psychic-guacamole --name TelegramWebhookHandlerNative --tail
```
Then invoke the checker once. This **processes real alerts and can send real notifications** (price alerts outside
their range re-fire on every run), and the old checker is still running too, so expect one duplicate for this run:
```bash
aws lambda invoke --function-name "$(aws cloudformation describe-stacks --stack-name psychic-guacamole \
  --query "Stacks[0].Outputs[?OutputKey=='PriceCheckerFunctionName'].OutputValue" --output text)" \
  --payload file://event_scheduled.json --cli-binary-format raw-in-base64-out /dev/stdout
```
Expect `Price check completed: N alerts checked, N symbols fetched, ...`. This is the first time the code talks
to Bybit **from AWS**. Stop here if you see a 403 / "block access from your country" (geo-block), `No price found`
for symbols that should exist, or a timeout.

**3. Cut over** (in this order)
```bash
aws events disable-rule --name telegram-price-bot-checker-schedule     # old checker off
# samconfig.toml: parameter_overrides = "ScheduleState=ENABLED"
sam deploy                                                             # new checker on

SECRET=$(aws ssm get-parameter --name /telegram-bot/webhook-secret --with-decryption --query Parameter.Value --output text)
printf 'url = "https://api.telegram.org/bot%s/setWebhook"\ndata = "url=%s"\ndata = "secret_token=%s"\n' "$TOKEN" "$URL" "$SECRET" | curl -s -K -
printf 'url = "https://api.telegram.org/bot%s/getWebhookInfo"\n' "$TOKEN" | curl -s -K - | jq .result   # url = new, no last_error_message
```
Smoke test in Telegram: `/price qqq`, `/alert qqq 600 700`, `/rsi btc 4h`, `/rsi`, `/list`, `/rsi rm <id>`, `/remove <id>`.
Commit the `samconfig.toml` change so the schedule state lives in git.

**Rollback** (any time before decommissioning)
```bash
# Telegram back to the URL you recorded in step 0 (same setWebhook call, old url, same secret_token).
# The production webhook is the bare API root, with no path: https://<api-id>.execute-api.eu-central-1.amazonaws.com
aws events enable-rule --name telegram-price-bot-checker-schedule
# samconfig.toml: ScheduleState=DISABLED, then sam deploy
```

**4. Decommission the old resources** (done)
Deleted: the functions `telegram-price-bot-frontend-native`, `telegram-price-bot-frontend`, `telegram-price-bot-native` and
`telegram-price-bot`, the API `telegram-price-bot-api`, the rule `telegram-price-bot-checker-schedule`, the IAM role
`telegram-price-bot-lambda-role` with its `*FullAccess` policies, and the SSM parameter `/telegram-bot/twelvedata-apikey`;
the `lambda-deploy` step is gone from `pom.xml`. Kept: `/telegram-bot/token`, `/telegram-bot/webhook-secret` and the
`PriceAlerts` table. Their old CloudWatch log groups (`/aws/lambda/telegram-price-bot*`) were left in place.

## Local development (unchanged)
```bash
docker run --rm --name dynamodb --network dynamodb_net -p 8000:8000 amazon/dynamodb-local:latest \
  -jar DynamoDBLocal.jar -sharedDb -inMemory
sam local start-api --template template.yaml --port 3000 --env-vars env.json --docker-network dynamodb_net
```
* Pass `--template template.yaml`. A stale `.aws-sam/build/template.yaml` (left by `sam build`) otherwise
  takes precedence over `template.yaml`; deleting `.aws-sam/` also fixes that.
* `env.json` keys are the logical IDs: `TelegramWebhookHandlerNative`, `PriceCheckerNative`. Only variables declared
  in the template can be overridden: `TELEGRAM_BOT_TOKEN`, `WEBHOOK_SECRET_TOKEN`, `ALERTS_TABLE`, `DYNAMODB_ENDPOINT`.
* ngrok: `ngrok http 3000`, then `setWebhook` with `https://<id>.ngrok-free.app/webhook` for a **dev bot**. A bot has exactly
  one webhook, so pointing the production bot at ngrok takes production offline.
* If `docker run --name dynamodb` fails because a stopped container already has that name, add
  `--network-alias dynamodb` to a differently named container (or `docker rm dynamodb`).
* A native binary cannot be debugged with JDWP; for a JVM debugger use `template_debug.yaml` (Java 21 JAR).
