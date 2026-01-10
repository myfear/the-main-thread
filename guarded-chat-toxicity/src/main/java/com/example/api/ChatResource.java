package com.example.api;

import com.example.ai.ChatBot;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    ChatBot chatBot;

    @POST
    public ChatResponse chat(ChatRequest request) {
        String reply = chatBot.chat(request.message());
        return new ChatResponse("ALLOWED", reply);
    }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String status, String reply) {
    }
}