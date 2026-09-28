# Keyboard workflow with Qute and HTMX

A Java 25 / Quarkus 3.39.4 lab for completing a two-step equipment-request form using only the keyboard. Submission creates an illustrative reference; it does not persist a request or reserve equipment.

## Run

Select JDK 25 in `JAVA_HOME`, then run from this directory:

```bash
./mvnw --version
./mvnw quarkus:dev
```

Open <http://localhost:8080/>. Tab to the skip link and activate it. Enter `Short` and quantity `9`, submit, follow the error links, correct the fields, review, edit, then submit. Stop dev mode with Ctrl+C.

HTMX 2.0.10 and its license are vendored under `src/main/resources/META-INF/resources/vendor/`. No frontend package manager, database, or container is required.

## Test

```bash
./mvnw org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"
./mvnw test
```

Playwright for Java 1.63.0 and axe integration 4.13.0 are test-only dependencies. The six tests cover keyboard completion and editing, axe scans at five workflow states, delayed-response focus, HTTP failure and retry, network failure, tampered confirmation fields, and JavaScript-disabled completion.

The test writes traces to `target/browser-traces/`:

```bash
./mvnw org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="show-trace target/browser-traces/completeWorkflowUsingOnlyKeyboard.zip"
```

Optional engine checks (not passing on the authoring host; see [validation limitations](VALIDATION.md)):

```bash
./mvnw org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install firefox webkit"
./mvnw test -Dbrowser=firefox
./mvnw test -Dbrowser=webkit
```

On fresh supported Linux CI workers, use `install --with-deps chromium` with OS package-install privileges. Match browser binaries to the Java dependency version.

## HTTP flow

- `GET /`: complete page with details form.
- `POST /review`: validates purpose and quantity; returns review or HTTP 422 with field errors.
- `POST /confirm`: `intent=edit` restores details; otherwise revalidates and returns a demo confirmation.
- `HX-Request: true`: selects the workflow fragment. Ordinary requests receive a complete document.

Input is Qute-escaped. HTMX swaps only the workflow's children. The status live region remains outside it. The script scopes its 422 handling to this target and the response marker, preserves focus outside the form, and retains form values on transport failures.


