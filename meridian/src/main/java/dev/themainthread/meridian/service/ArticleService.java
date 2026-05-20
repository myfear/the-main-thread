package dev.themainthread.meridian.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class ArticleService {

    private final Map<String, Article> articles = new ConcurrentHashMap<>();

    public ArticleService() {
        articles.put(
                "intro-to-meridian",
                new Article(
                        "intro-to-meridian",
                        "Introduction to Meridian",
                        "<h1>Introduction</h1><p>Welcome to Meridian.</p>",
                        "# Introduction\n\nWelcome to Meridian."));
    }

    public Optional<Article> findById(String id) {
        return Optional.ofNullable(articles.get(id));
    }

    public ArticleListResponse search(String query, int page, int size) {
        List<ArticleSummary> all = new ArrayList<>(
                articles.values().stream()
                        .map(a -> new ArticleSummary(a.id(), a.title()))
                        .sorted(Comparator.comparing(ArticleSummary::title))
                        .toList());
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(s -> s.title().toLowerCase(Locale.ROOT).contains(q)
                            || bodyContains(s.id(), q))
                    .collect(Collectors.toList());
        }
        int from = Math.max(0, page * size);
        int to = Math.min(all.size(), from + size);
        if (from >= all.size()) {
            return new ArticleListResponse(List.of());
        }
        return new ArticleListResponse(all.subList(from, to));
    }

    private boolean bodyContains(String id, String q) {
        return findById(id)
                .map(a -> (a.htmlContent() + " " + nullToEmpty(a.markdownContent())).toLowerCase(Locale.ROOT).contains(q))
                .orElse(false);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public Article create(String title, String content) {
        String id = slug(title) + "-" + UUID.randomUUID().toString().substring(0, 8);
        String safe = content == null ? "" : escapeHtml(content);
        String html = "<article><h1>" + escapeHtml(title) + "</h1><p>" + safe + "</p></article>";
        Article article = new Article(id, title, html, null);
        articles.put(id, article);
        return article;
    }

    private static String slug(String title) {
        if (title == null || title.isBlank()) {
            return "article";
        }
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
