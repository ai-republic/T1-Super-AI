package com.airepublic.t1.skills;

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
 * Skills are stored in: ~/.t1-super-ai/skills/
 */
@Slf4j
@Component
public class SkillManager {
    private static final String SKILLS_DIR = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "skills").toString();

    private final Map<String, Skill> loadedSkills = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillManager() {
        initializeDirectories();
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(SKILLS_DIR));
            log.info("Skills directory initialized: {}", SKILLS_DIR);
        } catch (Exception e) {
            log.error("Error creating skills directory", e);
        }
    }

    /**
     * Load all skills from the skills directory
     */
    public void loadAllSkills() {
        try {
            Path skillsPath = Paths.get(SKILLS_DIR);
            if (!Files.exists(skillsPath)) {
                log.warn("Skills directory does not exist: {}", SKILLS_DIR);
                return;
            }

            try (Stream<Path> paths = Files.walk(skillsPath)) {
                paths.filter(p -> p.toString().endsWith(".json"))
                        .forEach(this::loadSkillFromJson);
            }
        } catch (Exception e) {
            log.error("Error loading skills", e);
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
     * Create a new skill
     */
    public void createSkill(String name, String description, String prompt, List<String> requiredTools) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setPrompt(prompt);
        skill.setRequiredTools(requiredTools);
        skill.setCreatedAt(new Date());

        try {
            Path skillPath = Paths.get(SKILLS_DIR, name + ".json");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(skillPath.toFile(), skill);

            loadedSkills.put(name, skill);
            log.info("Created skill: {}", name);
        } catch (Exception e) {
            log.error("Error creating skill: {}", name, e);
        }
    }

    /**
     * Update an existing skill
     */
    public void updateSkill(String name, Skill updatedSkill) {
        try {
            Path skillPath = Paths.get(SKILLS_DIR, name + ".json");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(skillPath.toFile(), updatedSkill);

            loadedSkills.put(name, updatedSkill);
            log.info("Updated skill: {}", name);
        } catch (Exception e) {
            log.error("Error updating skill: {}", name, e);
        }
    }

    /**
     * Delete a skill
     */
    public void deleteSkill(String name) {
        try {
            Path skillPath = Paths.get(SKILLS_DIR, name + ".json");
            Files.deleteIfExists(skillPath);
            loadedSkills.remove(name);
            log.info("Deleted skill: {}", name);
        } catch (Exception e) {
            log.error("Error deleting skill: {}", name, e);
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
     * Reload all skills
     */
    public void reloadAllSkills() {
        loadedSkills.clear();
        loadAllSkills();
        log.info("Reloaded all skills: {} skills loaded", loadedSkills.size());
    }

    /**
     * Shutdown skill manager
     */
    public void shutdown() {
        loadedSkills.clear();
        log.info("SkillManager shutdown complete");
    }
}
