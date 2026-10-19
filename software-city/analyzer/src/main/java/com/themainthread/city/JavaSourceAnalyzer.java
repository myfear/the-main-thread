package com.themainthread.city;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

final class JavaSourceAnalyzer {
    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25).setTabSize(1));

    record ParsedType(String id, String module, String packageName, String qualifiedName,
            String typeName, String kind, CityDocument.Source source, CityDocument.Metrics metrics,
            String fingerprint) {
        CityDocument.Type snapshot() {
            return new CityDocument.Type(id, module, packageName, qualifiedName, typeName,
                    new CityDocument.Sides<>(null, kind), new CityDocument.Sides<>(null, source),
                    new CityDocument.Sides<>(null, metrics), new CityDocument.Change("unchanged", false));
        }
    }

    record Result(List<ParsedType> types, List<CityDocument.Diagnostic> diagnostics) {
    }

    Result analyze(String text, String path, String module, String revision) {
        var parsed = parser.parse(text);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            var diagnostics = parsed.getProblems().stream().map(problem ->
                new CityDocument.Diagnostic("error", "PARSE_ERROR", revision, path,
                        problem.getLocation().map(location -> location.getBegin().getRange()
                                .map(range -> range.begin.line).orElse(null)).orElse(null),
                        problem.getMessage(), List.of())).toList();
            return new Result(List.of(), diagnostics);
        }
        var unit = parsed.getResult().orElseThrow();
        String packageName = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        List<ParsedType> types = new ArrayList<>();
        List<CityDocument.Diagnostic> diagnostics = new ArrayList<>();
        for (TypeDeclaration<?> type : unit.getTypes()) {
            if (type instanceof ClassOrInterfaceDeclaration declaration && declaration.isCompact()) {
                diagnostics.add(new CityDocument.Diagnostic("info", "COMPACT_SOURCE_SKIPPED",
                        revision, path, null, "Compact source has no explicitly named top-level type.", List.of()));
                continue;
            }
            var range = type.getRange().orElseThrow();
            int methods = 0;
            int constructors = 0;
            int fields = type instanceof RecordDeclaration record ? record.getParameters().size() : 0;
            for (var member : type.getMembers()) {
                if (member instanceof MethodDeclaration || member instanceof AnnotationMemberDeclaration) {
                    methods++;
                } else if (member instanceof ConstructorDeclaration || member instanceof CompactConstructorDeclaration) {
                    constructors++;
                } else if (member instanceof FieldDeclaration field) {
                    fields += field.getVariables().size();
                }
            }
            String name = type.getNameAsString();
            String qualifiedName = packageName.isEmpty() ? name : packageName + "." + name;
            String declarationText = text.substring(offset(text, range.begin.line, range.begin.column),
                    offset(text, range.end.line, range.end.column) + 1);
            types.add(new ParsedType(module + "::" + qualifiedName, module, packageName,
                    qualifiedName, name, kind(type),
                    new CityDocument.Source(path, range.begin.line, range.end.line),
                    new CityDocument.Metrics(methods, constructors, fields, range.end.line - range.begin.line + 1),
                    fingerprint(declarationText)));
        }
        return new Result(List.copyOf(types), List.copyOf(diagnostics));
    }

    private static String kind(TypeDeclaration<?> type) {
        if (type instanceof AnnotationDeclaration) {
            return "annotation";
        }
        if (type instanceof RecordDeclaration) {
            return "record";
        }
        if (type instanceof EnumDeclaration) {
            return "enum";
        }
        return ((ClassOrInterfaceDeclaration) type).isInterface() ? "interface" : "class";
    }

    private static int offset(String text, int line, int column) {
        int offset = 0;
        int currentLine = 1;
        while (currentLine < line && offset < text.length()) {
            char c = text.charAt(offset++);
            if (c == '\r') {
                if (offset < text.length() && text.charAt(offset) == '\n') {
                    offset++;
                }
                currentLine++;
            } else if (c == '\n') {
                currentLine++;
            }
        }
        return offset + column - 1;
    }

    private static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
