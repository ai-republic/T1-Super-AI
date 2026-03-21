package com.airepublic.t1.skills;

import com.airepublic.t1.config.AgentConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * SkillManager handles skill loading and management.
 * Skills are prompt templates that can use any available tools.
 *
 * Skills are stored per-agent in: ~/.t1-super-ai/workspaces/<team>/<agent>/skills/
 */
@Slf4j
@Component
public class SkillManager {
    private final Map<String, Skill> loadedSkills = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentConfigService agentConfigService;

    public SkillManager(final AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    /**
     * Get the skills directory for a specific agent
     */
    private Path getSkillsDir(String agentName) {
        return agentConfigService.getAgentFolder(agentName).resolve("skills");
    }

    /**
     * Load all skills for a specific agent
     */
    public void loadAllSkills(String agentName) {
        try {
            Path skillsPath = getSkillsDir(agentName);
            if (!Files.exists(skillsPath)) {
                log.debug("Skills directory does not exist for agent {}: {}", agentName, skillsPath);
                return;
            }

            loadedSkills.clear();
            try (Stream<Path> paths = Files.walk(skillsPath)) {
                paths.filter(p -> p.toString().endsWith(".json"))
                        .forEach(this::loadSkillFromJson);
            }
            log.info("Loaded {} skills for agent: {}", loadedSkills.size(), agentName);
        } catch (Exception e) {
            log.error("Error loading skills for agent: {}", agentName, e);
        }
    }

    /**
     * Load a skill from a JSON file
     */
    public void loadSkillFromJson(Path skillPath) {
        try {
            Skill skill = objectMapper.readValue(skillPath.toFile(), Skill.class);
            loadedSkills.put(skill.getName(), skill);
            log.info("Loaded skill: {}", skill.getName());
        } catch (Exception e) {
            log.error("Error loading skill from {}", skillPath, e);
        }
    }

    /**
     * Create a new skill for a specific agent
     */
    public void createSkill(String agentName, String name, String description, String prompt, List<String> requiredTools) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setPrompt(prompt);
        skill.setRequiredTools(requiredTools);
        skill.setCreatedAt(new Date());

        try {
            Path skillsDir = getSkillsDir(agentName);
            Files.createDirectories(skillsDir);

            Path skillPath = skillsDir.resolve(name + ".json");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(skillPath.toFile(), skill);

            loadedSkills.put(name, skill);
            log.info("Created skill '{}' for agent: {}", name, agentName);
        } catch (Exception e) {
            log.error("Error creating skill '{}' for agent: {}", name, agentName, e);
        }
    }

    /**
     * Update an existing skill for a specific agent
     */
    public void updateSkill(String agentName, String name, Skill updatedSkill) {
        try {
            Path skillsDir = getSkillsDir(agentName);
            Path skillPath = skillsDir.resolve(name + ".json");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(skillPath.toFile(), updatedSkill);

            loadedSkills.put(name, updatedSkill);
            log.info("Updated skill '{}' for agent: {}", name, agentName);
        } catch (Exception e) {
            log.error("Error updating skill '{}' for agent: {}", name, agentName, e);
        }
    }

    /**
     * Delete a skill for a specific agent
     */
    public void deleteSkill(String agentName, String name) {
        try {
            Path skillsDir = getSkillsDir(agentName);
            Path skillPath = skillsDir.resolve(name + ".json");
            Files.deleteIfExists(skillPath);
            loadedSkills.remove(name);
            log.info("Deleted skill '{}' for agent: {}", name, agentName);
        } catch (Exception e) {
            log.error("Error deleting skill '{}' for agent: {}", name, agentName, e);
        }
    }

    /**
     * Get skill by name
     */
    public Skill getSkill(String name) {
        return loadedSkills.get(name);
    }

    /**
     * Get all loaded skills
     */
    public Collection<Skill> getAllSkills() {
        return loadedSkills.values();
    }

    /**
     * Reload all skills for a specific agent
     */
    public void reloadAllSkills(String agentName) {
        loadedSkills.clear();
        loadAllSkills(agentName);
        log.info("Reloaded all skills for agent {}: {} skills loaded", agentName, loadedSkills.size());
    }

    /**
     * Shutdown skill manager
     */
    public void shutdown() {
        loadedSkills.clear();
        log.info("SkillManager shutdown complete");
    }
}
