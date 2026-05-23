package dev.forgeassist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ModelRouterTest {

    @Inject
    ModelRouter router;

    @InjectMock
    PromptClassifier classifier;

    @InjectMock
    @ModelName("fast")
    ChatModel fastModel;

    @InjectMock
    ChatModel powerModel;

    @BeforeEach
    void stubModels() {
        when(fastModel.chat(any(ChatRequest.class))).thenReturn(response("fast-lane"));
        when(powerModel.chat(any(ChatRequest.class))).thenReturn(response("power-lane"));
        clearInvocations(fastModel, powerModel);
    }

    @Test
    void simplePromptUsesFastLane() {
        String prompt = "What does the --no-cache flag do in forge build?";
        when(classifier.classify(prompt)).thenReturn(Complexity.SIMPLE);
        clearInvocations(classifier);

        String answer = router.route(prompt);

        assertEquals("fast-lane", answer);
        verify(classifier).classify(prompt);
        verify(fastModel).chat(any(ChatRequest.class));
        verifyNoInteractions(powerModel);
    }

    @Test
    void complexPromptUsesPowerLane() {
        String prompt = "Why does my pipeline OOM only on cached arm64 builds?";
        when(classifier.classify(prompt)).thenReturn(Complexity.COMPLEX);
        clearInvocations(classifier);

        String answer = router.route(prompt);

        assertEquals("power-lane", answer);
        verify(classifier).classify(prompt);
        verify(powerModel).chat(any(ChatRequest.class));
        verifyNoInteractions(fastModel);
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}