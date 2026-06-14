package dev.verdictiq.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.UUID;

import dev.verdictiq.ai.PanelAiInvoker;
import dev.verdictiq.model.DisagreementEvent;
import dev.verdictiq.model.ModelVerdict;
import dev.verdictiq.model.PanelVerdict;
import dev.verdictiq.qualifier.PanelWork;
import io.quarkus.signals.Signal;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VerdictPanel {

    private final PanelAiInvoker panelAiInvoker;
    private final VerdictStore store;
    private final Signal<DisagreementEvent> disagreementSignal;
    private final ExecutorService panelExecutor;

    public VerdictPanel(
            PanelAiInvoker panelAiInvoker,
            VerdictStore store,
            Signal<DisagreementEvent> disagreementSignal,
            @PanelWork ExecutorService panelExecutor) {
        this.panelAiInvoker = panelAiInvoker;
        this.store = store;
        this.disagreementSignal = disagreementSignal;
        this.panelExecutor = panelExecutor;
    }

    public String submit(String text) {
        String id = UUID.randomUUID().toString();
        store.put(PanelVerdict.pending(id, text));
        panelExecutor.submit(() -> runPanel(id, text));
        return id;
    }

    private void runPanel(String id, String text) {
        Future<ModelVerdict> graniteFuture = panelExecutor.submit(() -> panelAiInvoker.classifyWithGranite(text));
        Future<ModelVerdict> mistralFuture = panelExecutor.submit(() -> panelAiInvoker.classifyWithMistral(text));

        try {
            ModelVerdict granite = graniteFuture.get();
            ModelVerdict mistral = mistralFuture.get();

            if (granite.label() == mistral.label()) {
                store.put(PanelVerdict.consensus(id, text, granite, mistral));
                return;
            }

            store.put(PanelVerdict.disagreement(id, text, granite, mistral));
            disagreementSignal.publish(new DisagreementEvent(
                    id,
                    text,
                    granite.label(),
                    granite.reason(),
                    mistral.label(),
                    mistral.reason()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(id, text, e);
        } catch (ExecutionException | RuntimeException e) {
            markFailed(id, text, e);
        }
    }

    private void markFailed(String id, String text, Exception failure) {
        String message = failure.getCause() != null && failure.getCause().getMessage() != null
                ? failure.getCause().getMessage()
                : failure.getMessage();

        PanelVerdict current = store.find(id).orElse(PanelVerdict.pending(id, text));
        store.put(current.failed(message == null ? "Panel processing failed." : message));
    }
}
