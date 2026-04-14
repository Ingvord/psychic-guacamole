
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

Build native image, package `function.zip`, and deploy to AWS Lambda in one command:
```bash
mvn -Pnative deploy -DskipTests
```

To deploy to a different function:
```bash
mvn -Pnative deploy -DskipTests -Dlambda.function.name=<function-name>
```

## Java fat JAR (webhook handler Lambda)

No GraalVM required — any JDK 21 works.

```bash
mvn package -DskipTests
```

Produces `target/telegram-price-bot-1.0.0-shaded.jar`.
