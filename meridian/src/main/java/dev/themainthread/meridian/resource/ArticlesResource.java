package dev.themainthread.meridian.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import dev.themainthread.meridian.service.Article;
import dev.themainthread.meridian.service.ArticleListResponse;
import dev.themainthread.meridian.service.ArticleService;
import dev.themainthread.meridian.service.CreateArticleRequest;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

@ApplicationScoped
@Path("/api/v1/articles")
public class ArticlesResource {

    private static final MediaType TEXT_MARKDOWN_TYPE = new MediaType("text", "markdown");

    private static final FlexmarkHtmlConverter HTML_TO_MARKDOWN = FlexmarkHtmlConverter.builder().build();

    @Inject
    ArticleService articleService;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search and list public articles", description = """
            Searches public knowledge articles by title and body text. When the q
            parameter is present, results are ordered by search relevance. Without q,
            results are ordered by publication date. This endpoint does not require
            authentication.
            """)
    @APIResponse(responseCode = "200", description = "Paginated article list")
    public ArticleListResponse listArticles(
            @QueryParam("q") String query,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return articleService.search(query, page, size);
    }

    @POST
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createArticle(CreateArticleRequest request) {
        Article created = articleService.create(request.title(), request.content());
        return Response.created(
                UriBuilder.fromPath("/api/v1/articles").path(created.id()).build())
                .entity(created)
                .build();
    }

    @GET
    @PermitAll
    @Path("/{id}/content")
    public Response getArticleContent(@PathParam("id") String id, @Context HttpHeaders headers) {
        Article article = articleService.findById(id).orElseThrow(NotFoundException::new);

        if (acceptsMarkdown(headers)) {
            String markdown = article.markdownContent();
            if (markdown == null || markdown.isBlank()) {
                markdown = HTML_TO_MARKDOWN.convert(article.htmlContent());
            }

            return Response.ok(markdown, TEXT_MARKDOWN_TYPE)
                    .header("Vary", HttpHeaders.ACCEPT)
                    .header("X-Markdown-Tokens", estimateTokens(markdown))
                    .header("Content-Signal", "search=yes, ai-input=yes, ai-train=no")
                    .build();
        }

        return Response.ok(article.htmlContent(), MediaType.TEXT_HTML_TYPE)
                .header("Vary", HttpHeaders.ACCEPT)
                .build();
    }

    private boolean acceptsMarkdown(HttpHeaders headers) {
        for (MediaType requested : headers.getAcceptableMediaTypes()) {
            if (requested.isWildcardType() || requested.isWildcardSubtype()) {
                return false;
            }
            if (requested.isCompatible(TEXT_MARKDOWN_TYPE)) {
                return true;
            }
            if (requested.isCompatible(MediaType.TEXT_HTML_TYPE)) {
                return false;
            }
        }
        return false;
    }

    private int estimateTokens(String value) {
        return Math.max(1, value.length() / 4);
    }
}
