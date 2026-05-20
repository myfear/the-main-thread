package dev.themainthread.meridian.mcp;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import dev.themainthread.meridian.service.Article;
import dev.themainthread.meridian.service.ArticleService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.stream.Collectors;

@ApplicationScoped
public class MeridianMcpTools {

    private static final FlexmarkHtmlConverter HTML_TO_MARKDOWN = FlexmarkHtmlConverter.builder().build();

    @Inject
    ArticleService articleService;

    @Tool(description = "Search public Meridian articles by keyword or phrase.")
    public String searchArticles(@ToolArg(description = "Search keyword or phrase") String query) {
        return articleService.search(query, 0, 10).items().stream()
                .map(article -> article.id() + ": " + article.title())
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Read one Meridian article as Markdown.")
    public String getArticle(@ToolArg(description = "Article ID") String id) {
        return articleService
                .findById(id)
                .map(MeridianMcpTools::markdownOrConverted)
                .orElse("No article found for ID `" + id + "`.");
    }

    private static String markdownOrConverted(Article article) {
        String markdown = article.markdownContent();
        if (markdown != null && !markdown.isBlank()) {
            return markdown;
        }
        return HTML_TO_MARKDOWN.convert(article.htmlContent());
    }
}
