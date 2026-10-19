package com.themainthread.city;

import java.util.List;

public record CityDocument(String schemaVersion, String mode, RepositoryInfo repository,
        Revisions revisions, List<Type> types, List<Diagnostic> diagnostics, Summary summary) {

    public record RepositoryInfo(String displayName) {
    }

    public record Revision(String ref, String commit) {
    }

    public record Revisions(Revision base, Revision head) {
    }

    public record Sides<T>(T base, T head) {
    }

    public record Source(String path, int startLine, int endLine) {
    }

    public record Metrics(int methodCount, int constructorCount, int fieldCount, int sourceLines) {
    }

    public record Change(String status, boolean fileChanged) {
    }

    public record Type(String id, String module, String packageName, String qualifiedName,
            String typeName, Sides<String> typeKind, Sides<Source> source,
            Sides<Metrics> metrics, Change change) {
    }

    public record Diagnostic(String severity, String code, String revision, String path,
            Integer line, String message, List<String> affectedTypeIds) {
    }

    public record Summary(int typeCountBase, int typeCountHead, int added, int removed,
            int modified, int unchanged, boolean incomplete) {
    }
}
