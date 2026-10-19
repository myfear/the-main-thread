package com.themainthread.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSnapshotAnalyzerTest {
    @TempDir
    Path temp;
    private final GitSnapshotAnalyzer analyzer = new GitSnapshotAnalyzer();
    private final CityJson json = new CityJson(new ObjectMapper());

    @Test
    void nonexistentAndNonGitDirectoriesAreOperationalFailures() throws Exception {
        assertThrows(IOException.class, () -> analyzer.analyze(temp.resolve("missing"), "HEAD",
                temp.resolve("out.json"), List.of(), List.of(), true));
        Path noGit = Files.createDirectories(temp.resolve("not-a-repository"));
        assertThrows(IOException.class, () -> analyzer.analyze(noGit, "HEAD",
                temp.resolve("out.json"), List.of(), List.of(), true));
    }

    @Test
    void analyzesCommittedModuleMarkersAndSortsDistinctModuleIds() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            repo.copyFixture();
            String commit = repo.commit();
            var document = analyze(repo, commit, true);
            assertEquals(9, document.types().size());
            assertFalse(document.summary().incomplete());
            assertEquals(List.of(".::Root", "api::example.Shared", "app::example.Action",
                    "app::example.Everything", "app::example.Helper", "app::example.Label",
                    "app::example.Point", "app::example.Shared", "app::example.State"),
                    document.types().stream().map(CityDocument.Type::id).toList());
            assertEquals(commit, document.revisions().head().commit());
            // Working-tree module markers and source are never consulted.
            Files.delete(repo.root.resolve("api/build.gradle.kts"));
            repo.write("app/src/main/java/example/Everything.java", "broken working tree");
            assertArrayEquals(json.serialize(document), json.serialize(analyze(repo, commit, true)));
        }
    }

    @Test
    void ignoresWorkingTreeAndLeavesHeadIndexAndDirtyFilesByteIdentical() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            String path = "src/main/java/App.java";
            repo.write(path, "class App { void committed() {} }");
            String commit = repo.commit();
            repo.write(path, "class Staged {}");
            repo.git.add().addFilepattern(path).call();
            repo.write(path, "class Staged {} // unstaged");
            repo.write("untracked.txt", "leave me alone");
            repo.write("mvnw", "#!/bin/sh\nexit 99\n");
            byte[] head = Files.readAllBytes(repo.root.resolve(".git/HEAD"));
            byte[] index = Files.readAllBytes(repo.root.resolve(".git/index"));
            byte[] branch = Files.readAllBytes(repo.root.resolve(".git/refs/heads/main"));
            byte[] dirty = Files.readAllBytes(repo.root.resolve(path));
            var document = analyze(repo, commit, true);
            json.write(document, temp.resolve("city.json"));
            assertEquals(".::App", document.types().getFirst().id());
            assertEquals(1, document.types().getFirst().metrics().head().methodCount());
            assertArrayEquals(head, Files.readAllBytes(repo.root.resolve(".git/HEAD")));
            assertArrayEquals(index, Files.readAllBytes(repo.root.resolve(".git/index")));
            assertArrayEquals(branch, Files.readAllBytes(repo.root.resolve(".git/refs/heads/main")));
            assertArrayEquals(dirty, Files.readAllBytes(repo.root.resolve(path)));
            assertEquals("leave me alone", Files.readString(repo.root.resolve("untracked.txt")));
        }
    }

    @Test
    void omitsWholeBrokenFilesAndEveryDuplicateIdentityWithDiagnostics() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            repo.write("src/main/java/a/A.java", "package actual; class Same {}");
            repo.write("src/main/java/b/B.java", "package actual; class Same {}");
            repo.write("src/main/java/Bad.java", "class Recovered {} class Bad { void x( }");
            repo.write("src/main/java/Good.java", "class Good {}");
            repo.commit();
            var document = analyze(repo, "HEAD", false);
            assertEquals(List.of(".::Good"), document.types().stream().map(CityDocument.Type::id).toList());
            assertTrue(document.summary().incomplete());
            assertEquals(2, document.diagnostics().stream().filter(d -> d.code().equals("DUPLICATE_TYPE_ID")).count());
            assertTrue(document.diagnostics().stream().anyMatch(d -> d.code().equals("PARSE_ERROR")));
            assertTrue(document.diagnostics().stream().filter(d -> d.code().equals("DUPLICATE_TYPE_ID"))
                    .allMatch(d -> d.affectedTypeIds().equals(List.of(".::actual.Same"))));
            json.validate(document);
            assertThrows(IOException.class, () -> analyze(repo, "HEAD", true));
        }
    }

    @Test
    void invalidUtf8IsDiagnosedAndSymlinkSourcesAreNotFollowed() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            repo.write("src/main/java/Good.java", "class Good {}");
            Files.write(repo.root.resolve("src/main/java/Invalid.java"), new byte[] {(byte) 0xc3, 0x28});
            Path external = temp.resolve("External.java");
            Files.writeString(external, "class MustNotAppear {}");
            Files.createSymbolicLink(repo.root.resolve("src/main/java/Link.java"), external);
            repo.commit();
            var document = analyze(repo, "HEAD", false);
            assertEquals(1, document.types().size());
            assertTrue(document.diagnostics().stream().anyMatch(d -> d.code().equals("INVALID_UTF8")));
            assertTrue(document.diagnostics().stream().anyMatch(d -> d.code().equals("UNSUPPORTED_GIT_ENTRY")));
            assertEquals("class MustNotAppear {}", Files.readString(external));
        }
    }

    @Test
    void moduleAndPackageInfoAreNotBuildingsAndDefaultPackageFallsBackToRootModule() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            repo.write("src/main/java/module-info.java", "module sample {}");
            repo.write("src/main/java/package-info.java", "package sample;");
            repo.write("src/main/java/Empty.java", "");
            repo.write("src/main/java/Root.java", "class Root {}");
            repo.commit();
            var document = analyze(repo, "HEAD", true);
            assertEquals(1, document.types().size());
            assertEquals("", document.types().getFirst().packageName());
            assertEquals(".::Root", document.types().getFirst().id());
        }
    }

    @Test
    void detectsAmbiguousRefsAndAcceptsExplicitRefsTagsAndParentExpressions() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            repo.write("src/main/java/A.java", "class A {}");
            String first = repo.commit();
            repo.write("src/main/java/A.java", "class A { void added() {} }");
            repo.commit();
            repo.git.tag().setName("release").setAnnotated(false).call();
            repo.git.branchCreate().setName("release").call();
            assertThrows(IOException.class, () -> analyze(repo, "release", true));
            assertNotNull(analyze(repo, "refs/tags/release", true));
            assertEquals(first, analyze(repo, "HEAD~1", true).revisions().head().commit());
            assertThrows(IOException.class, () -> analyze(repo, "missing-ref", true));
            assertThrows(IOException.class, () -> analyze(repo, "HEAD:", true));
        }
    }

    @Test
    void exportsAreDeterministicAndContainNoAbsolutePathsOrTimingData() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            repo.copyFixture();
            repo.commit();
            byte[] first = json.serialize(analyze(repo, "HEAD", true));
            byte[] second = json.serialize(analyze(repo, "HEAD", true));
            assertArrayEquals(first, second);
            String text = new String(first, java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(text.contains(temp.toString()));
            assertFalse(text.contains("timestamp"));
            assertFalse(text.contains("\r"));
            assertTrue(text.endsWith("\n"));
        }
    }

    @Test
    void rejectsWorktreeGitAndSymlinkExportDestinationsBeforeWriting() throws Exception {
        try (var repo = new TestRepository(temp.resolve("repository"))) {
            repo.write("src/main/java/A.java", "class A {}");
            repo.commit();
            for (Path output : List.of(repo.root.resolve("city.json"), repo.root.resolve(".git/objects/city.json"))) {
                assertThrows(IOException.class, () -> analyzer.analyze(repo.root, "HEAD", output,
                        List.of(), List.of(), true));
                assertFalse(Files.exists(output));
            }
            Path alias = temp.resolve("alias");
            Files.createSymbolicLink(alias, repo.root);
            assertThrows(IOException.class, () -> analyzer.analyze(repo.root, "HEAD", alias.resolve("city.json"),
                    List.of(), List.of(), true));
        }
    }

    private CityDocument analyze(TestRepository repository, String ref, boolean strict) throws IOException {
        return analyzer.analyze(repository.root, ref, temp.resolve("export.json"), List.of(), List.of(), strict);
    }
}
