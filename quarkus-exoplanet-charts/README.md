# Exoplanet charts with Quarkus

The companion app for [Build an Exoplanet Dashboard with Quarkus and Chart.js](article.md). Java builds three Chart.js configurations from the NASA Exoplanet Archive: stacked discovery counts, method shares, and logarithmic period/radius scatter points. The browser renders them and handles year selection and tooltips.

## Run

Requires JDK 25. The project was scaffolded with Quarkus CLI 3.39.1 and pins Quarkus 3.39.1, Web Bundler 2.3.4, `software.xdev:chartjs-java-model:3.0.2`, and `org.mvnpm:chart.js:4.5.1`. Maven dependencies need an initial download. No container, API key, or separate Node.js/npm installation is required.

From this module:

```bash
./mvnw quarkus:dev
```

Open [localhost:8080](http://localhost:8080). The default view uses the bundled snapshot and discovery years 1992–2025. Click a discovery bar to select that year, or use the form. Choose **NASA archive · live** and submit to query NASA. A successful live catalogue is cached for one hour. Errors are displayed; switching to the snapshot is explicit.

The snapshot is included, and Web Bundler packages Chart.js with the page code and CSS. Snapshot mode makes no external network requests at runtime.

## Browser bundle

[Quarkus Web Bundler](https://docs.quarkiverse.io/quarkus-web-bundler/dev/index.html) processes `src/main/resources/web`. The `{#bundle /}` tag in `index.html` becomes the generated script and stylesheet tags. `app.js` explicitly imports and registers the bar, doughnut, and scatter components, their scales, and the legend and tooltip plugins. Production assets are served under `/static/bundle/` with content hashes in their names.

Chart.js uses Maven `provided` scope: its browser code enters the bundle, and its dependency JAR is omitted from the runtime package. Web Bundler creates the ignored `node_modules/` directory from Maven dependencies. No separate npm command or `package.json` is needed.

## Build and test

Stop dev mode before running the packaged-app checks:

```bash
./mvnw verify -DskipITs=false
java -jar target/quarkus-app/quarkus-run.jar
```

The suite has ten unit/application checks and three packaged-JAR checks. Automated client tests use a local HTTP fixture or an intentionally unavailable local port; they do not call NASA. Test cases cover missing and invalid measurements, limits, an unknown method, zero-count years, empty series, response consistency, invalid input, REST-client mapping, cache reuse, and failure behavior. The browser-asset check reads the generated page and follows its script and stylesheet URLs, including production hashes. The deliberate failure case logs a warning.

The tutorial targets JVM execution. Native-image reflection and Jackson 3 model serialization have not been validated. Stop the process with Ctrl+C; no other services need cleanup.

## API

```bash
curl -fsS 'http://localhost:8080/api/dashboard?source=snapshot&from=1992&to=2025'
curl -fsS 'http://localhost:8080/api/dashboard?source=live&from=2016&to=2016'
curl -i 'http://localhost:8080/api/dashboard?from=2025&to=2000'
```

`GET /api/dashboard` returns source/retrieval metadata, counts, and the `discoveries`, `methods`, and `sizes` chart objects. Sources are `snapshot` and `live`; years must be ordered and between 1992 and the current year. Invalid requests return 400; unavailable catalogues return 503. The default range is intentionally fixed at 1992–2025, and the current year is partial.

## Data

The [query](src/main/resources/data/query.sql) selects seven columns from `ps where default_flag=1`. A cached immutable catalogue feeds fresh chart builders per response. Missing years are reported, missing methods join Other, and unavailable measurements remain in discovery counts. Only finite positive radius/period pairs with both limit flags zero enter the scatter. These are default solutions, not every measurement ever published for each planet. Error bars and population-inference models are outside this demonstration.

The snapshot was retrieved on **2026-09-07 at 13:00:47 UTC**:

| Scope | Planets | Scatter points | Excluded from scatter |
|---|---:|---:|---:|
| Entire snapshot, including 2026 | 6,360 | 4,712 | 1,648 |
| Default 1992–2025 range | 6,089 | 4,513 | 1,576 |
| 2016 | 1,504 | 1,427 | 77 |

Snapshot SHA-256: `f1e5e01ea3b3a703154b37e826bfb23f7f9d0af015b6f4a0eaac48f3cbf23500`.

To replace the snapshot with a fresh download (Python 3 and internet required):

```bash
python3 scripts/refresh-snapshot.py
```

This changes the snapshot file. It reads the same query as the live client and validates the response before replacement. Restart the app to clear its in-memory cache, or rebuild the JAR to include the changed resource. Preserve the printed retrieval time and hash when recording results. The counts above describe the included snapshot and can change after a refresh.

Data credit: [NASA Exoplanet Archive](https://exoplanetarchive.ipac.caltech.edu/), operated by Caltech for NASA's Exoplanet Exploration Program. See [archive acknowledgment guidance](https://exoplanetarchive.ipac.caltech.edu/docs/acknowledge.html) and [dataset research](dataset-research.md). Chart.js is distributed under the [bundled MIT license](src/main/resources/web/public/vendor/Chart.js-LICENSE.md); `chartjs-java-model` is Apache-2.0 licensed.

## Verification record

On 7 September 2026, `mvn -B -ntp clean verify -DskipITs=false` passed all 13 checks after the Web Bundler integration, with none skipped. The generated JavaScript and CSS were served successfully in both application tests and packaged-JAR tests. The packaged page rendered all three chart types, and selecting 2016 updated the counts to 1,504 selected, 1,427 plotted, and 77 excluded. Dev mode also loaded the dashboard with its generated module script and stylesheet. Neither browser run recorded errors or warnings.

All 18 complete file listings in the revised article match the companion source; its shell listings pass syntax checks. Before the bundler change, the expanded walkthrough was also reconstructed and verified in a fresh CLI-generated project. Earlier browser checks covered clicking the 2016 bar, an empty 1993 selection, a non-empty range with no scatter measurements, accessible tables, the live-source selector, and a 390-pixel layout without horizontal overflow. A real NASA request and the article's snapshot inspection and HTTP assertions also succeeded.

Quarkus Agent MCP supplied documentation and extension patterns. Its launcher declined the untracked generated Maven wrapper, so the current build and dev-mode validation used installed Maven. No native build was executed.
