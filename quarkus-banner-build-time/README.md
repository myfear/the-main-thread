# Quarkus Banner build-time demo

This project supports the Main Thread article, “What a Startup Banner Teaches Us About Quarkus Build Time.” It uses Quarkus Banner 1.6.0 to render a two-color **THE MAIN THREAD** startup banner during augmentation.

## Run the application

```bash
./mvnw quarkus:dev
```

Then verify the endpoint:

```bash
curl -s http://localhost:8080/thread
```

## Run the tests

```bash
./mvnw test
```

## Build and run the packaged application

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

The banner's text, font, layout, and configured colors are build-time inputs. A runtime `QUARKUS_BANNER_GENERATOR_TEXT` override therefore does not change a packaged application. Runtime console detection still chooses between the pre-rendered colored and plain variants.

Run with plain output:

```bash
NO_COLOR=1 java -jar target/quarkus-app/quarkus-run.jar
```

