# Barcode Lab

A small companion application for Beyond QR Codes: Choosing the Right Barcode Format with Quarkus. Eight editable examples show how payload rules, reader support and rendering settings affect the choice of barcode format.

## Run it

Use JDK 21 or newer. The project targets Java 21 and pins Quarkus 3.36.0, Quarkiverse ZXing 1.2.0 and its transitive ZXing 3.5.4. Maven is provided by the wrapper. No database, containers or external API accounts are required.

From this directory:

```bash
./mvnw quarkus:dev
```

Open [the gallery](http://localhost:8080/gallery). To use another port:

```bash
./mvnw quarkus:dev -Dquarkus.http.port=8095
```

Each card lets you edit the payload and dimensions, generate another PNG and download it. QR also exposes L, M, Q and H error correction. A failed request hides the previous image and download link so an old symbol cannot be mistaken for rejected input. Images retain their intrinsic pixel dimensions; scroll within a card to see a wide symbol.

Stop dev mode with Ctrl+C. The application stores no payloads or generated files.

## HTTP API

- `GET /` and `GET /gallery`: Qute gallery.
- `GET /barcodes`: JSON catalog with IDs, examples, constraints and default dimensions.
- `GET /barcodes/{type}?value=...`: PNG for a short payload.
- `POST /barcodes/{type}` with a UTF-8 `text/plain` body: PNG without putting the payload into a query string. The gallery uses this route for edits.

Both generation routes accept optional `width`, `height` and QR-only `ecc` query parameters. Dimensions must be integers from 128 through 1600; ECC defaults to M. ZXing treats requested dimensions as rendering targets and can return different actual dimensions, particularly for PDF417. The response supplies `X-Barcode-Width` and `X-Barcode-Height`. The lab rejects actual output larger than 1600 pixels in either direction.

```bash
curl --get 'http://localhost:8080/barcodes/qr' \
  --data-urlencode 'value=https://www.the-main-thread.com/' \
  --data-urlencode 'ecc=H' \
  --output /tmp/main-thread-qr.png

curl 'http://localhost:8080/barcodes/aztec' \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data-binary 'TRAIN:ICE1001:MUC-BER:2026-10-03' \
  --output /tmp/transport-aztec.png

curl --get 'http://localhost:8080/barcodes/ean13' \
  --data-urlencode 'value=4006381333932'
```

The last request returns HTTP 400 with `application/problem+json`:

```json
{
  "type": "urn:barcode-lab:invalid-input",
  "title": "Invalid barcode request",
  "status": 400,
  "detail": "EAN-13 check digit must be 1 for these first 12 digits.",
  "field": "value"
}
```

Unknown format IDs, missing/blank values, invalid dimensions, unsupported ECC settings and encoder capacity failures also return this problem shape. Failures at the HTTP layer have their own responses: excessively long GET URLs can return 414, and POST bodies beyond the configured 16 KiB limit return 413 before barcode validation. GET URLs can appear in logs/history; use POST for larger values and do not use secrets as lab examples.

## Input policies

**QR, PDF417 and Aztec** accept non-blank UTF-8 text with lab limits of 2048 Java string units and 2048 UTF-8 bytes. Those are request limits; the encoder may reach capacity earlier depending on the format, payload and correction settings.

**Code 128** uses printable ASCII (space through `~`) and a 64-character lab limit. This deliberately excludes control characters and ZXing's FNC escape characters. It generates plain Code 128, without GS1-128 application identifiers or GS1 semantics.

**Code 39** uses the base alphabet: A-Z, digits, space and `- . $ / + %`, with a 40-character lab limit. ZXing can encode extended ASCII, but this application rejects that mode. It never silently uppercases or trims an identifier.

**Codabar** requires explicit A-D guards and at least one interior character. Interior characters are digits or `- $ : / . +`. The 40-character lab limit includes guards. Other aliases and automatically inserted guards offered by the encoder are outside this application's contract.

**EAN-13** requires exactly 13 ASCII digits and independently validates the check digit. The application deliberately rejects the 12-digit input that ZXing could complete automatically. A correct check digit establishes arithmetic consistency; it does not establish a GS1 allocation or a product identity. The example is only a test fixture.

**ITF** accepts an even number of ASCII digits, at most 80 as supported by this ZXing writer. It does not enforce a GTIN check digit or claim ITF-14 compliance. The 14-digit example is generic ITF.

All linear formats also need sufficient requested width to render at least two pixels per narrow module with the chosen horizontal margins. Increasing the payload can require increasing the width, even before its character limit is reached. This is a lab rendering policy rather than a physical printing specification.

## Verification

```bash
# Full application tests, then the same contract against the packaged JVM app
./mvnw verify -DskipITs=false
```

The POM runs the Quarkus Eclipse formatter and import sorting during `process-sources`. It includes the formatter configuration in `config/eclipse-format.xml` so it does not depend on another local checkout. Test ports are assigned automatically.

There are 42 test executions in each mode: 84 total. Checks cover all eight formats through HTTP → PNG → decode, UTF-8 in the three 2D formats, all four QR correction levels, unchanged identifier text, EAN-13 checksum cases, invalid alphabets, odd ITF lengths, rendering limits, encoder capacity, both payload transports, and gallery/catalog availability. Codabar decoding explicitly retains start/stop characters, and the expected barcode format is supplied to the decoder to avoid EAN/UPC reinterpretation.

To run the built application:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Scaffold used

The application was generated with Quarkus CLI 3.39.1, then the non-platform extension was added separately with CLI extension management:

```bash
quarkus create app com.themainthread:barcode-lab:1.0.0-SNAPSHOT \
  --java=21 \
  --platform-bom=io.quarkus.platform:quarkus-bom:3.36.0 \
  --extensions=rest-jackson,rest-qute \
  --no-code --no-dockerfiles --wrapper --batch-mode
cd barcode-lab
quarkus extension add io.quarkiverse.barcode:quarkus-zxing:1.2.0 --batch-mode
```

REST Assured was then added as a platform-managed test dependency. `rest-qute` supplies Qute, `rest-jackson` supplies the catalog and structured error serialization, and `quarkus-zxing` supplies ZXing and its AWT integration. `.mcp.json` configures the Quarkus Agent MCP for continued local work.

## Files to read

- [BarcodeType](src/main/java/com/themainthread/barcode/BarcodeType.java): public format IDs, metadata, examples and defaults.
- [BarcodeValidator](src/main/java/com/themainthread/barcode/BarcodeValidator.java): application input contracts and EAN-13 checksum.
- [BarcodeService](src/main/java/com/themainthread/barcode/BarcodeService.java): rendering hints, dimension limits and PNG output.
- [BarcodeResource](src/main/java/com/themainthread/barcode/BarcodeResource.java): catalog, GET/POST image endpoints and problem responses.
- [GalleryResource](src/main/java/com/themainthread/barcode/GalleryResource.java) and its [checked template](src/main/resources/templates/GalleryResource/gallery.html): gallery.
- [BarcodeResourceTest](src/test/java/com/themainthread/barcode/BarcodeResourceTest.java): behavioral checks and PNG decoding.

The payloads that look like tickets are illustrative strings. The app does not implement railway, event, retail allocation or healthcare application protocols. Real labels need scanner, quiet-zone, material, resolution and physical-size checks for the intended workflow.
