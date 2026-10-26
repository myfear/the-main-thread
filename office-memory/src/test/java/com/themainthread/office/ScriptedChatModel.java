package com.themainthread.office;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

@Alternative
@Priority(1)
@ApplicationScoped
public class ScriptedChatModel implements ChatModel {
    private final Deque<Function<ChatRequest, AiMessage>> replies = new ArrayDeque<>();

    public synchronized void enqueue(Function<ChatRequest, AiMessage> reply) {
        replies.add(reply);
    }

    public synchronized void clear() {
        replies.clear();
    }

    @Override
    public synchronized ChatResponse doChat(ChatRequest request) {
        if (replies.isEmpty()) {
            throw new IllegalStateException("Unexpected model invocation: tests must script every response.");
        }
        return ChatResponse.builder().aiMessage(replies.removeFirst().apply(request)).build();
    }
}
