package com.example.resource;

import com.example.benchmark.GuardrailBenchmark;
import com.example.service.ChatBot;

import dev.langchain4j.guardrail.GuardrailException;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

   @Inject
   ChatBot chatService;

   @Inject
   GuardrailBenchmark benchmark;

   @POST
   @Path("/chat")
   public Response chat(ChatRequest request) {
      try {
         String response = chatService.chat(request.message());
         return Response.ok(new ChatResponse(response)).build();
      } catch (GuardrailException e) {
         Log.errorf(e, "Guardrail violation detected: %s", e.getMessage());
         return Response.status(Response.Status.BAD_REQUEST)
               .entity(new ErrorResponse("Request blocked by guardrail: " + e.getMessage()))
               .build();
      }
   }

   @GET
   @Path("/benchmark")
   public GuardrailBenchmark.BenchmarkResults runBenchmark() {
      GuardrailBenchmark.BenchmarkResults results = benchmark.runBenchmark();
      results.print();
      return results;
   }

   public record ChatRequest(String message) {
   }

   public record ChatResponse(String message) {
   }

   public record ErrorResponse(String error) {
   }
}