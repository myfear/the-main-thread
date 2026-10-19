package com.themainthread.city;

import java.util.List;
import java.util.regex.Pattern;

final class SourceFilter {
    private static final List<String> OUTPUT_SEGMENTS = List.of("target", "build", ".gradle",
            ".git", "generated", "generated-sources", "generated-test-sources");
    private final List<Pattern> includes;
    private final List<Pattern> excludes;

    SourceFilter(List<String> includes, List<String> excludes) {
        this.includes = compile(includes.isEmpty() ? List.of("**/src/main/java/**/*.java") : includes);
        this.excludes = compile(excludes);
    }

    boolean accepts(String path) {
        if (!path.endsWith(".java")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (OUTPUT_SEGMENTS.contains(segment)) {
                return false;
            }
        }
        return includes.stream().anyMatch(p -> p.matcher(path).matches())
                && excludes.stream().noneMatch(p -> p.matcher(path).matches());
    }

    private static List<Pattern> compile(List<String> globs) {
        return globs.stream().map(SourceFilter::glob).toList();
    }

    private static Pattern glob(String glob) {
        if (glob.isBlank() || glob.startsWith("/") || glob.contains("\\") || glob.contains("..")
                || glob.contains("[") || glob.contains("{") || glob.contains("***")) {
            throw new IllegalArgumentException("Invalid glob: use relative / paths and only *, ? or ** wildcards: " + glob);
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    if ((i > 0 && glob.charAt(i - 1) != '/')
                            || (i + 2 < glob.length() && glob.charAt(i + 2) != '/')) {
                        throw new IllegalArgumentException("** must occupy an entire path segment: " + glob);
                    }
                    i++;
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                        regex.append("(?:.*/)?");
                        i++;
                    } else {
                        regex.append(".*");
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }
}
