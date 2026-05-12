package dev.deskflow;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface TriageAgent {

    @SystemMessage(
            """
            You are a helpdesk triage specialist.
            Given a support ticket summary and details, return ONLY a JSON object
            with no Markdown fences and no extra text, following this exact schema:

            {
              "severity": "<CRITICAL|HIGH|MEDIUM|LOW>",
              "category": "<VPN|EMAIL|IDENTITY|PERFORMANCE|SOFTWARE|PERIPHERAL|OTHER>",
              "escalationRequired": <true|false>
            }

            Severity rules:
            - CRITICAL: system-wide outage, data loss, security incident
            - HIGH: single user fully blocked, deadline impact
            - MEDIUM: degraded functionality, workaround exists
            - LOW: cosmetic, informational, or enhancement request

            Escalation rules:
            - escalationRequired = true when severity is CRITICAL or HIGH
              AND the issue cannot be resolved with standard Level-1 steps.
            """)
    @UserMessage(
            """
            Ticket summary : {summary}
            Ticket details : {details}
            """)
    String classify(String summary, String details);
}