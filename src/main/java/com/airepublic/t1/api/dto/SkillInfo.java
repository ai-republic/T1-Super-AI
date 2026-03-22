package com.airepublic.t1.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillInfo {
    private String name;
    private String description;
    private String prompt;
    private List<String> requiredTools;
}
