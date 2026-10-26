# Maven 3.10 and the road to Maven 4

Companion for [Maven 3.10 and the Road to Maven 4](https://www.the-main-thread.com/p/maven-3-10-road-to-maven-4).

Compare Maven **3.9.16**, **3.10.0**, and **4.0.0-rc-7** with the same plugin versions. The examples cover stricter POM validation, reproducible archives, classpath resource lookup, repository credential scope, and a Maven 4 library consumed by Maven 3. The Maven 4 RC is a preview marked unsuitable for production.

## Requirements

- **JDK 25 or newer**, selected through `JAVA_HOME` or on `PATH`.
- A POSIX shell on macOS or Linux for the launch scripts.
- Network access to Maven Central on the first run.
- Permission to bind a temporary HTTP server to `127.0.0.1` for the credential comparison.

The Java runner targets Java 25 and uses JDK APIs for downloads, ZIP extraction, process execution, and the HTTP repository. The small application examples target Java 17 to keep the artifact target separate from the JDK running the build. The complete project, including the runner, requires JDK 25.

## Run the comparisons

From this directory:

```shell
./scripts/lab.sh verify
```

The launcher runs [MavenLab.java](lab-runner/src/main/java/com/themainthread/mavenlab/MavenLab.java) in Java source-file mode. No prior Maven build is needed. The runner downloads the pinned Maven ZIPs and checks the SHA-512 values in [config/maven-distributions.properties](config/maven-distributions.properties).

It supplies empty global settings, dedicated user settings, and a local repository under `.lab`. The child Maven and Java processes use the same JDK as the runner. Common Maven/JVM option environment variables are cleared, and `user.home` points to `.lab/home`. Your normal Maven settings and credentials are excluded.

Some individual Maven commands intentionally fail. The runner checks their exit status and results. Success ends with:

```text
Requested checks passed. Results: .../.lab/runs/RUN_ID/summary.json
```

Run one scenario with `--only`:

```shell
./scripts/lab.sh verify --only validation
./scripts/lab.sh verify --only classpath
./scripts/lab.sh verify --only reproducibility
./scripts/lab.sh verify --only consumer_pom
./scripts/lab.sh verify --only credentials
```

Each invocation creates a new run. A single-scenario run is marked `partial` and does not load, relabel, or update earlier results. A failed run is retained with its failure status. The summary includes UTC timestamps, selected scenarios, JDK and operating-system details, Maven distribution checksums, and a fingerprint of the runner source.

Commands through the launch scripts acquire a lock because the comparisons share build directories and a Maven repository. Wait for a comparison to finish before starting another build in this directory.

## Select a Maven version

Pass Maven arguments after the version:

```shell
./scripts/maven.sh 3.9.16 --version
./scripts/maven.sh 3.10.0 clean verify
./scripts/maven.sh 4.0.0-rc-7 clean verify
```

This helper uses the same Java bootstrap and isolated settings as the comparison runner. It downloads only the selected distribution when necessary. To download all three without building:

```shell
./scripts/lab.sh bootstrap
```

The standard wrapper is pinned to Maven **3.10.0**:

```shell
./mvnw clean verify
```

That command builds the root reactor and runs the verification-record tests. The five cross-version comparisons are run separately by `lab.sh verify`. The wrapper follows normal Maven settings and cache behavior; its first distribution download requires `unzip` and either `sha256sum` or `shasum`. The Java launch scripts perform their own download and extraction with JDK APIs.

## Explore the examples

### POM validation

`examples/duplicate-plugins` repeats Clean Plugin 3.5.0. `examples/duplicate-dependencies` repeats a dependency. Both warn and succeed under 3.9.16, then fail under 3.10.0 and the Maven 4 RC.

```shell
./scripts/maven.sh 3.9.16 -f examples/duplicate-plugins/pom.xml validate
./scripts/maven.sh 3.10.0 -f examples/duplicate-plugins/pom.xml validate
./scripts/maven.sh 3.10.0 -f examples/fixed-plugins/pom.xml validate
```

The second command intentionally exits `1`. The final command checks the repair.

### Classpath ordering

`app` depends on `api` and `adapter`; `api` depends on `support`. Both `support` and `adapter` contain `collision.properties`.

```shell
./scripts/maven.sh 3.10.0 clean install
./scripts/maven.sh 3.10.0 -f classpath/app/pom.xml \
    org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath \
    -Dmdep.outputFile=target/classpath.txt -DincludeScope=runtime
cat classpath/app/target/classpath.txt
```

The checked goal emits `api`, `support`, `adapter` under all three Maven versions, and Java loads `chosen=support`. Explicit `preOrder` and `levelOrder` settings leave this goal's output unchanged. The Java runner separately rearranges the classpath to `api`, `adapter`, `support` and checks `chosen=adapter`. That second order is constructed by the runner; it is not emitted by Maven.

### Reproducible archives

```shell
./scripts/lab.sh verify --only reproducibility
```

The resource-only JAR is packaged twice, with 2.2 seconds between builds. The hashes differ under 3.9.16 and match under 3.10.0 and the Maven 4 RC. An explicit output timestamp also gives 3.9.16 matching hashes. Changing the resource changes the hash. The runner restores the source resource afterward. This compares local rebuilds in one environment.

### Maven 4 producer and Maven 3 consumer

```shell
./scripts/maven.sh 4.0.0-rc-7 -f examples/maven4/pom.xml clean install
./scripts/maven.sh 3.10.0 -f examples/consumer/pom.xml clean verify
```

The producer uses model `4.1.0`, parent inference, subprojects, and a sibling dependency without an explicit version. Its installed `greeting` POM uses model `4.0.0` and retains a parent reference; the parent is installed too. Flattening is not enabled.

The `consumer_pom` comparison also launches the Maven 3 consumer, calls the library, and checks the result file for `Hello from a Maven 4 library`.

### Credential scope

```shell
./scripts/lab.sh verify --only credentials
```

The temporary loopback repository uses dummy `demo` / `demo-password` credentials. The runner clears its own cached `secured` artifact before each case and closes the server afterward. Request records contain a path and authentication boolean, not an Authorization header.

For normal `compile` resolution, a matching explicit origin permits authentication and a wrong explicit origin refuses it. With no declared origin, default mode warns and permits authentication; strict mode refuses it.

The pinned Dependency Plugin 3.11.0 `get` comparison sends the dummy credentials with the wrong origin despite the warning. That result is recorded separately from normal lifecycle resolution. The example does not establish its cause or a fix.

## Save a completed full run

Use the run ID printed by a successful full comparison:

```shell
./scripts/lab.sh snapshot RUN_ID .lab/snapshots/RUN_ID
```

Replace both `RUN_ID` values with that exact ID. Snapshot creation rejects partial, failed, and unfinished runs, files changed after completion, and an existing destination. It copies only files listed in that run's completion record. It does not combine results from different runs.

## Build versions and cleanup

The parents pin Clean **3.5.0**, Resources **3.3.1**, Compiler **3.15.0**, Surefire **3.5.4**, JAR **3.5.0**, Install **3.1.4**, Deploy **3.1.4**, and Dependency **3.11.0**. Deploy is not executed. The runner's record tests use JUnit **6.1.3**.

The comparison launch scripts target macOS and Linux; Windows comparison execution is not supported. The standard wrapper includes `mvnw.cmd` for ordinary Maven use.

Generated `target/` directories and `.lab/` are ignored. Delete them when you no longer need the builds, downloaded tools, or local reports. A later run recreates them. Deleting `.lab` also deletes its saved snapshots.
