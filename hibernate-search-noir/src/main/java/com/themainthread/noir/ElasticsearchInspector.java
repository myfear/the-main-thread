package com.themainthread.noir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.ResponseException;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

@ApplicationScoped
public class ElasticsearchInspector {

    private final SearchMapping searchMapping;
    private final ObjectMapper mapper;

    public ElasticsearchInspector(SearchMapping searchMapping, ObjectMapper mapper) {
        this.searchMapping = searchMapping;
        this.mapper = mapper;
    }

    public JsonNode indexedAuthor(long id) {
        return get("/author-read/_doc/" + id);
    }

    public List<String> analyze(String analyzer, String text) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("analyzer", analyzer);
        body.put("text", text);
        JsonNode response = post("/author-read/_analyze", body);
        List<String> tokens = new ArrayList<>();
        for (JsonNode token : response.path("tokens")) {
            tokens.add(token.path("token").asText());
        }
        return tokens;
    }

    private JsonNode get(String endpoint) {
        Request request = new Request("GET", endpoint);
        return perform(request);
    }

    private JsonNode post(String endpoint, Object jsonBody) {
        Request request = new Request("POST", endpoint);
        try {
            request.setJsonEntity(mapper.writeValueAsString(jsonBody));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return perform(request);
    }

    private JsonNode perform(Request request) {
        try {
            Response response = client().performRequest(request);
            return mapper.readTree(response.getEntity().getContent());
        } catch (ResponseException e) {
            int status = e.getResponse().getStatusCode();
            if (status == 404) {
                throw new NotFoundException("Indexed document not found");
            }
            throw new WebApplicationException("Elasticsearch request failed: " + status, Status.BAD_GATEWAY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Rest5Client client() {
        return searchMapping.backend()
                .unwrap(ElasticsearchBackend.class)
                .client(Rest5Client.class);
    }
}
