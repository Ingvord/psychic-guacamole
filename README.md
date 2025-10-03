# psychic-guacamole

## Run local AWS Lambda with debug port open

```
sam local start-api \
  --port 3000 \
  --docker-network dynamodb_net \
  --debug-port 5858 \
  --debugger-path localhost \
  --debug-args "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5858"
```