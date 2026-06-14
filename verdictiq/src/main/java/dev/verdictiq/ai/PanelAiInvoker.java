package dev.verdictiq.ai;

import dev.verdictiq.model.ModelVerdict;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;

@ApplicationScoped
public class PanelAiInvoker {

    private final GranitePanelist granitePanelist;
    private final MistralPanelist mistralPanelist;
    private final JudgeAiService judgeAiService;

    public PanelAiInvoker(
            GranitePanelist granitePanelist,
            MistralPanelist mistralPanelist,
            JudgeAiService judgeAiService) {
        this.granitePanelist = granitePanelist;
        this.mistralPanelist = mistralPanelist;
        this.judgeAiService = judgeAiService;
    }

    @ActivateRequestContext
    public ModelVerdict classifyWithGranite(String text) {
        return granitePanelist.classify(text).normalized();
    }

    @ActivateRequestContext
    public ModelVerdict classifyWithMistral(String text) {
        return mistralPanelist.classify(text).normalized();
    }

    @ActivateRequestContext
    public ModelVerdict adjudicate(
            String text,
            String labelA,
            String reasonA,
            String labelB,
            String reasonB) {
        return judgeAiService.adjudicate(text, labelA, reasonA, labelB, reasonB).normalized();
    }
}
