# FLUX MLX application

This Java 25 / Quarkus 3.40.1 application prepares scene captions with LangChain4j and native Ollama, then queues local FLUX 3 image generation through MFLUX 0.23.0. Python is confined to the inference runtime. Follow the [runtime setup and API examples](../README.md) before generating images.

From this directory, run `./mvnw verify` to test the REST contract and the packaged application without downloading models. Start the application with `java -jar target/quarkus-app/quarkus-run.jar` or use `./mvnw quarkus:dev` for development.

The application listens on `http://127.0.0.1:8080` and uses Ollama at `http://127.0.0.1:11434`. Export `MFLUX_BIN` as the absolute path to the installed `mflux-generate-flux3` executable and `HF_HOME` as the model-cache directory used during setup. The image process reads that cache in offline mode. Set `OLLAMA_URL` to override the caption server and `IMAGE_OUTPUT_DIR` to override the default `data/images` output directory.

`POST /api/captions` prepares a caption. `POST /api/images` returns a job ID, `GET /api/jobs/{id}` returns its status, and `GET /api/jobs/{id}/image` retrieves the PNG after success. The queue admits one active generation and two waiting jobs. Each generation runs in its own MFLUX process. The application is intended for local use.

See the [Quarkus REST guide](https://quarkus.io/guides/rest) and [Quarkus LangChain4j documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html) for the extension APIs.
