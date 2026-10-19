# Software City

Software City exports the named top-level Java types in a local Git commit as versioned JSON and displays them as an interactive city. The Java 25 Quarkus CLI reads committed source through JGit and parses it with JavaParser. The TypeScript/Three.js viewer opens snapshots locally.

Companion for [Java Code as a City: A Tour of Quarkus and Spring](https://www.the-main-thread.com/p/java-code-city-quarkus-spring).

This version provides `analyze` and the snapshot viewer. It bundles derived JSON examples and small synthetic test inputs. Quarkus and Spring source code is not included. Check out their pinned revisions in a separate directory outside this repository to regenerate the examples.

## Build and test

Install a JDK 25 and set `JAVA_HOME` to it. Confirm that `java -version` reports Java 25. Maven is supplied by the wrapper; the first build downloads its distribution and dependencies.

From this directory, build and run unit tests, command-mode tests, and packaged JVM integration tests:

```sh
cd analyzer
./mvnw verify
cd ..
```

Keep the entire `analyzer/target/quarkus-app/` directory together when copying the application; `quarkus-run.jar` uses its adjacent dependencies. No native-image build, container, or server is required.

## Run the viewer

Install Node.js 22.12 or later and npm. The viewer was verified with Node.js 26.7.0 and npm 11.19.0. From this directory:

```sh
cd viewer
npm ci
npm run dev
```

Open the loopback URL printed by Vite, normally `http://127.0.0.1:5173/`. The initial city is the bundled Quarkus core/runtime snapshot: 219 types in 19 packages at commit `866d824ae3cca9750702e8eb4caea636d086ed48`.

Choose **Open JSON** or drop an analyzer snapshot JSON onto the page. Try `fixtures/synthetic.snapshot.json` from this project's directory for the reproducible nine-type example. **Load Quarkus sample** restores the real snapshot. Invalid files produce validation errors and preserve the current city and selection. Incomplete analysis displays the analyzer's diagnostics and omitted IDs.

Search by type, package, or module; multiple search terms must all match. Select a result or building to inspect exact counts and the repository-relative source location. **Copy path:line** copies that location. From search, press Down Arrow to enter the results, use arrow keys or Home/End to navigate, and press Enter to select. Search dims other buildings without moving them.

Drag the city to orbit, right-drag to pan, and scroll or pinch to zoom. A touch drag orbits; two fingers pan and zoom. The focused canvas also supports arrow-key panning. **Reset camera** fits the city; **Focus selected** frames the selected building. Height represents methods using `1 + 2 × log₂(1 + methods)`; footprint reserves weight `1 + min(63, fields)`, with gutters between buildings. The color selector changes shading by methods, fields, or source lines without changing positions or dimensions.

WebGL 2 is required for the city. If it cannot start, the type browser, search, metrics, and source locations remain available. Controls and results have keyboard focus states. On narrow screens, the city, type browser, and details stack vertically.

Vite serves static files on loopback; there is no application backend. Once dependencies are installed, the viewer and analyzer require no external services. The viewer does not upload selected files, retrieve source, or load external assets. Use the static server rather than opening `index.html` through `file://`.

### Verify and build the viewer

From `viewer/`, install the test browser once, then run the complete check:

```sh
npm exec -- playwright install chromium
npm run verify
```

The check validates generated type declarations, runs 16 unit tests, checks strict TypeScript, builds the production viewer, and runs 10 Chromium browser tests. Browser tests use software WebGL rendering and start a temporary production preview on `127.0.0.1:4173`. They exercise rendering, picking, local-file imports, drag-and-drop, keyboard controls, camera controls, responsive layout, invalid input, and WebGL/clipboard failures.

Run individual checks or serve the production build:

```sh
npm test
npm run build
npm run test:e2e
npm run preview
```

The production output is `viewer/dist/`. Serve that directory with a local static server. The first dependency and Chromium installation can require downloads. Maven, npm, and Playwright are the only build/test tools; Python is not required.

After changing the authoritative schema, regenerate and review declarations with `npm run generate:types`. `npm run check:types` fails if the generated file has drifted.

## Analyze a committed revision

From this directory, export an existing local Git commit:

```sh
java -jar analyzer/target/quarkus-app/quarkus-run.jar analyze \
  --repo /path/to/java-repository --ref HEAD --strict \
  --out /path/outside/java-repository/city.json
```

Replace the repository and output paths. The destination must be outside the analyzed worktree and Git directories, including destinations reached through symbolic links. Existing output is replaced only after successful analysis and validation.

The analyzer resolves refs once. It never checks out commits, fetches refs, follows source symlinks, or runs repository builds, scripts, tests, annotation processors, or Java code. Staged and uncommitted changes are ignored. Fetch missing refs yourself before analysis. If a short ref matches a branch and tag, use its full `refs/heads/...` or `refs/tags/...` name.

Inspect command help:

```sh
java -jar analyzer/target/quarkus-app/quarkus-run.jar analyze --help
```

### Choose source paths

The default include is `**/src/main/java/**/*.java`, covering files directly under source roots and nested packages. Repeat `--include` to replace that default; repeat `--exclude` to remove matches. Exclusions win.

Patterns match case-sensitive, repository-relative paths using `/`. `*` matches within one segment, `?` matches one non-separator character, and `**` occupies an entire segment and matches zero or more directories. Quote patterns to prevent shell expansion.

For example, include test sources explicitly while excluding a package:

```sh
java -jar analyzer/target/quarkus-app/quarkus-run.jar analyze \
  --repo /path/to/java-repository \
  --include '**/src/test/java/**/*.java' --exclude '**/internal/**' \
  --out /path/outside/java-repository/tests.json
```

Directories named `target`, `build`, `.gradle`, `.git`, `generated`, `generated-sources`, or `generated-test-sources` are always excluded. This name-based exclusion also applies inside source roots. Bracket/brace expansion, backslash separators, absolute patterns, and parent traversal are unsupported.

## Understand the export

The contract is defined in `schemas/software-city-v1.schema.json`. The schema supports snapshots and future comparisons. Every export is validated against the packaged schema and checked for consistent identities, revision data, spans, statuses, and counts.

Each candidate represents an explicitly named top-level class, interface, enum, record, or annotation. Multiple declarations in a file are separate types. Nested and anonymous types are not separate candidates. Compact Java sources, `module-info.java`, and `package-info.java` do not become types.

| Value | Meaning |
| --- | --- |
| ID | Module directory plus `::` plus qualified name. Root/unmarked modules use `.`. |
| Module | Nearest ancestor containing a committed `pom.xml`, `build.gradle`, or `build.gradle.kts`; contents are not evaluated. |
| Package | AST package declaration; empty string for the default package. |
| Methods | Direct methods including annotation members; excludes constructors, nested members, and generated accessors. |
| Constructors | Explicit direct constructors, including compact record constructors; no implicit constructors. |
| Fields | Direct field variables plus record components; excludes enum constants and nested members. |
| Source lines | Inclusive declaration span, including annotations/modifiers and nested content; excludes attached leading Javadoc. This is not logical LOC. |

Snapshots have a null base revision, null base source/metrics/type kind, unchanged statuses, and `fileChanged: false`. Missing values remain null. Ref labels and repository basename are display metadata; source paths are relative. With the same arguments and tool version, repeated exports have identical UTF-8 bytes. Timing is printed only with `--verbose`, outside JSON.

### Handle incomplete analysis

Without `--strict`, malformed/non-UTF-8 source, unsupported Git entries, and duplicate IDs produce structured diagnostics. A malformed file contributes no recovered types; all declarations sharing a duplicate ID are omitted. The export sets `summary.incomplete` to true and counts only verified, included types.

With `--strict`, these errors fail the command and preserve existing output. Informational diagnostics, such as skipped compact source, do not make analysis incomplete. `--verbose` prints informational diagnostics and elapsed analysis/export time.

| Exit | Meaning |
| --- | --- |
| `0` | Successful export; inspect `summary.incomplete` for permissive runs. |
| `1` | Repository, ref, strict-analysis, validation, or output failure. |
| `2` | Missing/invalid arguments or unsupported glob syntax. |

## Reproduce the synthetic example

The fixture contains nine types across root, `app`, and `api` modules, covering all five type kinds and identical qualified names in different modules.

Create the disposable Git fixture using the Java/JGit generator and verify the application:

```sh
cd analyzer
./mvnw -Pfixtures verify
cd ..
```

This writes `analyzer/target/fixture-repo/`, its fixed commit in `analyzer/target/fixture-commit.txt`, and `analyzer/target/fixture.snapshot.json`. Repeating generation recreates only that disposable repository. Fixed metadata produces the same commit and export.

Run the packaged application against it:

```sh
FIXTURE_COMMIT=$(cat analyzer/target/fixture-commit.txt)
java -jar analyzer/target/quarkus-app/quarkus-run.jar analyze \
  --repo analyzer/target/fixture-repo --ref "$FIXTURE_COMMIT" --strict \
  --out analyzer/target/fixture-cli.snapshot.json
cmp analyzer/target/fixture.snapshot.json analyzer/target/fixture-cli.snapshot.json
```

The bundled sample is `fixtures/synthetic.snapshot.json`.

## Reproduce the Quarkus example

`fixtures/quarkus-runtime.snapshot.json` is genuine output from [Quarkus](https://github.com/quarkusio/quarkus) commit `866d824ae3cca9750702e8eb4caea636d086ed48`, scoped to core/runtime. It contains 219 types and no diagnostics.

To obtain the exact source revision, run these commands from this directory. The checkout goes into `$HOME/software-city-sources`, outside the companion repository. You can choose another external directory by changing `SOFTWARE_CITY_SOURCES`. Git is required; no Quarkus build is needed.

```sh
SOFTWARE_CITY_SOURCES="$HOME/software-city-sources"
mkdir -p "$SOFTWARE_CITY_SOURCES"
git init "$SOFTWARE_CITY_SOURCES/quarkus"
git -C "$SOFTWARE_CITY_SOURCES/quarkus" fetch --depth 1 \
  https://github.com/quarkusio/quarkus.git \
  866d824ae3cca9750702e8eb4caea636d086ed48
git -C "$SOFTWARE_CITY_SOURCES/quarkus" checkout --detach \
  866d824ae3cca9750702e8eb4caea636d086ed48
```

Analyze that local repository:

```sh
java -jar analyzer/target/quarkus-app/quarkus-run.jar analyze \
  --repo "$SOFTWARE_CITY_SOURCES/quarkus" \
  --ref 866d824ae3cca9750702e8eb4caea636d086ed48 \
  --include 'core/runtime/src/main/java/**/*.java' --strict --verbose \
  --out analyzer/target/quarkus-runtime.snapshot.json
```

The export is 181,793 bytes. You can instead use an existing local Quarkus repository containing that commit by changing `--repo`. The analyzer never downloads missing objects or changes the checkout itself. Open the resulting JSON in the viewer to inspect your regenerated snapshot.

## Reproduce the Spring Core example

`fixtures/spring-core.snapshot.json` contains 679 top-level Java types across 50 packages from Spring Framework 7.0.9, commit `82a6b40b9366ec181ededdad282b307ee5381a52`. Choose **Open JSON** in the viewer and select that file. The Quarkus sample remains the default.

Check out the pinned [Spring Framework](https://github.com/spring-projects/spring-framework) tag in the same external source directory, then export the selected main Java directory:

```sh
SOFTWARE_CITY_SOURCES="$HOME/software-city-sources"
mkdir -p "$SOFTWARE_CITY_SOURCES"
git init "$SOFTWARE_CITY_SOURCES/spring-framework"
git -C "$SOFTWARE_CITY_SOURCES/spring-framework" fetch --depth 1 \
  https://github.com/spring-projects/spring-framework.git \
  refs/tags/v7.0.9:refs/tags/v7.0.9
git -C "$SOFTWARE_CITY_SOURCES/spring-framework" checkout --detach refs/tags/v7.0.9
```

```sh
java -jar analyzer/target/quarkus-app/quarkus-run.jar analyze \
  --repo "$SOFTWARE_CITY_SOURCES/spring-framework" \
  --ref refs/tags/v7.0.9 \
  --include 'spring-core/src/main/java/**/*.java' --strict \
  --out analyzer/target/spring-core.snapshot.json
```

This selection includes checked-in ASM/CGLIB sources and excludes Kotlin, tests, version-specific Java directories, and sources created by the build. It does not describe every class shipped in the Spring Core JAR. No Spring build is needed.

Both source checkouts stay outside the companion repository. You can browse a selected type using its repository-relative source path inside the matching checkout. The analyzer reads committed Git objects and leaves the checkout unchanged. The exported JSON goes under `analyzer/target/`; it contains metrics and source locations, not the upstream Java source text. The bundled snapshots let you use the viewer without downloading either source repository.

Spring's module script is named `spring-core.gradle`, which this analyzer does not recognize as a module marker. Its nearest recognized marker is the root `build.gradle`, so stable IDs use `.` as the module. The include pattern scopes the snapshot to Spring Core regardless of that label.

These are separate snapshots of modules with different responsibilities. The viewer fits the camera and normalizes color for each file independently; screenshot dimensions and shades are not a shared measurement scale. Use exact inspector counts to compare types. This does not measure framework startup, runtime memory, throughput, or dependencies.

## Current limitations

- Source must be UTF-8 and follow supported Java 25 syntax. Unsupported syntax is diagnosed rather than compiled or resolved.
- Maven profiles, Gradle source sets, generated code, dependencies, inheritance, and runtime relationships are not resolved.
- Nested types affect the enclosing source span but not its direct-member counts.
- Snapshots only; Git revision comparison and before/after controls are not implemented.
- The viewer's production JavaScript is approximately 833 kB minified (194 kB gzip), including Three.js, schema validation, and the bundled snapshot. Vite reports a chunk-size warning.
- Browser verification used Chromium with software WebGL rendering. Hardware GPU performance, other browsers, and the 10,000-type target have not been measured.
- The type list is not virtualized. Package labels are limited to eight readable districts; smaller districts retain outlines and their package names in the inspector.

The analyzer follows [Quarkus Picocli command mode](https://quarkus.io/guides/picocli) and uses [JavaParser](https://github.com/javaparser/javaparser) for AST-only parsing.
