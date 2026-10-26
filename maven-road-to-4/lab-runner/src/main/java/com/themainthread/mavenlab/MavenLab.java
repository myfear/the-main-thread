package com.themainthread.mavenlab;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class MavenLab {
    private static final String DEPENDENCY = "org.apache.maven.plugins:maven-dependency-plugin:3.11.0";
    private final Path root;
    private final MavenTools tools;
    private RunRecord record;

    private MavenLab(Path root) throws IOException {
        this.root = root;
        tools = new MavenTools(root);
    }

    public static void main(String[] args) {
        try {
            if (Runtime.version().feature() < 25) {
                throw new IllegalArgumentException("The comparison runner requires JDK 25 or newer");
            }
            Path root = Path.of("").toAbsolutePath().normalize();
            if (!Files.isRegularFile(root.resolve("pom.xml"))) {
                throw new IllegalArgumentException("Run from the maven-road-to-4 directory");
            }
            int exit = new MavenLab(root).dispatch(List.of(args));
            if (exit != 0) {
                System.exit(exit);
            }
        } catch (Exception error) {
            // CLI diagnostics, not application logging.
            System.err.println("Maven lab: " + error.getMessage());
            System.exit(1);
        }
    }

    private int dispatch(List<String> args) throws Exception {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Usage: lab.sh verify [--only SCENARIO] | bootstrap [VERSION]"
                    + " | maven VERSION [ARGS] | snapshot RUN_ID DESTINATION");
        }
        if (args.getFirst().equals("snapshot")) {
            require(args.size() == 3, "Usage: lab.sh snapshot RUN_ID DESTINATION");
            require(args.get(1).matches("[A-Za-z0-9-]+"), "Invalid run id");
            Path source = root.resolve(".lab/runs").resolve(args.get(1));
            RunRecord.snapshot(source, Path.of(args.get(2)));
            IO.println("Snapshot saved: " + Path.of(args.get(2)).toAbsolutePath());
            return 0;
        }
        Path lockFile = root.resolve(".lab/command.lock");
        Files.createDirectories(lockFile.getParent());
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            require(lock != null, "Another lab command is using this directory; wait for it to finish");
            switch (args.getFirst()) {
                case "bootstrap" -> {
                    require(args.size() <= 2, "Usage: lab.sh bootstrap [VERSION]");
                    for (String version : args.size() == 2 ? List.of(args.get(1)) : MavenTools.VERSIONS) {
                        tools.install(version);
                    }
                }
                case "maven" -> {
                    require(args.size() >= 2, "Usage: maven.sh VERSION [ARGS]");
                    return tools.interactive(args.get(1), args.subList(2, args.size()));
                }
                case "verify" -> {
                    List<String> selected = RunRecord.SCENARIOS;
                    if (args.size() != 1) {
                        require(args.size() == 3 && args.get(1).equals("--only")
                                && RunRecord.SCENARIOS.contains(args.get(2)), "Unknown scenario or verify arguments");
                        selected = List.of(args.get(2));
                    }
                    verify(selected);
                }
                default -> throw new IllegalArgumentException("Unknown action: " + args.getFirst());
            }
        }
        return 0;
    }

    private void verify(List<String> selected) throws Exception {
        Map<String, String> runtime = Map.of(
                "java_version", System.getProperty("java.version"),
                "java_runtime_version", System.getProperty("java.runtime.version"),
                "java_vendor", System.getProperty("java.vendor"),
                "os_name", System.getProperty("os.name"), "os_arch", System.getProperty("os.arch"));
        Path sources = root.resolve("lab-runner/src/main/java");
        StringBuilder fingerprints = new StringBuilder();
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                fingerprints.append(sources.relativize(file)).append(':').append(RunRecord.hash(file)).append('\n');
            }
        }
        Path fingerprint = root.resolve(".lab/runner-inputs.txt");
        Files.writeString(fingerprint, fingerprints);
        record = new RunRecord(root.resolve(".lab/runs"), selected, runtime,
                tools.checksums(), RunRecord.hash(fingerprint));
        IO.println("Run: " + record.id() + " (" + (record.isFull() ? "full" : "partial") + ")");
        try {
            for (String version : MavenTools.VERSIONS) {
                tools.install(version);
            }
            for (String name : selected) {
                record.start(name);
                IO.println("Checking " + name);
                try {
                    Map<String, Object> results = switch (name) {
                        case "validation" -> validation();
                        case "classpath" -> classpath();
                        case "reproducibility" -> reproducibility();
                        case "consumer_pom" -> consumerPom();
                        case "credentials" -> credentials();
                        default -> throw new IllegalArgumentException(name);
                    };
                    record.pass(name, results);
                } catch (Exception error) {
                    record.fail(name, error);
                    throw error;
                }
            }
            record.finish();
            IO.println("Requested checks passed. Results: " + record.directory().resolve("summary.json"));
        } catch (Exception error) {
            record.fail(error);
            IO.println("Failed run retained: " + record.directory().resolve("summary.json"));
            throw error;
        }
    }

    private MavenTools.Result maven(String version, String project, String name, Integer expected, String... args)
            throws Exception {
        return tools.maven(version, root.resolve(project), List.of(args), output(name + ".log"), expected);
    }

    private Path output(String name) {
        return record.directory().resolve(name);
    }

    private Map<String, Object> validation() throws Exception {
        Map<String, Object> outcomes = new LinkedHashMap<>();
        for (String version : MavenTools.VERSIONS) {
            Map<String, Object> exits = new LinkedHashMap<>();
            for (String kind : List.of("plugins", "dependencies")) {
                MavenTools.Result result = maven(version, "examples/duplicate-" + kind,
                        "duplicate-" + kind + "-" + version, null, "validate");
                require(result.output().contains("must be unique"), "Missing duplicate diagnostic");
                require(result.exit() == (version.equals("3.9.16") ? 0 : 1),
                        "Unexpected duplicate validation on " + version);
                exits.put("duplicate_" + kind + "_exit", result.exit());
            }
            maven(version, "examples/fixed-plugins", "fixed-plugins-" + version, 0, "validate");
            outcomes.put(version, exits);
        }
        return outcomes;
    }

    private Map<String, Object> classpath() throws Exception {
        Map<String, Object> outcomes = new LinkedHashMap<>();
        List<String> expected = List.of("api-1.0.0.jar", "support-1.0.0.jar", "adapter-1.0.0.jar");
        for (String version : MavenTools.VERSIONS) {
            maven(version, ".", "reactor-" + version, 0, "clean", "install");
            List<String> jars = dependencyClasspath(version, "classpath/app", "classpath-" + version);
            require(jarNames(jars).equals(expected), "Unexpected classpath on " + version);
            String resource = javaResult("classpath/app", jars, "ResourceProbe", "resource-" + version);
            require(resource.equals("chosen=support"), "Unexpected resource on " + version);
            outcomes.put(version, Map.of("classpath", jarNames(jars), "resource", resource));
        }
        List<String> jars = List.of();
        for (String order : List.of("preOrder", "levelOrder")) {
            jars = dependencyClasspath("3.10.0", "classpath/app", "classpath-" + order,
                    "-Daether.system.dependencyVisitor=" + order);
            require(jarNames(jars).equals(expected), "Unexpected explicit " + order + " result");
            outcomes.put("3.10.0-" + order, Map.of("classpath", jarNames(jars)));
        }
        List<String> manual = List.of(jars.get(0), jars.get(2), jars.get(1));
        String resource = javaResult("classpath/app", manual, "ResourceProbe", "resource-manually-reordered");
        require(resource.equals("chosen=adapter"), "Manually rearranged lookup failed");
        outcomes.put("manually_reordered", Map.of("classpath", jarNames(manual), "resource", resource,
                "origin", "explicitly reordered by the Java runner, not emitted by Maven"));
        return outcomes;
    }

    private List<String> dependencyClasspath(String version, String project, String name, String... properties)
            throws Exception {
        Path result = output(name + ".txt");
        List<String> args = new ArrayList<>(List.of(DEPENDENCY + ":build-classpath",
                "-Dmdep.outputFile=" + result, "-DincludeScope=runtime"));
        args.addAll(List.of(properties));
        maven(version, project, name, 0, args.toArray(String[]::new));
        return Arrays.asList(Files.readString(result).strip().split(Pattern.quote(File.pathSeparator)));
    }

    private static List<String> jarNames(List<String> jars) {
        return jars.stream().map(jar -> Path.of(jar).getFileName().toString()).toList();
    }

    private String javaResult(String project, List<String> jars, String main, String name) throws Exception {
        Path result = output(name + ".txt");
        String classpath = root.resolve(project + "/target/classes") + File.pathSeparator
                + String.join(File.pathSeparator, jars);
        MavenTools.Result process = tools.execute(List.of(MavenTools.javaCommand(), "-cp", classpath,
                "com.themainthread.mavenlab." + main, result.toString()), output(name + ".log"));
        require(process.exit() == 0, "Java lookup failed; read " + output(name + ".log"));
        return Files.readString(result).strip();
    }

    private Map<String, Object> reproducibility() throws Exception {
        Map<String, Object> outcomes = new LinkedHashMap<>();
        Path resource = root.resolve("examples/reproducible/src/main/resources/build-input.txt");
        byte[] original = Files.readAllBytes(resource);
        String baseline310 = null;
        try {
            for (String version : MavenTools.VERSIONS) {
                List<String> hashes = rebuild(version, "repeatable-" + version);
                boolean equal = hashes.getFirst().equals(hashes.getLast());
                require(equal == !version.equals("3.9.16"), "Unexpected archive comparison on " + version);
                outcomes.put(version, Map.of("hashes", hashes, "equal", equal));
                if (version.equals("3.10.0")) {
                    baseline310 = hashes.getFirst();
                }
            }
            List<String> hashes = rebuild("3.9.16", "repeatable-explicit-timestamp",
                    "-Dproject.build.outputTimestamp=1980-02-01T00:00:00Z");
            require(hashes.getFirst().equals(hashes.getLast()), "Explicit timestamp did not stabilize archive");
            outcomes.put("3.9.16-explicit-timestamp", Map.of("hashes", hashes, "equal", true));
            Files.writeString(resource, "a changed input, even with fixed archive timestamps\n");
            maven("3.10.0", "examples/reproducible", "changed-input", 0, "clean", "package");
            String changed = RunRecord.hash(root.resolve("examples/reproducible/target/repeatable-jar-1.0.0.jar"));
            require(baseline310 != null && !changed.equals(baseline310), "Input change was invisible");
            outcomes.put("changed_input", Map.of("sha256", changed, "equal", false));
        } finally {
            Files.write(resource, original);
        }
        return outcomes;
    }

    private List<String> rebuild(String version, String name, String... properties) throws Exception {
        List<String> hashes = new ArrayList<>();
        for (int attempt = 1; attempt <= 2; attempt++) {
            List<String> args = new ArrayList<>(List.of("clean", "package"));
            args.addAll(List.of(properties));
            maven(version, "examples/reproducible", name + "-" + attempt, 0, args.toArray(String[]::new));
            hashes.add(RunRecord.hash(root.resolve("examples/reproducible/target/repeatable-jar-1.0.0.jar")));
            if (attempt == 1) {
                Thread.sleep(Duration.ofMillis(2200));
            }
        }
        return hashes;
    }

    private Map<String, Object> consumerPom() throws Exception {
        MavenTools.Result rejected = maven("3.10.0", "examples/maven4", "model-4.1-on-maven3", null, "validate");
        require(rejected.exit() == 1 && rejected.output().contains("4.1.0"), "Maven 3 did not reject model 4.1");
        maven("4.0.0-rc-7", "examples/maven4", "model-4.1-on-maven4", 0, "clean", "install");
        Path installed = root.resolve(".lab/repository/com/themainthread/mavenlab/greeting/1.0.0/greeting-1.0.0.pom");
        String pom = Files.readString(installed);
        require(pom.contains("<modelVersion>4.0.0</modelVersion>"), "Consumer POM is not model 4.0");
        Files.writeString(output("greeting-consumer.pom"), pom);
        maven("3.10.0", "examples/consumer", "maven3-consumer", 0, "clean", "verify");
        List<String> jars = dependencyClasspath("3.10.0", "examples/consumer", "maven3-consumer-classpath");
        String message = javaResult("examples/consumer", jars, "Consumer", "consumer-message");
        require(message.equals("Hello from a Maven 4 library"), "Maven 3 consumer did not call library");
        return Map.of("source_model", "4.1.0", "consumer_model", "4.0.0",
                "maven3_model_exit", rejected.exit(), "message", message);
    }

    private Map<String, Object> credentials() throws Exception {
        Map<String, Object> outcomes = new LinkedHashMap<>();
        Path work = Files.createDirectories(root.resolve(".lab/work").resolve(record.id()));
        Path project = Files.createDirectories(work.resolve("credentials-project"));
        try (RepositoryProbe server = new RepositoryProbe()) {
            Files.writeString(project.resolve("pom.xml"), """
                    <project><modelVersion>4.0.0</modelVersion>
                      <parent><groupId>com.themainthread.mavenlab</groupId>
                        <artifactId>maven-road-to-4</artifactId><version>1.0.0</version>
                        <relativePath>%s</relativePath></parent>
                      <artifactId>credentials-probe</artifactId>
                      <dependencies><dependency><groupId>com.themainthread.mavenlab</groupId>
                        <artifactId>secured</artifactId><version>1.0.0</version>
                      </dependency></dependencies>
                      <profiles><profile><id>lab</id><repositories><repository>
                        <id>lab-repo</id><url>%s/repository</url>
                        <releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled></snapshots>
                      </repository></repositories></profile></profiles>
                    </project>
                    """.formatted(xml(root.resolve("pom.xml").toString()), server.origin()));
            List<CredentialCase> cases = List.of(
                    new CredentialCase("maven3.9", "3.9.16", null, "origin", true),
                    new CredentialCase("maven3.10-without-origin", "3.10.0", null, "origin", true),
                    new CredentialCase("maven3.10-strict-without-origin", "3.10.0", null, "strict", false),
                    new CredentialCase("maven3.10-with-origin", "3.10.0", server.origin(), "origin", true),
                    new CredentialCase("maven3.10-wrong-origin", "3.10.0", "http://127.0.0.1:1", "origin", false));
            for (CredentialCase test : cases) {
                Path settings = work.resolve("settings-" + test.name() + ".xml");
                String origins = test.origin() == null ? "" : "<repositoryOrigins><repositoryOrigin>"
                        + test.origin() + "</repositoryOrigin></repositoryOrigins>";
                Files.writeString(settings, """
                        <settings><servers><server><id>lab-repo</id><username>demo</username>
                          <password>demo-password</password>%s</server></servers></settings>
                        """.formatted(origins));
                clearCredentialCache();
                MavenTools.Result result = tools.maven(test.version(), project,
                        List.of("-Plab",
                                "-Dmaven.repository.credentialScope=" + test.scope(), "compile"),
                        output("credentials-" + test.name() + ".log"), null, settings);
                List<Map<String, Object>> requests = server.drainRequests();
                Files.writeString(output("credentials-" + test.name() + "-requests.json"), Json.encode(requests));
                require(result.exit() == (test.success() ? 0 : 1), "Unexpected credential exit: " + test.name());
                require(authenticated(requests) == test.success(), "Unexpected authentication: " + test.name());
                outcomes.put(test.name(), Map.of("exit", result.exit(), "requests", requests));
            }
            clearCredentialCache();
            MavenTools.Result result = tools.maven("3.10.0", project,
                    List.of("-Plab",
                            DEPENDENCY + ":get", "-Dartifact=com.themainthread.mavenlab:secured:1.0.0",
                            "-Dtransitive=false"), output("credentials-dependency-get-wrong-origin.log"), null,
                    work.resolve("settings-maven3.10-wrong-origin.xml"));
            List<Map<String, Object>> requests = server.drainRequests();
            Files.writeString(output("credentials-dependency-get-wrong-origin-requests.json"), Json.encode(requests));
            require(result.exit() == 0 && authenticated(requests), "The pinned dependency:get behavior changed");
            outcomes.put("dependency-get-wrong-origin", Map.of("exit", result.exit(), "requests", requests,
                    "note", "Plugin 3.11.0 get sent dummy credentials despite the origin warning"));
        }
        return outcomes;
    }

    private void clearCredentialCache() throws IOException {
        MavenTools.deleteTree(root.resolve(".lab/repository/com/themainthread/mavenlab/secured"));
    }

    private static boolean authenticated(List<Map<String, Object>> requests) {
        return requests.stream().anyMatch(request -> Boolean.TRUE.equals(request.get("authorized")));
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record CredentialCase(String name, String version, String origin, String scope, boolean success) {
    }
}
