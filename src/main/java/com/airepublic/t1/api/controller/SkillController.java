package com.airepublic.t1.api.controller;

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

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkills() {
        try {
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
    public ResponseEntity<ApiResponse<Void>> createSkill(@RequestBody SkillInfo skillInfo) {
        // TODO: Implement skill creation
        return ResponseEntity.ok(ApiResponse.success(
                "Skill creation endpoint - implementation pending", null));
    }
}
