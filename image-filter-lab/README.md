# Image Filter Lab

Explore ten [Pembana Image Filters](https://github.com/pembana/pembana-image-filters) in a local Quarkus application. Compare an original image with its filtered result, inspect Gaussian kernel weights and ordered-dither matrices, and download the result as PNG.

Companion to [Filters from the Inside: Gaussian Blur and Dithering in Java](https://www.the-main-thread.com/p/java-image-filters-gaussian-blur-dithering).

## Requirements

- JDK 25, with `java` on your path and `JAVA_HOME` pointing to that JDK.
- Internet access for the first Maven dependency download.
- A browser with JavaScript enabled.

The Maven wrapper is included. No database, container engine, frontend build, or external service is needed. The project uses Quarkus 3.40.1.2 and Pembana Image Filters 1.0.0-M2.

## Run the lab

From this directory:

```sh
./mvnw quarkus:dev
```

Open [the local lab](http://localhost:8080). The generated 1280 × 800 test frame loads automatically. Choose a filter, adjust its controls, and move the comparison slider with the pointer or arrow keys. **Download PNG** becomes available after the current render succeeds.

**Choose image** accepts PNG, JPEG, or GIF files up to 10 MiB and 24 million pixels. GIF processing uses the first frame. JPEG EXIF orientation is applied before filtering. WebP, HEIC, SVG, and camera RAW files are not supported.

Stop dev mode with Ctrl+C. To use another port:

```sh
./mvnw quarkus:dev -Dquarkus.http.port=8082
```

Then open [port 8082](http://localhost:8082).

## Build and verify

Run the unit/application tests, package the application, and run the HTTP suite against the packaged JAR:

```sh
./mvnw verify -DskipITs=false
```

The verification run has 51 Surefire test cases and 32 Failsafe integration cases. All must finish with zero failures and errors. The suite covers all ten filters, malformed settings, upload limits, PNG decoding, EXIF handling, and palette and kernel properties.

For the unit/application tests alone, use `./mvnw test`. To package without the separate integration-test phase, use `./mvnw package`.

After stopping dev mode, run the packaged application:

```sh
java -Djava.awt.headless=true -jar target/quarkus-app/quarkus-run.jar
```

Keep the entire `target/quarkus-app` directory together when copying the application. This is a JVM companion; native AWT/ImageIO execution and the generated native container scaffolding are not validated.

## API

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/filters` | Filter names, supported settings, ranges, defaults, and help |
| GET | `/api/sample.png` | Generated 1280 × 800 test frame |
| GET | `/api/theory/gaussian?radius=6` | Radius, sigma, tap count, and rounded kernel weights |
| GET | `/api/theory/dither` | Named threshold matrices from the library |
| POST | `/api/render` | PNG output from multipart fields `image`, `filter`, and `settings` |

The `filter` field is required. Omit `image` to use the test frame; an empty upload is an error. Omit `settings` to use defaults, or supply a JSON object whose keys match the catalog. Unknown filters or settings, malformed values, and unsupported images return HTTP 400 with a JSON `error` message. HTTP body/part size limits can return HTTP 413 before the resource runs.

Finite numeric settings are clamped to the catalog's ranges. Integer controls round after clamping. Boolean settings must be JSON booleans.

Render the sample without supplying a local image:

```sh
curl --fail-with-body -sS \
     -F 'filter=gaussian' \
     -F 'settings={"radius":6}' \
     http://localhost:8080/api/render \
     -o blurred.png

curl --fail-with-body -sS \
     -F 'filter=dither' \
     -F 'settings={"matrix":"halftone6","levels":2,"colorDither":false}' \
     http://localhost:8080/api/render \
     -o dithered.png
```

Both outputs are 1280 × 800 PNGs. To upload an existing JPEG, add `-F 'image=@photo.jpg;type=image/jpeg'`.

Successful renders include `X-Image-Width`, `X-Image-Height`, `X-Output-Bytes`, and `X-Render-Millis`. The timer covers filtering and PNG encoding, excluding upload transfer, source decoding, orientation correction, and browser display.

## Filter behavior and limits

The catalog includes Gaussian Blur, Unsharp Mask, Dither, Quantize, Posterize, Sepia, Grayscale, Solarize, Contrast, and Invert.

- Gaussian radius uses `sigma = radius / 3`, with `2 * ceil(radius) + 1` taps for a positive radius. Radius zero copies the pixels and displays an identity kernel.
- Ordered dither applies thresholds per RGB channel or to their arithmetic mean. Two levels with color enabled allow eight RGB combinations; two levels with color disabled give black and white.
- M2's dither `mod` lookup scales the full byte value, not the fractional remainder between output levels. Patterns at more than two levels can shift the average tone.
- Quantize uses an octree palette with an 8–64 color budget. Its error-diffusion controls are intentionally unavailable: M2 omits the adjacent-row offset in the diffusion buffer index. This companion does not present that implementation as correct Floyd–Steinberg diffusion.
- Processing works on decoded 8-bit channels. It does not provide a color-managed printing pipeline, preserve source metadata, or guarantee photograph-editor behavior around transparent pixels.

The app binds to `127.0.0.1` by default. It is intended for local experiments and has no authentication, queue, or global concurrency limit. The request body is capped at 12 MiB; uploaded files are removed when the request ends. The pixel limit does not cap total heap use: filters allocate intermediate buffers, and concurrent renders multiply that cost. Canceling a browser request may leave its server calculation running.

For Linux JVM containers, `src/main/docker/Dockerfile.jvm` is supplied as a starting point with Podman commands. Container execution has not been validated; the host JVM is the documented execution path.

## Code map

- `FilterCatalog`, `FilterSpec`, and `FilterParam`: metadata used to build browser controls.
- `FilterResource`: multipart validation, decoding limits, PNG responses, and theory endpoints.
- `ImageFilterService`: bounded settings, per-request filter instances, and PNG encoding.
- `DitherPresets`: names and arrays for the threshold matrices.
- `ExifOrientation`: JPEG orientation parsing and the eight pixel mappings.
- `SampleImage`: generated test frame and cached PNG.
- `src/main/resources/META-INF/resources/`: browser UI.
- `FilterResourceIT`: the endpoint suite reused against the packaged artifact.

## Credits

Pembana Image Filters is maintained by Pembana, based on Jerry Huxtable's JHLabs filters, and distributed under the [MIT license](https://github.com/pembana/pembana-image-filters/blob/main/LICENSE). Its Java packages retain the `com.jhlabs` namespace. See the [Quarkus REST guide](https://quarkus.io/guides/rest) for the multipart APIs.
