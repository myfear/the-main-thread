package dev.cleardesk.supervisor;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;

/**
 * Extends the default skills catalogue with explicit routing rules for the three delegate tools.
 */
@ApplicationScoped
public class ClearDeskSkillsSystemMessageProvider implements SystemMessageProvider {

    @Override
    public Optional<String> getSystemMessage(Object memoryId) {
        Instance<SkillsToolProvider> skillsToolProvider = CDI.current().select(SkillsToolProvider.class);
        if (skillsToolProvider.isResolvable()) {
            String available = skillsToolProvider.get().getSkills().formatAvailableSkills();
            String body = """
                    You are ClearDesk, an internal supervisor for three specialists.

                    Routing rules:
                    - When the user request matches one of the skills below, call `activate_skill` with that skill name first,
                      read the skill body, then call exactly one of `routeToSupport`, `routeToFinance`, or `routeToDevOps`
                      as instructed by the skill.
                    - If none of the skills fit, still pick the single best specialist using the skill descriptions as a contract,
                      then call the matching route tool (you may activate the closest skill if helpful).

                    Available skills:
                    %s
                    """
                    .formatted(available);
            return Optional.of(body);
        }
        return Optional.empty();
    }
}
