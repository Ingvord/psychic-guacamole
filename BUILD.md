
# Prerequisites

- GraalVM JDK 21 (for native image builds)
- AWS CLI configured with appropriate credentials

If using sdkman:
```bash
sdk install java 21-graalce
```

# Build & Deploy

## Native image (price checker Lambda)

`JAVA_HOME` must point to a GraalVM JDK 21 distribution.

With sdkman:
```bash
sdk use java 21-graalce
```

Build the native image and package `function.zip` (binary + `bootstrap` + `libaws-crt-jni.so`):
```bash
mvn -Pnative package -DskipTests
```

Deploying is done with SAM, which uploads `function.zip` and applies a reviewed CloudFormation change set:
```bash
sam deploy
```
See `DEPLOY_SAM.md`. Maven no longer pushes code to Lambda itself.

## Java fat JAR (webhook handler Lambda)

No GraalVM required — any JDK 21 works.

```bash
mvn package -DskipTests
```

Produces `target/telegram-price-bot-1.0.0-shaded.jar`.

## Testing

Three levels, all driven by JUnit. `mvn test` is offline and fast; the other two are opt-in profiles.

### 1. Offline: synthesised market data, mocked Telegram
```bash
mvn test
```
Runs unit tests (RSI maths, Bybit parsing, symbol mapping) and `RsiScenarioTest`, which drives the real
webhook handler and the real scheduled checker on synthesised Bybit candles (`CandleFixtures`). Every
Telegram message the bot would send is captured and printed to stdout in a framed block.
`src/test/resources/fixtures/bybit/kline_btcusdt_240.json` is a real Bybit response that pins the parser to
Bybit's actual format.

### 2. Same scenarios, delivered to your Telegram chat
```bash
export TELEGRAM_BOT_TOKEN=...      # e.g. jq -r .PriceCheckerDebug.TELEGRAM_BOT_TOKEN env.json
export TEST_CHAT_ID=...            # your chat id
mvn test -Ptelegram
```
Each scenario posts a `🧪 Scenario N: ...` header, then the bot's real messages. A non-200 from Telegram
(e.g. a Markdown parse error) fails the test.

### 3. End to end: real Bybit + real Telegram + dynamodb-local
```bash
docker run --rm -d --name dynamodb-e2e -p 127.0.0.1:8000:8000 \
  amazon/dynamodb-local:latest -jar DynamoDBLocal.jar -sharedDb -inMemory

export TELEGRAM_BOT_TOKEN=... TEST_CHAT_ID=...
DYNAMODB_ENDPOINT=http://localhost:8000 mvn test -Pe2e

docker stop dynamodb-e2e
```
Refuses to run unless `DYNAMODB_ENDPOINT` points at localhost, so it can never touch the production table.
It creates and drops the table `price-alerts-e2e`. The second test needs some pair to have RSI(2) in a
zone right now and is skipped otherwise.
