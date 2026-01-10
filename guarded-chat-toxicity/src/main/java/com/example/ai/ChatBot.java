package com.example.ai;

import com.example.guardrails.ToxicityGuardrail;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ChatBot {

    @SystemMessage("You are a helpful assistant. Keep responses short and professional.")
    @InputGuardrails(ToxicityGuardrail.class)
    String chat(@UserMessage String message);
}