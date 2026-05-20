package dev.themainthread.meridian.service;

public record Article(
        String id,
        String title,
        String htmlContent,
        String markdownContent) {
}
