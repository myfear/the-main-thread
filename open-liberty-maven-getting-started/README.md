# Open Liberty with Maven

This module contains the executable sample for the corresponding Main-Thread article. It uses Java 21, Jakarta REST 4.0, Open Liberty 26.0.0.6, and Liberty Maven Plugin 3.12.0.

Run the complete lifecycle:

```bash
./mvnw clean verify
```

The build creates `target/getting-started.war` and the runnable server package `target/getting-started.jar`. It also starts Open Liberty, runs `HelloResourceIT`, and stops the server.

Start dev mode:

```bash
./mvnw liberty:dev
```

Then call the endpoint:

```bash
curl http://localhost:9080/api/hello
```


