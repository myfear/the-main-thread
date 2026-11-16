# Pixel-art converter

Convert PNG and JPEG images to pixel art using downsampling, Floyd–Steinberg error diffusion, and nearest-neighbor upscaling. Version 2.0.0 updates the original Java 21 example for Java 25 and Quarkus 3.40.1.2.

Companion to [Virtual Threads in Quarkus: Building a Bounded Image Processing Service](https://www.the-main-thread.com/p/java-21-virtual-threads-quarkus-image-processing).

## Requirements

Use JDK 25 and set `JAVA_HOME` to that installation. The included Maven wrapper downloads Maven and dependencies on its first invocation. No database or broker is required. Podman is optional for the JVM container.

## Run and convert an image

From this directory:

```sh
./mvnw quarkus:dev
```

In another terminal, replace `photo.jpg` with a local PNG or JPEG:

```sh
curl --fail-with-body -sS \
  -F 'image=@photo.jpg' -F 'blockSize=8' \
  http://localhost:8080/api/filter/pixelate -o pixelated.png
```

The response is a PNG. `X-Execution-Virtual: true` identifies request execution; `X-Image-Width` and `X-Image-Height` give output dimensions. A 53 × 37 image with block size 8 produces 48 × 32 pixels: incomplete blocks at the right and bottom edges are discarded. PNG transparency is discarded; filtering uses RGB. JPEG EXIF orientation is not applied. This service accepts PNG and JPEG only.

The chain samples the top-left pixel of each block, diffuses quantization errors with a step of 32 per color channel, then repeats pixels using nearest-neighbor interpolation. The output channel values are 0, 32, 64, 96, 128, 160, 192, 224, and 255.

## Limits and errors

- `image` is required and must be nonempty.
- `blockSize` defaults to 8; the default accepted range is 1–128. It must fit both source dimensions.
- The entire multipart request is limited to 15 MiB, including multipart overhead.
- Image headers are checked before raster decoding. The default limit is 4,000,000 source pixels.
- At most two conversions per application instance can hold decoded images at once. Excess requests receive HTTP 503 and `Retry-After: 1` immediately.
- Invalid settings, unsupported formats, and undecodable images return 400. Oversized requests or decoded dimensions return 413. Framework-level HTTP errors can have a different body from application JSON errors.

Configure `pixel.max-concurrent`, `pixel.max-pixels`, and `pixel.max-block-size` in `application.properties` or through their environment equivalents. Values must be positive. Increasing limits increases possible memory and CPU consumption. Admission happens after multipart upload handling, before image decoding; the limit does not bound incoming connections, upload storage, or total process memory.

The annotated endpoint runs decoding, filtering, and encoding on its virtual thread. CPU computation occupies a carrier thread. The semaphore limits active conversions but does not isolate CPU execution or speed up the algorithm. Tune limits to your heap and CPU budget before exposing the service to sustained traffic.

## Build and test

Stop dev mode before packaged verification:

```sh
./mvnw verify -DskipITs=false
java -Djava.awt.headless=true -jar target/quarkus-app/quarkus-run.jar
```

Keep the entire `target/quarkus-app` directory when copying the application. Tests generate images in Java and cover PNG/JPEG conversion, dispatch, output dimensions, palette behavior, invalid images, upload and pixel limits, overload rejection, and conversion-slot release. The integration suite exercises the packaged JVM application.

This is a JVM companion. Native AWT/ImageIO execution is outside its validated scope.

## Optional JVM container

After packaging:

```sh
podman build -f src/main/docker/Dockerfile.jvm -t pixel-art-converter .
podman run --rm -p 8080:8080 pixel-art-converter
```

## Original edition

The [original companion](https://github.com/myfear/ejq_substack_articles/tree/6dfff4c4412f468f415b5e5722f0a16cae246123/pixel-art-converter) contains the Java 21 and Quarkus 3.30.3 edition. This edition preserves its three-filter design and endpoint path.
