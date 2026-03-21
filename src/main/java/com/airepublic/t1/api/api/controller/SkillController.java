package com.airepublic.t1.api.controller;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.api.dto.ApiResponse;
import com.airepublic.t1.api.dto.SkillInfo;
import com.airepublic.t1.skills.SkillManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {
    private final SkillManager skillManager;
    private final AgentManager agentManager;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkills(
            @RequestParam(required = false) String agentName) {
        try {
            // Use provided agent name or current agent
            final String targetAgent = agentName != null ? agentName : agentManager.getCurrentAgentName();

            if (targetAgent == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("No agent specified and no current agent available"));
            }

            // Load skills for the target agent
            skillManager.loadAllSkills(targetAgent);

            List<SkillInfo> skills = skillManager.getAllSkills().stream()
                    .map(skill -> SkillInfo.builder()
                            .name(skill.getName())
                            .description(skill.getDescription())
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(skills));

        } catch (Exception e) {
            log.error("Error listing skills", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error listing skills: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createSkill(
            @RequestParam(required = false) String agentName,
            @RequestBody SkillInfo skillInfo) {
        try {
            // Use provided agent name or current agent
            final String targetAgent = agentName != null ? agentName : agentManager.getCurrentAgentName();

            if (targetAgent == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("No agent specified and no current agent available"));
            }

            skillManager.createSkill(targetAgent, skillInfo.getName(),
                    skillInfo.getDescription(), skillInfo.getPrompt(),
                    skillInfo.getRequiredTools());

            return ResponseEntity.ok(ApiResponse.success(
                    "Skill '" + skillInfo.getName() + "' created for agent: " + targetAgent, null));

        } catch (Exception e) {
            log.error("Error creating skill", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error creating skill: " + e.getMessage()));
        }
    }
}
