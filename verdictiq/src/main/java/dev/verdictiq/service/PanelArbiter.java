package dev.verdictiq.service;

import org.jboss.logging.Logger;

import dev.verdictiq.ai.PanelAiInvoker;
import dev.verdictiq.model.DisagreementEvent;
import dev.verdictiq.model.ModelVerdict;
import dev.verdictiq.model.PanelVerdict;
import dev.verdictiq.model.Sentiment;
import io.quarkus.signals.Receives;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PanelArbiter {

    private static final Logger LOG = Logger.getLogger(PanelArbiter.class);

    private final PanelAiInvoker panelAiInvoker;
    private final VerdictStore store;

    public PanelArbiter(PanelAiInvoker panelAiInvoker, VerdictStore store) {
        this.panelAiInvoker = panelAiInvoker;
        this.store = store;
    }

    void onDisagreement(@Receives DisagreementEvent event) {
        PanelVerdict current = store.find(event.verdictId()).orElse(PanelVerdict.pending(event.verdictId(), event.text()));

        try {
            ModelVerdict finalVerdict = panelAiInvoker.adjudicate(
                    event.text(),
                    labelName(event.graniteLabel()),
                    safeReason(event.graniteReason()),
                    labelName(event.mistralLabel()),
                    safeReason(event.mistralReason()));

            boolean abstained = finalVerdict.label() == Sentiment.UNCERTAIN;

            store.put(current.adjudicated(finalVerdict.label(), finalVerdict.reason(), abstained));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Judge failed for verdict %s", event.verdictId());
            store.put(current.failed("Judge failed: " + safeMessage(e)));
        }
    }

    private String labelName(Sentiment label) {
        return label != null ? label.name() : Sentiment.UNCERTAIN.name();
    }

    private String safeReason(String reason) {
        return reason != null && !reason.isBlank() ? reason : "Model returned no reason.";
    }

    private String safeMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
