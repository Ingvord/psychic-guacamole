# psychic-guacamole

## Run local dynamodb

```bash
docker run --rm \
--network dynamodb_net \
-p 8000:8000 \
amazon/dynamodb-local:latest \
-jar DynamoDBLocal.jar -sharedDb -inMemory
```

## Run local AWS Lambda with debug port open

```bash
sam local start-api \
  --port 3000 \
  --docker-network dynamodb_net \
  --debug-port 5858 \
  --debug-args "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5858"
```