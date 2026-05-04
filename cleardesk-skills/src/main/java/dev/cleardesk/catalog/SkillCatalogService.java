package dev.cleardesk.catalog;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.FileSystemSkill;

/**
 * Single source of truth for skill metadata used by {@code /.well-known/agent-skills} (same paths as
 * {@code quarkus.langchain4j.skills.directories}).
 */
@ApplicationScoped
public class SkillCatalogService {

    private final List<FileSystemSkill> skills;

    public SkillCatalogService() {
        this.skills = List.copyOf(ClassPathSkillLoader.loadSkills("skills"));
    }

    public List<FileSystemSkill> fileSystemSkills() {
        return skills;
    }

    public List<SkillSummary> summaries() {
        return skills.stream().map(s -> new SkillSummary(s.name(), s.description())).toList();
    }
}
