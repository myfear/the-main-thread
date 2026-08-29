package com.themainthread.planner.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.themainthread.planner.contract.BoardDto;
import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.ScheduleBookingCommand;
import com.themainthread.planner.contract.ScheduleProblem;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public final class BoardClient {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BoardClient(String baseUrl) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public BoardDto fetchBoard(Instant from, Instant to) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/board?from=" + from + "&to=" + to))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Board request failed with status " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), BoardDto.class);
    }

    public CompletableFuture<ScheduleResult> proposeSchedule(String bookingId, ScheduleBookingCommand command) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(baseUri.resolve("/api/bookings/" + bookingId + "/schedule"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(command)))
                    .build();
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::toScheduleResult);
    }

    private ScheduleResult toScheduleResult(HttpResponse<String> response) {
        try {
            BookingDto booking = null;
            ScheduleProblem problem = null;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                booking = objectMapper.readValue(response.body(), BookingDto.class);
            } else {
                problem = objectMapper.readValue(response.body(), ScheduleProblem.class);
            }
            return new ScheduleResult(response.statusCode(), booking, problem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record ScheduleResult(int statusCode, BookingDto booking, ScheduleProblem problem) {
    }
}
