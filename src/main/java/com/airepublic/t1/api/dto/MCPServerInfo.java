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
public class MCPServerInfo {
    private String name;
    private Boolean connected;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MCPToolInfo {
        private String name;
        private String description;
    }
}
