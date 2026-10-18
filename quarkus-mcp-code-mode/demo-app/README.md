# Request-event demo

A local Quarkus REST application for the [Code Mode tutorial](https://www.the-main-thread.com/p/quarkus-mcp-code-mode-quickjs).

Use JDK 25. From this directory, run `./mvnw verify` to build and test the application, or `./mvnw quarkus:dev` to run it independently. `GET /items` returns `["pencil","notebook"]` and emits the bundled request events once per application lifetime. It also logs a marked payment error without throwing an exception.

The events deliberately include duplicate records and retries arriving out of order. They are synthetic inputs for the parent companion's JavaScript report. The log format is message-only so the scripts can identify lines beginning with `R `.

For the complete gateway walkthrough, follow the [companion README](../README.md). Its launcher manages a separate local copy and owns the resulting application process. Stop an independently launched dev-mode session with Ctrl-C.
