# Build a Quarkus JSON endpoint with Bazel

Companion to [Bazel for Java Developers: Build a Quarkus App](https://www.the-main-thread.com/p/bazel-quarkus-build).

This example builds one Quarkus REST endpoint with Jackson. `GET /hello` returns:

```json
{"message":"Hello from Bazel"}
```

The application uses Java 25, Quarkus 3.40.1, Bazel 7.7.1, and Quarkus Bazel
rules 0.8.0. It needs no database or container engine.

The commands were validated on macOS with Apple Silicon.

## Install the prerequisites

You need Git, curl, a JDK 25, and
[Bazelisk](https://github.com/bazelbuild/bazelisk#installation). Set `JAVA_HOME`
to your JDK 25 installation. On macOS, with JDK 25 already installed, you can
install Bazelisk with Homebrew and select the JDK:

```bash
brew install bazelisk
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

Bazelisk provides the `bazel` command and reads `.bazelversion` to select and
download Bazel 7.7.1. The first build also downloads rules, Java build tools, and
Maven dependencies. Allow network access to their public repositories.

## Get the example

Clone the public companion repository and enter this example:

```bash
git clone https://github.com/myfear/the-main-thread.git
cd the-main-thread/bazel-rest-jackson
```

If you already have the repository, open its `bazel-rest-jackson` directory.
Run every `bazel` command from here, where `MODULE.bazel` and `BUILD.bazel`
are located. Check the tools:

```bash
java -version
"$JAVA_HOME/bin/java" -version
bazel --version
```

`"$JAVA_HOME/bin/java" -version` must report Java 25; `bazel --version` must
report Bazel 7.7.1. The checked-in build files and lockfiles are ready to use.
You do not need to recreate the files from the article or generate a new app.

`.bazelrc` selects Java 25 for application source and the local JDK for application
execution and Java build tools. Bazel distinguishes these runtimes: setting only
the application runtime leaves its build tools on a separate default. This setup
does not pin the exact local JDK distribution or patch version. The Java rules
also download a compiler toolchain.

## Understand the files

- [.bazelversion](.bazelversion) selects the Bazel version for Bazelisk.
- [.bazelrc](.bazelrc) holds common command options, including the Java configuration.
- [MODULE.bazel](MODULE.bazel) declares Bazel modules, Maven artifacts, the Quarkus BOM, and
  the Quarkus toolchain. Bzlmod is Bazel's module dependency system.
- [MODULE.bazel.lock](MODULE.bazel.lock) records resolved module information for Bazel.
- [maven_install.json](maven_install.json) records resolved Java dependencies and checksums. Keep
  both lockfiles with the source.
- [BUILD.bazel](BUILD.bazel) declares the targets to compile, package, run, and test.
- [GreetingResource.java](src/main/java/com/themainthread/bazel/GreetingResource.java)
  implements `/hello` and its JSON response record.
- [GreetingResourceTest.java](src/test/java/com/themainthread/bazel/GreetingResourceTest.java)
  checks the response in a running test application.
- [GreetingResourceIT.java](src/test/java/com/themainthread/bazel/GreetingResourceIT.java)
  runs that same check against the packaged application.

After a build, Bazel creates `bazel-bin`, `bazel-out`, `bazel-testlogs`, and a
`bazel-<directory-name>` symlink in this directory. These are generated
shortcuts into your machine's build cache, not source files to copy or commit.
Each reader's build creates its own links. See
[Bazel's output directory layout](https://bazel.build/remote/output-directories).

The published Quarkus rules are a Bazel module. The Java libraries they build
with still come from Maven repositories through `rules_jvm_external`. Using
Maven artifact coordinates does not invoke a Maven build. You do not need to
clone either the Quarkus rules repository or the Bazel Central Registry.

## Build and run

```bash
bazel build //:app
bazel run //:app
```

`//:app` identifies the target called `app` in the root Bazel package. A Bazel
package is a directory containing a build file; it is separate from a Java
package. `build` produces the application, and `run` builds it if needed and
starts it.

The packaged application is in `bazel-bin/app-quarkus-app/quarkus-app/`.
Keep that directory together: the Fast JAR layout includes supporting libraries
alongside `quarkus-run.jar`.

In another terminal, request the endpoint:

```bash
curl -i http://localhost:8080/hello
```

Expect HTTP 200, a JSON content type, and the greeting shown at the start of this
README. Press Ctrl+C in the application terminal to stop it.

## Test the endpoint

```bash
bazel test //:test //:integration_test
```

`test` runs `@QuarkusTest`. `integration_test` runs the same HTTP assertion against
the packaged application using `@QuarkusIntegrationTest`. The assertion checks
the status, JSON content type, and `message` field.

Bazel can reuse successful test results when their inputs have not changed. To
force both tests to execute again:

```bash
bazel test //:test //:integration_test --nocache_test_results
```

Building `//:app` does not run these tests. Request them explicitly.

## Try dev mode

Stop the packaged application first so port 8080 is available. Start dev mode:

```bash
bazel run //:app_dev
```

In [GreetingResource.java](src/main/java/com/themainthread/bazel/GreetingResource.java),
change the return statement to:

```java
return new Greeting("Hello from my Bazel build");
```

Save the source file. In another terminal, request the endpoint to trigger
reload:

```bash
curl http://localhost:8080/hello
```

Expect the changed response:

```json
{"message":"Hello from my Bazel build"}
```

Restore `Hello from Bazel` in the source and request the endpoint again before
running the tests, which assert that original value. Stop dev mode with Ctrl+C.

## Inspect the build

List the targets in the root package:

```bash
bazel query '//:*'
```

Inspect the application compilation action:

```bash
bazel aquery 'mnemonic("Javac", //:lib)' \
  --include_artifacts=false --include_commandline=false
```

A target is a named piece of the build. A rule defines how to build that kind of
target. Rules create actions, such as compiling Java or performing Quarkus
augmentation. `query` inspects targets; `aquery` inspects actions.

Run an unchanged build twice and compare the process summaries:

```bash
bazel build //:app
bazel build //:app
```

Change the greeting and build again. Bazel tracks action inputs and reuses valid
outputs. This demonstrates incremental behavior, not a speed comparison with
Maven or Gradle. Restore the greeting and rerun the tests when finished.

## Relate this to Maven and Gradle

Maven organizes plugin goals into lifecycle phases. `mvn package` reaches the
packaging phase through the preceding phases, including tests unless configured
otherwise. Gradle organizes work as tasks with dependencies and already supports
incremental execution and build caching.

Bazel asks you to name the target you want, then analyzes its rules and dependency
graph to determine the actions needed. Here `java_library` compiles the Java
source, while `quarkus_app` adds Quarkus augmentation and packaging. The extra
build declarations make these inputs and relationships explicit.

## Update Java dependencies

After changing Maven coordinates or the BOM in `MODULE.bazel`, regenerate the
Java lockfile:

```bash
REPIN=1 bazel run @maven//:pin
```

Review the lockfile changes and rerun the tests. Rules 0.8.0 require one of their
exact supported Quarkus versions; keep the BOM and `quarkus.toolchain` version
aligned. See the [rules release notes](https://github.com/quarkusio/quarkus-bazel-rules/releases/tag/v0.8.0).

## Troubleshoot setup

- If `bazel` is not found, install Bazelisk and make its `bazel` command
  available on your `PATH`.
- If Java 25 is not selected or compilation reports an unsupported release,
  check `"$JAVA_HOME/bin/java" -version` and point `JAVA_HOME` to a JDK 25.
  After changing it, run `bazel shutdown` and retry.
- If Bazel cannot find the project, check that your terminal is in
  `the-main-thread/bazel-rest-jackson`, alongside `MODULE.bazel`.
- If port 8080 is already in use, stop the other application before starting
  this example. Run the packaged application and dev mode one at a time.

## Stop the build server

After stopping the application with Ctrl+C, stop the background Bazel server:

```bash
bazel shutdown
```
