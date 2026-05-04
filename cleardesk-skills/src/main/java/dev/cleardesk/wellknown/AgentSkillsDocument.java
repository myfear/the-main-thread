package dev.cleardesk.wellknown;

import java.util.List;

import dev.cleardesk.catalog.SkillSummary;

/**
 * JSON body for {@code GET /.well-known/agent-skills}.
 */
public record AgentSkillsDocument(String version, List<SkillSummary> skills) {
}
