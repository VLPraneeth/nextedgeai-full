Monorepo for all backend projects

# Running locally

Run the app you need:

```
mvn clean install -DskipTests -pl arcade spring-boot:run
mvn clean install -DskipTests -pl viper spring-boot:run -Dspring-boot.run.arguments=--server.port=8083
```