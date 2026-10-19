package com.themainthread.city;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GitSnapshotAnalyzer {
    public CityDocument analyze(Path repositoryPath, String ref, Path output, List<String> includes,
            List<String> excludes, boolean strict) throws IOException {
        SourceFilter filter = new SourceFilter(includes, excludes);
        if (!Files.isDirectory(repositoryPath)) {
            throw new IOException("Repository directory does not exist.");
        }
        var builder = new FileRepositoryBuilder().setMustExist(true)
                .findGitDir(repositoryPath.toRealPath().toFile());
        if (builder.getGitDir() == null) {
            throw new IOException("No existing Git repository found. Provide --repo with a local Git repository.");
        }
        try (Repository repository = builder.build()) {
            validateDestination(repository, output);
            ObjectId commit = resolve(repository, ref);
            TreeMap<String, ObjectId> sources = new TreeMap<>();
            Set<String> modules = new HashSet<>();
            List<CityDocument.Diagnostic> diagnostics = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repository);
                    TreeWalk tree = new TreeWalk(repository)) {
                tree.addTree(walk.parseCommit(commit).getTree());
                tree.setRecursive(true);
                while (tree.next()) {
                    String path = tree.getPathString();
                    String name = path.substring(path.lastIndexOf('/') + 1);
                    if (List.of("pom.xml", "build.gradle", "build.gradle.kts").contains(name)
                            && tree.getFileMode(0).getObjectType() == Constants.OBJ_BLOB
                            && !tree.getFileMode(0).equals(FileMode.SYMLINK)) {
                        modules.add(parent(path));
                    }
                    if (filter.accepts(path) && !List.of("package-info.java", "module-info.java").contains(name)) {
                        if (!tree.getFileMode(0).equals(FileMode.REGULAR_FILE)
                                && !tree.getFileMode(0).equals(FileMode.EXECUTABLE_FILE)) {
                            diagnostics.add(error("UNSUPPORTED_GIT_ENTRY", path,
                                    "Source entry is not a regular Git blob; it was not followed."));
                        } else {
                            sources.put(path, tree.getObjectId(0).copy());
                        }
                    }
                }
            }
            var parser = new JavaSourceAnalyzer();
            TreeMap<String, List<JavaSourceAnalyzer.ParsedType>> byId = new TreeMap<>();
            for (var entry : sources.entrySet()) {
                String path = entry.getKey();
                try {
                    byte[] bytes = repository.open(entry.getValue(), Constants.OBJ_BLOB).getBytes();
                    String source = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
                    var result = parser.analyze(source, path, module(path, modules), "head");
                    diagnostics.addAll(result.diagnostics());
                    for (var type : result.types()) {
                        byId.computeIfAbsent(type.id(), ignored -> new ArrayList<>()).add(type);
                    }
                } catch (CharacterCodingException e) {
                    diagnostics.add(error("INVALID_UTF8", path, "Source is not valid UTF-8."));
                } catch (IOException | org.eclipse.jgit.errors.LargeObjectException e) {
                    diagnostics.add(error("BLOB_READ_ERROR", path, "Committed source blob could not be read."));
                }
            }
            List<CityDocument.Type> types = new ArrayList<>();
            for (var entry : byId.entrySet()) {
                if (entry.getValue().size() == 1) {
                    types.add(entry.getValue().getFirst().snapshot());
                } else {
                    for (String path : new TreeSet<>(entry.getValue().stream().map(t -> t.source().path()).toList())) {
                        diagnostics.add(new CityDocument.Diagnostic("error", "DUPLICATE_TYPE_ID", "head",
                                path, null, "Multiple declarations share a stable type ID; all were omitted.",
                                List.of(entry.getKey())));
                    }
                }
            }
            diagnostics.sort(Comparator.comparing(CityDocument.Diagnostic::revision)
                    .thenComparing(CityDocument.Diagnostic::path)
                    .thenComparing(d -> d.line() == null ? 0 : d.line())
                    .thenComparing(CityDocument.Diagnostic::code).thenComparing(CityDocument.Diagnostic::message));
            boolean incomplete = diagnostics.stream().anyMatch(d -> d.severity().equals("error"));
            if (strict && incomplete) {
                throw new IOException("Strict analysis failed: " + diagnostics.stream()
                        .filter(d -> d.severity().equals("error")).count()
                        + " source diagnostics. Run without --strict to export structured diagnostics.");
            }
            Path displayRoot = repository.isBare() ? repository.getDirectory().toPath() : repository.getWorkTree().toPath();
            return new CityDocument("1", "snapshot",
                    new CityDocument.RepositoryInfo(displayRoot.getFileName().toString()),
                    new CityDocument.Revisions(null, new CityDocument.Revision(ref, commit.name())),
                    List.copyOf(types), List.copyOf(diagnostics),
                    new CityDocument.Summary(0, types.size(), 0, 0, 0, types.size(), incomplete));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (org.eclipse.jgit.errors.RepositoryNotFoundException e) {
            throw new IOException("No existing Git repository found. Provide --repo with a local Git repository.", e);
        }
    }

    private static ObjectId resolve(Repository repository, String ref) throws IOException {
        try {
            String stem = ref.split("[~^]", 2)[0];
            if (!stem.equals("HEAD") && !stem.startsWith("refs/")) {
                int matches = 0;
                for (String prefix : List.of("refs/", "refs/heads/", "refs/tags/", "refs/remotes/")) {
                    if (repository.exactRef(prefix + stem) != null) {
                        matches++;
                    }
                }
                if (matches > 1) {
                    throw new IOException("Ambiguous ref: use a fully qualified refs/heads/... or refs/tags/... name.");
                }
            }
            ObjectId resolved = repository.resolve(ref + "^{commit}");
            if (resolved == null) {
                throw new IOException("Ref is not a locally available commit. Check the ref or fetch it yourself; this tool never fetches.");
            }
            try (RevWalk walk = new RevWalk(repository)) {
                return walk.parseCommit(resolved).getId().copy();
            }
        } catch (org.eclipse.jgit.errors.AmbiguousObjectException e) {
            throw new IOException("Ambiguous object ID: provide a longer or full commit ID.", e);
        } catch (org.eclipse.jgit.errors.RevisionSyntaxException | org.eclipse.jgit.errors.IncorrectObjectTypeException e) {
            throw new IOException("Invalid revision: provide a ref resolving to a commit.", e);
        }
    }

    private static void validateDestination(Repository repository, Path output) throws IOException {
        Path destination = canonical(output);
        List<Path> protectedRoots = new ArrayList<>();
        protectedRoots.add(repository.getDirectory().toPath().toRealPath());
        // The common directory also protects linked worktrees' shared Git data.
        protectedRoots.add(repository.getCommonDirectory().toPath().toRealPath());
        if (!repository.isBare()) {
            protectedRoots.add(repository.getWorkTree().toPath().toRealPath());
        }
        if (protectedRoots.stream().anyMatch(destination::startsWith)) {
            throw new IOException("Export destination must be outside the analyzed worktree and Git directories.");
        }
    }

    private static Path canonical(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        if (Files.exists(path) || Files.isSymbolicLink(path)) {
            return path.toRealPath();
        }
        Path parent = path.getParent();
        return parent == null ? path : canonical(parent).resolve(path.getFileName());
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String module(String path, Set<String> modules) {
        String directory = parent(path);
        while (!directory.isEmpty()) {
            if (modules.contains(directory)) {
                return directory;
            }
            directory = parent(directory);
        }
        return ".";
    }

    private static CityDocument.Diagnostic error(String code, String path, String message) {
        return new CityDocument.Diagnostic("error", code, "head", path, null, message, List.of());
    }
}
