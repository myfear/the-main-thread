package com.example.service;

import com.example.guardrails.MedicalAdviceGuardrail;
import com.example.guardrails.PromptInjectionGuardrail;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface ChatBot {
    @SystemMessage("You are a helpful AI assistant.")
    @InputGuardrails({PromptInjectionGuardrail.class, MedicalAdviceGuardrail.class})
    String chat(@UserMessage String userMessage);
}