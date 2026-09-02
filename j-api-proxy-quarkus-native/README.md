# J API Proxy in a Quarkus Native Application

This Quarkus 3.39.1 demo wraps the Agroal datasource with `j-api-proxy-jdbc` 0.1.0-alpha. It observes `DataSource` and `Connection` calls, registers the corresponding JDK proxy definitions for native image, and keeps statement and result-set interception disabled.

Run the JVM test (Podman is required for PostgreSQL Dev Services):

```bash
./mvnw test
```

Inspect the generated native proxy metadata:

```bash
./mvnw package -Dnative -Dquarkus.native.sources-only=true -DskipTests

unzip -p target/native-sources/j-api-proxy-quarkus-native-1.0.0-SNAPSHOT-runner.jar \
  META-INF/native-image/proxy-config.json | jq
```

Build the Linux native executable and run its integration test on a host with a working Podman setup:

```bash
./mvnw verify -Dnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.container-runtime=podman \
  -Dquarkus.test.integration-test-profile=test
```

The complete walkthrough is in [article.md](article.md).
