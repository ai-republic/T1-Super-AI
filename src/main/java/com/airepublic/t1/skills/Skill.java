package com.airepublic.t1.skills;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class Skill {
    private String name;
    private String description;
    private String prompt;
    private List<String> requiredTools = new ArrayList<>();
    private Date createdAt;
    private Date updatedAt;
}
