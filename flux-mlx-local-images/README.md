# Local FLUX images with MLX and Quarkus

Generate images on an Apple Silicon Mac with MFLUX 0.23.0 and a Java 25 application. LangChain4j prepares a scene caption through native Ollama. Quarkus launches one MFLUX process per image, with an explicit seed and 16 steps. Python is used only by the isolated inference runtime; application code and tests are Java.

The application provides caption preparation, a queue with one active generation and two waiting requests, job status, PNG retrieval, and JSON metadata saved beside each image. Quarkus and Ollama bind to loopback.

## Prerequisites

- An Apple Silicon Mac running macOS 26 or later, required by the locked MLX Metal wheel. The unquantized BF16 setup is intended for a Mac with 64 GB of unified memory; model loading and generation require more memory than the weight files alone.
- [uv](https://docs.astral.sh/uv/getting-started/installation/), Git, and `curl`. The setup script creates a Python 3.12 environment and downloads an interpreter if needed. No Swift toolchain is required.
- Java 25 and `JAVA_HOME` pointing to it. The companion includes a Maven wrapper.
- Native [Ollama for macOS](https://docs.ollama.com/macos), version 0.40.3. Use its macOS executable to access the Apple GPU.
- `jq` for the HTTP examples. With Homebrew, install both additional tools with `brew install uv jq`.
- At least 40 GB of free disk space for model weights, runtime packages, and caches.
- Hugging Face access to [FLUX 3 Image Klein](https://huggingface.co/black-forest-labs/flux-3-image-klein). Review its FLUX Kommunity License, request access, and create a read token. The separate [Qwen2.5 7B caption model](https://ollama.com/library/qwen2.5:7b) uses Apache 2.0.

## Install the inference runtime and download the model

Run these commands from `flux-mlx-local-images`:

```bash
./scripts/install-runtime.sh
export MFLUX_BIN="$PWD/.runtime/mflux/bin/mflux-generate-flux3"
export HF_HOME="$PWD/.runtime/huggingface"
```

The script installs MFLUX and its dependencies from the hashed lock file `inference/requirements.txt` into `.runtime/mflux`. It uses uv's cache under `.runtime/uv-cache`; any downloaded interpreter is stored under `.runtime/python`. No shell activation is needed. Keep `MFLUX_BIN` and `HF_HOME` exported in the terminal that starts Java.

After reviewing and accepting the FLUX terms, enter your token without putting it in shell history. This syntax is for macOS's default zsh:

```zsh
read -s 'HF_TOKEN?Hugging Face read token: '; printf '\n'
export HF_TOKEN
./scripts/download-model.sh
unset HF_TOKEN
```

The approximately 20 GB download includes the transformer, image decoder, and text encoder. The script uses the Hugging Face CLI from the inference environment and pins revision `8454017f9ebaada30c998fccd9f67a80c7580f68`, matching MFLUX's model catalog. Files are stored under `HF_HOME`. Wait for this command to complete before submitting an image request. It can be rerun to resume an interrupted download. The Java child process sets `HF_HUB_OFFLINE=1` and removes `HF_TOKEN` from its environment, so inference uses the local cache without a credential or download.

Start native Ollama in another terminal, unless it is already listening on port 11434:

```bash
OLLAMA_HOST=127.0.0.1:11434 OLLAMA_CONTEXT_LENGTH=4096 ollama serve
```

Download the caption model with `ollama pull qwen2.5:7b`. It adds approximately 4.7 GB of weights. There is no persistent FLUX server: each accepted job launches `mflux-generate-flux3`, loads the model, and waits for the process to exit.

## Build and start Java

In the terminal where `MFLUX_BIN` and `HF_HOME` are exported, enter the Maven module:

```bash
cd flux-mlx-app
./mvnw verify
java -jar target/quarkus-app/quarkus-run.jar
```

The tests use a local Java chat HTTP server and a Java renderer process; they require neither Python nor model downloads. They exercise both Quarkus test mode and the packaged JAR. To exercise the actual local models through the packaged application, stop any running copy of this app, keep Ollama available, and run:

```bash
FLUX_LIVE_TEST=true ./mvnw verify -Dit.test=LiveGenerationIT
```

Keep `MFLUX_BIN` and `HF_HOME` exported. This Java test requests a caption, submits 512 × 512 and 1024 × 1024 image jobs, retrieves both PNGs over HTTP, and decodes them with Java ImageIO.

The application listens on `http://127.0.0.1:8080`. `GET /q/health` checks the Java process; it does not load the image model. Override `OLLAMA_URL` to change the caption service URL, `MFLUX_BIN` to select the inference executable, or `IMAGE_OUTPUT_DIR` to change the image directory. `HF_HOME` must point to the cache used by the download script.

## Prepare a caption and generate an image

In a third terminal:

```bash
curl --fail-with-body -sS http://127.0.0.1:8080/api/captions \
  -H 'Content-Type: application/json' \
  -d '{"brief":"A small blue ceramic camera on a wooden desk, a red apple beside it, soft morning light, no text or logos."}' \
  > caption.json
jq -r .caption caption.json
```

Read the caption before submitting it. Edit `caption.json` if the model added details you do not want. You can also supply a caption directly and omit the language-model call.

```bash
jq '{prompt: .caption, width: 512, height: 512, seed: 42}' caption.json > request.json
curl --fail-with-body -sS http://127.0.0.1:8080/api/images \
  -H 'Content-Type: application/json' --data-binary @request.json > job.json
job_id=$(jq -r .id job.json)
curl --fail-with-body -sS "http://127.0.0.1:8080/api/jobs/$job_id" | jq
```

Repeat the status request until `state` is `SUCCEEDED`, then retrieve the PNG:

```bash
curl --fail-with-body -sS "http://127.0.0.1:8080/api/jobs/$job_id/image" -o image.png
open image.png
```

For 1024-pixel generation, change both dimensions in `request.json` to `1024`. Keep the prompt and seed fixed when comparing results. Accepted dimensions are 256–1024, in multiples of 64; the prompt limit is 4,000 characters and the seed must be nonnegative. Each command produces one PNG with `--steps 16`.

## Status and failure handling

- `POST /api/captions` returns the original brief and expanded caption. Empty or truncated model responses return HTTP 502.
- `POST /api/images` returns HTTP 202, a job ID, and a `Location` header. A full queue returns 429.
- `GET /api/jobs/{id}` returns `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, or `UNKNOWN`.
- `GET /api/jobs/{id}/image` returns the PNG, or 409 before a successful result exists.
- Invalid prompts and dimensions return 400. Unknown IDs return 404.

A nonzero process exit, invalid PNG, or five-minute generation timeout produces `FAILED`. On timeout, Java forcibly terminates the child and confirms its exit before starting another job. If termination cannot be confirmed within ten seconds, the result is `UNKNOWN`; the app stops admission with 503 and does not launch waiting jobs. Inspect the process and stop it before restarting Quarkus. Image generation is not automatically retried.

Completed attempts save JSON metadata in `flux-mlx-app/data/images`; successful attempts also save a PNG. Each child process has a `.log` file. Metadata includes the caption, model ID, dimensions, seed, steps, status, and elapsed generation time. Job lookup is in memory: it resets on restart and clears completed entries when its 64-entry limit is reached. Files remain on disk. The queue is a local demonstration, not a durable scheduler. Graceful shutdown interrupts the active task and terminates its child; killing the JVM forcibly can leave a child process behind.

If MFLUX reports missing files or an offline-cache error, check that `HF_HOME` is exported to the same location used during download, then rerun `./scripts/download-model.sh` from the tutorial root with an authorized token. A Hugging Face 401 or 403 requires a valid read token and granted repository access. If Java cannot start the process, check that `MFLUX_BIN` is an absolute path to `.runtime/mflux/bin/mflux-generate-flux3`. For memory pressure, start with 512 × 512, close other model servers, and run `ollama stop qwen2.5:7b` after preparing the caption. The generation queue does not serialize caption requests against Ollama.

## Stop and remove local data

Press Ctrl+C in the Quarkus terminal and stop the Ollama process if you started it for this example. Use `ollama rm qwen2.5:7b` to remove the caption weights when no longer needed.

Delete `.runtime/huggingface/` to remove the downloaded FLUX weights, or `.runtime/` to remove the inference environment and its caches as well. If you selected a different `HF_HOME`, inspect that location before deleting any shared models. Delete `flux-mlx-app/data/images/` to remove generated images and metadata. These local runtime and output directories are Git-ignored.
