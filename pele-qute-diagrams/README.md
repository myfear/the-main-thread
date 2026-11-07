# Fulfillment Diagram Lab

Render a Mermaid flowchart in a Quarkus Qute page, adapt its layout to the available width, and download standalone SVG and PNG snapshots.

Companion to [Responsive Mermaid Diagrams in Quarkus with Pele and Qute](https://www.the-main-thread.com/p/responsive-mermaid-quarkus-pele-qute). The article URL is reserved and may not be live yet.

## Requirements

- Java 25. The companion was validated with Temurin 25.0.4.1.
- Internet access for the first Maven build. The included wrapper selects Maven 3.9.16.
- A current browser with JavaScript enabled.

The build pins Quarkus 3.40.1.1, Quarkus Web Bundler 3.0.0, and Pele 1.13.0. Web Bundler builds the frontend from Maven dependencies. You do not need a separate npm install or Node.js development server. No database or container is required.

The Pele artifact is available from Maven Central through mvnpm coordinates. It uses `provided` scope: its JavaScript is bundled into the application, and its dependency JAR is excluded from the runtime libraries.

## Run the application

From this project's directory:

```bash
./mvnw quarkus:dev
```

Open [the local application](http://localhost:8080). If that port is occupied:

```bash
./mvnw quarkus:dev -Dquarkus.http.port=8097
```

Stop dev mode with **Ctrl+C**.

## Explore the diagram

1. Select **Wide · 960**. The container width shown below the diagram is the actual available width; it can be smaller than the requested value.
2. Select **Narrow · 360**. The flow changes direction while retaining the same nodes and connections.
3. On a wide desktop viewport, select **Wide · 960**, then **Show order details**. The sibling panel reduces the diagram's space without resizing the browser. On a phone, details stack below it.
4. Choose **Scale the original layout** to compare image scaling with adaptation. Return to **Adapt to available space** afterward.
5. Choose **Paper** or **Graphite**, select a PNG size, then click **Create snapshot**. Both image previews appear; the download buttons save `fulfillment.svg` and `fulfillment.png`.
6. Resize the live diagram again. Existing snapshots retain their captured width and theme. Changing the source clears them.
7. Expand **Edit the Mermaid source** to change the flowchart. Invalid or empty source disables export. **Restore sample** recovers the original workflow.

The inline diagram is SVG too. The SVG image preview is a separate, self-contained image document. The PNG preview is a rasterization of that document at 1× or 2× its natural dimensions; those dimensions can exceed the container width.

## Build and verify

Run the HTTP and HTML-escaping tests, then build the application:

```bash
./mvnw verify
```

Install the Chromium version required by the pinned Playwright Java dependency to run the browser tests:

```bash
./mvnw exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test \
  -Dexec.args="install chromium"
```

Stop dev mode, then run the packaged-application checks:

```bash
./mvnw verify -DskipITs=false
```

The suite runs two Quarkus tests and six Chromium integration tests. It checks container breakpoints, graph preservation, sidebar resizing, scale-only geometry, SVG/PNG downloads, independent SVG display, light/dark backgrounds, 1×/2× dimensions, invalid-input recovery, and mobile keyboard controls. It writes screenshots and downloaded images to `target/browser-evidence/`.

On Linux, Chromium may require OS libraries. See [Playwright's browser dependencies](https://playwright.dev/java/docs/browsers#install-system-dependencies). Java tests use Playwright's bundled browser; a separately installed Chrome is not required.

Run the packaged application:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Open [localhost:8080](http://localhost:8080). Native-image and container execution are outside this companion's validated path.

## How it is arranged

- `DiagramCatalog` loads the UTF-8 Mermaid fixture once. `DiagramPage` renders a checked Qute template at `GET /`.
- `src/main/resources/diagrams/fulfillment.mmd` holds the sample and its accessibility description.
- `src/main/resources/templates/diagrams.html` supplies the page and escaped source text.
- `src/main/resources/web/diagram.js` mounts Pele and manages the controls and snapshot state.
- `src/main/resources/web/export.js` captures computed paint/font styles and converts the SVG to PNG.
- `src/main/resources/web/index.js` is the explicit Web Bundler entry point. `{#bundle /}` resolves the generated CSS and JavaScript URLs.

## Limits and troubleshooting

Only Pele's flowchart module is bundled. Other Mermaid diagram types need their own registration. Pele has its own layout engine, so output differs from Mermaid.js.

Adaptation can still produce a drawing wider than its container. Pele's automatic zoom controls help inspect it. Exports use the full drawing's view box and do not capture a temporary pan/zoom crop.

The editor accepts up to 10,000 characters. Rendering also limits edges to 200 and SVG output to 1,000,000 characters. Diagram links and external images are disabled. PNG exports are capped at 8,192 pixels per side and 16 million pixels overall; use 1× or simplify the diagram if the export exceeds those limits.

If dependency resolution fails, check access to Maven Central. The project pins Pele's version; adding an npm build is not required for the normal path.

If browser tests report a missing executable, rerun the Chromium installation command after updating Playwright. If a diagram fails to render, expand the source editor, read the error, and restore the sample to distinguish an input problem from a build problem.

Further reference: [Pele API](https://pele.run/api), [Web Bundler](https://docs.quarkiverse.io/quarkus-web-bundler/dev/), and [Qute](https://quarkus.io/guides/qute-reference).
