package com.airepublic.t1.api.controller;

import com.airepublic.t1.api.dto.ApiResponse;
import com.airepublic.t1.api.dto.PluginInfo;
import com.airepublic.t1.api.dto.SkillInfo;
import com.airepublic.t1.plugins.PluginManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/plugins")
@RequiredArgsConstructor
public class PluginController {
    private final PluginManager pluginManager;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PluginInfo>>> listPlugins() {
        try {
            List<PluginInfo> plugins = pluginManager.getAllPlugins().stream()
                    .map(plugin -> PluginInfo.builder()
                            .name(plugin.getName())
                            .version(plugin.getVersion())
                            .description(plugin.getDescription())
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(plugins));

        } catch (Exception e) {
            log.error("Error listing plugins", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error listing plugins: " + e.getMessage()));
        }
    }

    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<Void>> reloadPlugins() {
        try {
            pluginManager.shutdown();
            pluginManager.loadAllPlugins();
            return ResponseEntity.ok(ApiResponse.success("Plugins reloaded successfully", null));

        } catch (Exception e) {
            log.error("Error reloading plugins", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error reloading plugins: " + e.getMessage()));
        }
    }
}
