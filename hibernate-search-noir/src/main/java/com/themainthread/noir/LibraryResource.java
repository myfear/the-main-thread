package com.themainthread.noir;

import java.util.List;

import org.hibernate.search.mapper.orm.session.SearchSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.themainthread.noir.model.Author;
import com.themainthread.noir.model.Book;

import io.smallrye.common.annotation.Blocking;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/library")
@Produces(MediaType.APPLICATION_JSON)
public class LibraryResource {

    private final SearchSession searchSession;
    private final ElasticsearchInspector inspector;

    public LibraryResource(SearchSession searchSession, ElasticsearchInspector inspector) {
        this.searchSession = searchSession;
        this.inspector = inspector;
    }

    public record CatalogEntry(
            Long bookId,
            String title,
            Long authorId,
            String authorFirstName,
            String authorLastName) {
    }

    public record AnalyzeResponse(String analyzer, String text, List<String> tokens) {
    }

    @GET
    @Path("/books")
    @Transactional
    public CatalogEntry bookFromDatabase(@QueryParam("title") String title) {
        if (title == null || title.isBlank()) {
            throw new NotFoundException("title is required");
        }
        Book book = Book.find("title", title).firstResult();
        if (book == null) {
            throw new NotFoundException("No catalog row for title " + title);
        }
        return new CatalogEntry(book.id, book.title, book.author.id, book.author.firstName, book.author.lastName);
    }

    @GET
    @Path("/search")
    @Transactional
    public List<Author> search(@QueryParam("q") String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return searchSession.search(Author.class)
                .where(f -> f.simpleQueryString()
                        .fields("firstName", "lastName", "books.title")
                        .matching(q))
                .sort(f -> f.field("lastName_sort").then().field("firstName_sort"))
                .fetchHits(20);
    }

    @GET
    @Path("/index/author/{id}")
    @Blocking
    public JsonNode indexedAuthor(@PathParam("id") long id) {
        return inspector.indexedAuthor(id);
    }

    @GET
    @Path("/analyze")
    @Blocking
    public AnalyzeResponse analyze(@QueryParam("analyzer") String analyzer, @QueryParam("text") String text) {
        if (analyzer == null || analyzer.isBlank() || text == null) {
            throw new NotFoundException("analyzer and text are required");
        }
        return new AnalyzeResponse(analyzer, text, inspector.analyze(analyzer, text));
    }
}
