package com.themainthread.shapeshift;

import java.util.List;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

/** Conservative syntax checks shared with the browser before automatic inference. */
@ApplicationScoped
public class InputReadiness {
    public record Rule(String pattern, String exception, String reason) {
        boolean matches(String text) {
            return find(pattern, text) && (exception == null || !find(exception, text));
        }

        private boolean find(String expression, String text) {
            return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("[,;:]\\s*$", null, "Finish the current item or detail."),
            new Rule("\\b(?:to|with|at|for|between|among|by|and|or|of|on|in|a|an|the|until|into|about)\\s*[.!?…]*$",
                    "^(?:what|who|where|when|why|how)\\b.*\\?\\s*$", "Add the detail after the last word."),
            new Rule(
                    "^(?:please\\s+)?(?:remind(?:\\s+me)?(?:\\s+to)?|reminder|remember(?:\\s+to)?|don't forget(?:\\s+to)?|buy|shopping list|todo|checklist|split|divide|share|(?:set|start)(?:\\s+a)?(?:\\s+(?:timer|countdown))?|timer|focus(?:\\s+timer)?|countdown)\\s*[.!?…]*$",
                    null, "Add the task, items, amount, or duration."),
            new Rule("\\bat\\s+\\d{1,2}(?::\\d?)?\\s*$", null, "Finish the time, such as 8pm or at 20:00."),
            new Rule("^(?:please\\s+)?(?:split|divide)\\b|^share\\s*[$€£]?\\s*\\d",
                    "(?:split|divide|share)\\s*[$€£]?\\s*\\d+(?:\\.\\d{1,2})?(?:\\s*(?:euros?|dollars?|pounds?|EUR|USD|GBP))?\\s*(?:between|among|by|/)\\s*[1-9]\\d*(?![\\w.])",
                    "Add an amount and a whole-number people count."));

    public List<Rule> rules() {
        return RULES;
    }

    public String waitingReason(String text) {
        String trimmed = text.trim();
        if (trimmed.length() < 2) {
            return "Keep typing.";
        }
        for (Rule rule : RULES) {
            if (rule.matches(trimmed)) {
                return rule.reason();
            }
        }
        return null;
    }
}
