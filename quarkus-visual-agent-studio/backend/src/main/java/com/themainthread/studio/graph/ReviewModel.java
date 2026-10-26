package com.themainthread.studio.graph;

import java.util.List;

public interface ReviewModel {
    ReviewResult review(PullRequest request, GraphCompiler.AgentConfiguration configuration, FileReader files);

    interface FileReader {
        List<ChangedFile> read();
    }

    enum Severity {
        INFO, LOW, MEDIUM, HIGH, CRITICAL
    }

    record ChangedFile(String path, String diff) {
    }

    record PullRequest(int number, String title, List<ChangedFile> files) {
    }

    record Finding(String file, int line, Severity severity, String category, String message) {
    }

    record ReviewResult(String summary, List<Finding> findings) {
    }
}
