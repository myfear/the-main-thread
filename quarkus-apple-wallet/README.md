# quarkus-apple-wallet

Preview-only demo for Build and Preview Apple Wallet Passes with Quarkus. jPasskit builds The Main Thread membership card, and one Quarkus endpoint exports its JSON and artwork as an unsigned Pass Designer template. The application does not sign or distribute installable `.pkpass` files.

## Run and Preview

Use JDK 21, `curl`, and `unzip`. The visual preview requires macOS 27+ and [Pass Designer beta](https://developer.apple.com/pass-designer/). No Xcode, OpenSSL, paid developer membership, or signing certificate is needed for this demo.

From this module, start Quarkus:

```bash
./mvnw quarkus:dev
```

In another terminal, export and open the template:

```bash
./scripts/preview-pass-designer.sh
```

The script downloads `GET /wallet/membership/TMT-0042/template?name=Markus+Eisele&tier=Founding+Member` and opens a fresh `target/designer-preview.*/TheMainThread.pkpasstemplate` directory. Set `BASE_URL` if Quarkus uses another port, or `NO_OPEN=1` to export without launching the Mac app. Generation also works on Linux.

Expect the cream-and-charcoal artwork, serif wordmark, copper lines, native QR code, and member fields in the lower panel. The Images row should have no warning badge. Open the extracted `.pkpasstemplate`, not the ZIP or a signed `.pkpass`.

Each export is a snapshot. Change the query parameters or Java code and export again to see another card. Designer edits do not update the Java model. Save designs you want to keep outside `target/`, which Maven can delete during a clean. Stop Quarkus with Ctrl+C when finished.

## Build

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

The same preview script works with the packaged application. Placeholder identifiers are supplied in `application.properties` for both modes. There is no automated test suite; check the exported document in Pass Designer after changes.

## Artwork

The `posterGeneric` layout targets iOS/watchOS 27, with a `generic` fallback for earlier versions. Apple controls the live fields' typography, placement, and barcode. Names and QR codes are not baked into the images. See [Apple's Wallet design guidance](https://developer.apple.com/design/human-interface-guidelines/wallet).

The ten ready-to-use PNGs are in `src/main/resources/pass-template/`. Their source artwork is in `design/`. To rebuild their scale variants, install ImageMagick and run:

```bash
./scripts/render-pass-assets.sh
```

ImageMagick is not needed to run the application. `PassTemplateService` lists the ten images explicitly; keep that list in sync if you add or remove assets.

## Installable Passes

Creating a real Wallet pass additionally requires an Apple-issued Pass Type ID certificate, its private key, matching identifiers, and Apple's WWDR intermediate. A signer must hash the pass files, sign the manifest, and package the result as a `.pkpass`. 
