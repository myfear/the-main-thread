import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ProjectCatalogTest {
    @TempDir Path root;
    static final String SHA = "a".repeat(40);

    @BeforeEach void init() throws Exception {
        ProjectCatalog.git(root, "init", "-q");
        ProjectCatalog.git(root, "config", "user.name", "Catalog test");
        ProjectCatalog.git(root, "config", "user.email", "test@example.invalid");
    }

    void file(String path, String content) throws Exception { ProjectCatalog.write(root.resolve(path), content); }
    void stage() throws Exception { ProjectCatalog.git(root, "add", "--all"); }
    void commit() throws Exception {
        stage();
        ProjectCatalog.git(root, "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "-qm", "fixture");
    }
    String pom(String properties) { return "<project xmlns='http://maven.apache.org/POM/4.0.0'><properties>" + properties + "</properties></project>"; }
    List<ProjectCatalog.Project> projects() throws Exception { return ProjectCatalog.discover(root); }

    @Test void discoversTrackedCompanionsRegardlessOfBuildOrReadme() throws Exception {
        file("maven/pom.xml", pom("<maven.compiler.release>21</maven.compiler.release>"));
        file("nested/service/pom.xml", pom("<maven.compiler.release>25</maven.compiler.release>"));
        file("gradle/build.gradle", "plugins {}");
        file("jbang/Hello.java", "//JAVA 21+");
        file("scripts/run.sh", "true");
        file("readme-only/README.md", "# Companion");
        file(".github/workflows/test.yml", "name: test");
        file("README.md", "root");
        stage();
        file("untracked/pom.xml", pom(""));
        assertEquals(List.of("gradle", "jbang", "maven", "nested", "readme-only", "scripts"), projects().stream().map(ProjectCatalog.Project::path).toList());
        assertEquals(List.of("maven"), projects().stream().filter(ProjectCatalog.Project::buildable).map(ProjectCatalog.Project::path).toList());
        assertEquals("25", projects().stream().filter(p -> p.path().equals("nested")).findFirst().orElseThrow().java());
    }

    @Test void overridesAreOptionalTypedAndDoNotAlterBuildSelection() throws Exception {
        file("demo/pom.xml", pom("<maven.compiler.release>21</maven.compiler.release>"));
        file("demo/.companion.json", "{\"include\":false,\"java\":\"25\",\"articleUrl\":\"https://www.the-main-thread.com/p/example\"}");
        stage();
        var p = projects().getFirst();
        assertFalse(p.included()); assertTrue(p.buildable()); assertEquals("25", p.java()); assertEquals("21", p.buildJava());
        file("demo/.companion.json", "{\"include\":\"false\"}");
        assertThrows(IllegalArgumentException.class, this::projects);
        file("demo/.companion.json", "{\"build\":true}");
        assertThrows(IllegalArgumentException.class, this::projects);
        file("demo/.companion.json", "{\"articleUrl\":\"javascript:alert(1)\"}");
        assertThrows(IllegalArgumentException.class, this::projects);
    }

    @Test void buildJdkCanDifferFromApplicationBytecodeTarget() throws Exception {
        file("demo/pom.xml", pom("<maven.compiler.release>17</maven.compiler.release>"
                + "<build.jdk>25</build.jdk><maven.build.java.version>${build.jdk}</maven.build.java.version>"));
        file("demo/runner/pom.xml", pom("<maven.compiler.release>25</maven.compiler.release>"));
        stage();
        var project = projects().getFirst();
        assertEquals("17", project.java());
        assertEquals("25", project.buildJava());
        assertEquals(List.of(new ProjectCatalog.Build("demo", "25", false)),
                ProjectCatalog.matrix(root, "workflow_dispatch", "", "HEAD", true));
    }

    @Test void rejectsInvalidBuildJdkBeforeCreatingMatrix() throws Exception {
        for (String version : List.of("25-ea", "", "${missing}")) {
            file("demo/pom.xml", pom("<maven.compiler.release>17</maven.compiler.release>"
                    + "<maven.build.java.version>" + version + "</maven.build.java.version>"));
            stage();
            var error = assertThrows(IllegalArgumentException.class,
                    () -> ProjectCatalog.matrix(root, "workflow_dispatch", "", "HEAD", true));
            assertTrue(error.getMessage().contains("maven.build.java.version"));
        }
    }

    @Test void metadataUsesSourcePropertiesAndOnlyConsistentNestedValues() throws Exception {
        file("single/service/pom.xml", pom("<jdk>25</jdk><maven.compiler.release>${jdk}</maven.compiler.release><quarkus.platform.version>3.40.1</quarkus.platform.version>"));
        file("mixed/a/pom.xml", pom("<maven.compiler.release>21</maven.compiler.release>"));
        file("mixed/b/pom.xml", pom("<maven.compiler.release>25</maven.compiler.release>"));
        file("top/pom.xml", pom("<maven.compiler.release>21</maven.compiler.release><substack-article.url> https://www.the-main-thread.com/p/current </substack-article.url>"));
        file("top/module/pom.xml", pom("<maven.compiler.release>25</maven.compiler.release>"));
        stage();
        var p = projects();
        assertEquals("", p.get(0).java()); assertEquals("25", p.get(1).java()); assertEquals("3.40.1", p.get(1).quarkus());
        assertEquals("21", p.get(2).java()); assertEquals("https://www.the-main-thread.com/p/current", p.get(2).articleUrl());
    }

    @Test void invalidLegacyLinksAreFlaggedAndOmitted() throws Exception {
        assertFalse(ProjectCatalog.validArticle("https://https://www.the-main-thread.com/p/demo"));
        assertFalse(ProjectCatalog.validArticle("https://www.the-main-thread-com/p/demo"));
        file("demo/pom.xml", pom("<substack-article.url>https://https://www.the-main-thread.com/p/demo</substack-article.url>"));
        stage();
        assertEquals("", projects().getFirst().articleUrl());
    }

    @Test void missingRootPomsAndDeletedProjectsDisappearFromBuildSelection() throws Exception {
        file("demo/pom.xml", pom("")); file("demo/README.md", "demo"); stage();
        Files.delete(root.resolve("demo/pom.xml"));
        assertFalse(projects().getFirst().buildable());
        Files.delete(root.resolve("demo/README.md"));
        assertTrue(projects().isEmpty());
    }

    @Test void matrixSupportsDiffsFullBuildsAndCatalogOnlyDispatch() throws Exception {
        file("demo/pom.xml", pom("<maven.compiler.release>25</maven.compiler.release>"));
        file("nested/module/pom.xml", pom(""));
        file("video-pipeline/pom.xml", pom("")); commit();
        String base = ProjectCatalog.git(root, "rev-parse", "HEAD").strip();
        file("nested/README.md", "changed"); commit();
        assertTrue(ProjectCatalog.matrix(root, "push", base, "HEAD", false).isEmpty());
        file("demo/src/App.java", "class App {}"); commit();
        assertEquals("demo", ProjectCatalog.matrix(root, "pull_request", base, "HEAD", false).getFirst().path());
        assertEquals(1, ProjectCatalog.matrix(root, "workflow_dispatch", "", "HEAD", true).size());
        assertTrue(ProjectCatalog.matrix(root, "workflow_dispatch", "", "HEAD", false).isEmpty());
        assertEquals(1, ProjectCatalog.matrix(root, "push", "0".repeat(40), "HEAD", false).size());
        assertThrows(IllegalArgumentException.class, () -> ProjectCatalog.matrix(root, "push", "", "HEAD", false));
    }

    @Test void sharedAutomationChangesExerciseExistingBuilds() throws Exception {
        file("demo/pom.xml", pom("")); commit();
        String base = ProjectCatalog.git(root, "rev-parse", "HEAD").strip();
        file(".github/actions/setup-catalog/action.yml", "name: catalog"); commit();
        assertEquals(1, ProjectCatalog.matrix(root, "push", base, "HEAD", false).size());
    }

    @Test void renderIncludesEveryCompanionAndPreservesExistingBadgeUrls() throws Exception {
        file("built/pom.xml", pom("<maven.compiler.release>21</maven.compiler.release>"));
        file("pending/pom.xml", pom("")); file("manual/README.md", "manual"); stage();
        file("badges/built.json", "{\"message\":\"passing\"}");
        file("badges/built-metadata.json", "{\"java\":\"8\",\"substackUrl\":\"https://obsolete.invalid/\",\"tests\":{\"total\":2,\"passed\":1,\"skipped\":1}}");
        file("badges/deleted.json", "{\"message\":\"passing\"}");
        String table = ProjectCatalog.table(projects(), root.resolve("badges"), "myfear/the-main-thread");
        assertTrue(table.contains("/badges/built.json")); assertTrue(table.contains("| 21 |"));
        assertTrue(table.contains("✅ 1/2 (⏭️ 1)")); assertTrue(table.contains("Not configured")); assertTrue(table.contains("Not yet built"));
        assertFalse(table.contains("deleted")); assertFalse(table.contains("obsolete"));
        assertEquals(3, table.lines().filter(l -> l.startsWith("| **[")).count());
    }

    @Test void renderingIsIdempotentAndPreservesSurroundingTextAndLineEndings() {
        String original = "before\r\n" + ProjectCatalog.START + "\r\nold\r\n" + ProjectCatalog.END + "\r\nafter";
        String rendered = ProjectCatalog.replaceTable(original, "new\n");
        assertEquals("before\r\n" + ProjectCatalog.START + "\r\nnew\r\n" + ProjectCatalog.END + "\r\nafter", rendered);
        assertEquals(rendered, ProjectCatalog.replaceTable(rendered, "new\n"));
        assertThrows(IllegalArgumentException.class, () -> ProjectCatalog.replaceTable("no markers", ""));
        assertThrows(IllegalArgumentException.class, () -> ProjectCatalog.replaceTable(original + ProjectCatalog.END, ""));
        assertThrows(IllegalArgumentException.class, () -> ProjectCatalog.replaceTable(ProjectCatalog.END + ProjectCatalog.START, ""));
    }

    @Test void reportAggregationCountsModulesWithoutDoubleCountingContainers() throws Exception {
        file("reports/a/TEST-a.xml", "<testsuite tests='4' failures='1' errors='1' skipped='1'/>");
        file("reports/b/TEST-b.xml", "<testsuites><testsuite tests='5'><testsuite tests='5' skipped='1'/></testsuite></testsuites>");
        var tests = ProjectCatalog.tests(root.resolve("reports"));
        assertEquals(new ProjectCatalog.Tests(9, 5, 1, 1, 2), tests);
        assertNull(ProjectCatalog.tests(root.resolve("absent")));
        file("reports/c/TEST-c.xml", "<testsuite tests='0' failures='1'/>");
        assertThrows(Exception.class, () -> ProjectCatalog.tests(root.resolve("reports")));
    }

    @Test void xmlRejectsExternalEntities() throws Exception {
        file("demo/pom.xml", "<!DOCTYPE project [<!ENTITY data SYSTEM 'file:///etc/passwd'>]><project><properties><java.version>&data;</java.version></properties></project>");
        stage();
        assertThrows(Exception.class, this::projects);
    }

    void aggregate(long number, long attempt) throws Exception {
        ProjectCatalog.aggregate(root, root.resolve("matrix.json"), root.resolve("results"), root.resolve("badges"), SHA, number, attempt);
    }
    void result(String status, long number, long attempt) throws Exception {
        file("results/result-demo/status.properties", "project=demo\nsourceSha=" + SHA + "\nrunNumber=" + number + "\nrunAttempt=" + attempt + "\nstatus=" + status);
    }
    @Test void resultsTrackProvenanceAndPreventOutOfOrderOverwrites() throws Exception {
        file("demo/pom.xml", pom("")); stage();
        file("matrix.json", "[{\"path\":\"demo\"}]");
        result("passing", 10, 2); aggregate(10, 2);
        String original = Files.readString(root.resolve("badges/demo-metadata.json"));
        result("failing", 9, 10); aggregate(9, 10); assertEquals(original, Files.readString(root.resolve("badges/demo-metadata.json")));
        result("failing", 10, 1); aggregate(10, 1); assertEquals(original, Files.readString(root.resolve("badges/demo-metadata.json")));
        result("failing", 10, 3); aggregate(10, 3);
        assertEquals("failing", ProjectCatalog.readJson(root.resolve("badges/demo.json")).path("message").asText());
        assertTrue(ProjectCatalog.readJson(root.resolve("badges/demo-metadata.json")).path("tests").isNull());
        String updated = Files.readString(root.resolve("badges/demo-metadata.json")); aggregate(10, 3);
        assertEquals(updated, Files.readString(root.resolve("badges/demo-metadata.json")));
    }

    @Test void missingAndMismatchedResultsReplaceStaleGreenWithNoResult() throws Exception {
        file("demo/pom.xml", pom("")); stage(); file("matrix.json", "[{\"path\":\"demo\"}]");
        file("badges/demo.json", "{\"message\":\"passing\"}"); aggregate(1, 1);
        assertEquals("no result", ProjectCatalog.readJson(root.resolve("badges/demo.json")).path("message").asText());
        result("passing", 1, 1); aggregate(2, 1);
        assertEquals("no result", ProjectCatalog.readJson(root.resolve("badges/demo.json")).path("message").asText());
        file("matrix.json", "[]");
        String before = Files.readString(root.resolve("badges/demo-metadata.json")); aggregate(3, 1);
        assertEquals(before, Files.readString(root.resolve("badges/demo-metadata.json")));
    }

    @Test void retryingFailedJobsRetainsSuccessfulEarlierAttemptArtifacts() throws Exception {
        file("demo/pom.xml", pom("")); stage(); file("matrix.json", "[{\"path\":\"demo\"}]");
        result("passing", 10, 1); aggregate(10, 2);
        var metadata = ProjectCatalog.readJson(root.resolve("badges/demo-metadata.json"));
        assertEquals("passing", metadata.path("status").asText());
        assertEquals(1, metadata.path("runAttempt").asLong());
        result("failing", 10, 2); aggregate(10, 2);
        result("passing", 10, 1); aggregate(10, 3);
        assertEquals("failing", ProjectCatalog.readJson(root.resolve("badges/demo.json")).path("message").asText());
    }

    @Test void allSkippedTestsAreNotDisplayedAsPassing() throws Exception {
        var data = ProjectCatalog.JSON.readTree("{\"total\":2,\"passed\":0,\"skipped\":2}");
        assertTrue(ProjectCatalog.testDisplay(data).startsWith("⏭️"));
    }

    @Test void pinValidationIncludesCompositeActionsAndNestedExamples() throws Exception {
        file(".github/actions/setup-catalog/action.yml", "steps:\n  - uses: actions/setup-java@" + SHA);
        file(".github/workflows/main.yml", "steps:\n  - uses: actions/checkout@" + SHA);
        file("demo/.github/workflows/example.yml", "steps:\n  - uses: actions/checkout@" + SHA); stage();
        ProjectCatalog.validatePins(root);
        file("demo/.github/workflows/example.yml", "steps:\n  - uses: actions/checkout@" + "b".repeat(40));
        assertThrows(IllegalArgumentException.class, () -> ProjectCatalog.validatePins(root));
        file("demo/.github/workflows/example.yml", "steps:\n  - uses: actions/checkout@v7");
        assertThrows(IllegalArgumentException.class, () -> ProjectCatalog.validatePins(root));
    }

    @Test void publisherRetriesBothBranchesAndPreservesConcurrentChanges() throws Exception {
        Path seed = root.resolve("seed"), remote = root.resolve("remote.git"), workspace = root.resolve("workspace"), competitor = root.resolve("competitor");
        Files.createDirectories(seed);
        ProjectCatalog.git(seed, "init", "-qb", "main");
        configure(seed);
        ProjectCatalog.write(seed.resolve("demo/pom.xml"), pom("<maven.compiler.release>21</maven.compiler.release>"));
        ProjectCatalog.write(seed.resolve("README.md"), "Introduction\n" + ProjectCatalog.START + "\nold\n" + ProjectCatalog.END + "\nFooter\n");
        Files.createDirectories(seed.resolve(".github/scripts"));
        for (String name : List.of("ProjectCatalog.java", "publish-catalog.sh"))
            Files.copy(Path.of(".github/scripts", name), seed.resolve(".github/scripts/" + name));
        ProjectCatalog.git(seed, "add", "."); ProjectCatalog.git(seed, "commit", "-qm", "initial");
        String source = ProjectCatalog.git(seed, "rev-parse", "HEAD").strip();
        ProjectCatalog.git(root, "init", "--bare", "-q", remote.toString());
        ProjectCatalog.git(seed, "remote", "add", "origin", remote.toString());
        ProjectCatalog.git(seed, "push", "-q", "origin", "main");
        ProjectCatalog.git(remote, "symbolic-ref", "HEAD", "refs/heads/main");
        ProjectCatalog.git(seed, "checkout", "-q", "--orphan", "badges");
        ProjectCatalog.git(seed, "rm", "-rfq", ".");
        ProjectCatalog.write(seed.resolve("retained.json"), "{\"message\":\"passing\"}");
        ProjectCatalog.git(seed, "add", "."); ProjectCatalog.git(seed, "commit", "-qm", "badges");
        ProjectCatalog.git(seed, "push", "-q", "origin", "badges");
        ProjectCatalog.git(root, "clone", "-q", remote.toString(), workspace.toString());
        ProjectCatalog.git(root, "clone", "-q", remote.toString(), competitor.toString()); configure(competitor);
        Path main = workspace.resolve("main-repo"), badges = workspace.resolve("badges-branch");
        ProjectCatalog.git(root, "clone", "-qb", "main", remote.toString(), main.toString()); configure(main);
        ProjectCatalog.git(root, "clone", "-qb", "badges", remote.toString(), badges.toString()); configure(badges);
        ProjectCatalog.write(workspace.resolve("plan/matrix.json"), "[{\"path\":\"demo\"}]");
        ProjectCatalog.write(workspace.resolve("results/result-demo/status.properties"),
                "project=demo\nsourceSha=" + source + "\nrunNumber=42\nrunAttempt=1\nstatus=passing");

        // A competing push between our fetch and push forces the publisher to
        // regenerate on the new branch tip. These hooks touch only this fixture.
        for (String branch : List.of("main", "badges")) {
            Path checkout = branch.equals("main") ? main : badges;
            Path sentinel = root.resolve(branch + "-raced");
            String edit = branch.equals("main") ? "printf '\\nConcurrent human edit\\n' >> README.md"
                    : "printf '{\"message\":\"passing\"}\\n' > concurrent.json";
            String hook = "#!/bin/sh\nset -eu\nif [ ! -e " + quote(sentinel) + " ]; then\n"
                    + "touch " + quote(sentinel) + "\ncd " + quote(competitor) + "\n"
                    + "git fetch -q origin " + branch + "\ngit checkout -q -B " + branch + " FETCH_HEAD\n"
                    + edit + "\ngit add .\ngit commit -qm competing\ngit push -q origin HEAD:" + branch + "\nfi\n";
            Path hookPath = checkout.resolve(".git/hooks/pre-push");
            ProjectCatalog.write(hookPath, hook); assertTrue(hookPath.toFile().setExecutable(true));
        }
        var env = new HashMap<String, String>();
        env.put("GITHUB_ACTIONS", "true"); env.put("GITHUB_WORKSPACE", workspace.toString());
        env.put("GITHUB_SHA", source); env.put("GITHUB_RUN_NUMBER", "42"); env.put("GITHUB_RUN_ATTEMPT", "1");
        env.put("GITHUB_REPOSITORY", "myfear/the-main-thread");
        runPublisher(workspace, env);
        assertTrue(Files.exists(root.resolve("main-raced"))); assertTrue(Files.exists(root.resolve("badges-raced")));
        String readme = ProjectCatalog.git(remote, "show", "main:README.md");
        assertTrue(readme.contains("Concurrent human edit")); assertTrue(readme.contains("**[demo]"));
        assertTrue(ProjectCatalog.git(remote, "show", "badges:concurrent.json").contains("passing"));
        assertTrue(ProjectCatalog.git(remote, "show", "badges:retained.json").contains("passing"));
        for (String branch : List.of("main", "badges")) {
            assertEquals("Markus Eisele <markus@eisele.net>|Markus Eisele <markus@eisele.net>",
                    ProjectCatalog.git(remote, "log", "-1", "--format=%an <%ae>|%cn <%ce>", branch).strip());
        }
        String metadata = ProjectCatalog.git(remote, "show", "badges:demo-metadata.json");
        env.put("GITHUB_RUN_NUMBER", "41");
        ProjectCatalog.write(workspace.resolve("results/result-demo/status.properties"),
                "project=demo\nsourceSha=" + source + "\nrunNumber=41\nrunAttempt=1\nstatus=failing");
        runPublisher(workspace, env);
        assertEquals(metadata, ProjectCatalog.git(remote, "show", "badges:demo-metadata.json"));
    }

    static void configure(Path repo) throws Exception {
        ProjectCatalog.git(repo, "config", "user.name", "Catalog test");
        ProjectCatalog.git(repo, "config", "user.email", "test@example.invalid");
        ProjectCatalog.git(repo, "config", "commit.gpgsign", "false");
    }
    static String quote(Path path) { return "'" + path.toString().replace("'", "'\\''") + "'"; }
    void runPublisher(Path workspace, Map<String, String> env) throws Exception {
        Path log = Files.createTempFile(root, "publisher-", ".log");
        var builder = new ProcessBuilder("bash", ".github/scripts/publish-catalog.sh").directory(workspace.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().putAll(env);
        Process process = builder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Publisher timed out: " + Files.readString(log)); }
        assertEquals(0, process.exitValue(), Files.readString(log));
    }
}
