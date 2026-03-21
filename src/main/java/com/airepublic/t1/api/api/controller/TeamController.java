package com.airepublic.t1.api.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.airepublic.t1.api.dto.ApiResponse;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.ConfigurationLoader;
import com.airepublic.t1.config.WorkspaceInitializer;
import com.airepublic.t1.util.PathUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for team workspace management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final WorkspaceInitializer workspaceInitializer;
    private final AgentConfigService agentConfigService;
    private final ConfigurationLoader configurationLoader;
    private final com.airepublic.t1.agent.AgentManager agentManager;
    private final com.airepublic.t1.agent.AgentOrchestrator agentOrchestrator;

    /**
     * Get list of all available teams
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> listTeams() {
        try {
            final List<String> teams = getAvailableTeams();
            return ResponseEntity.ok(ApiResponse.success(teams));
        } catch (final Exception e) {
            log.error("Failed to list teams", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to list teams: " + e.getMessage()));
        }
    }

    /**
     * Get the current active team
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<String>> getCurrentTeam() {
        try {
            final String currentTeam = workspaceInitializer.getTeamName();
            return ResponseEntity.ok(ApiResponse.success(currentTeam));
        } catch (final Exception e) {
            log.error("Failed to get current team", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get current team: " + e.getMessage()));
        }
    }

    /**
     * Switch to a different team workspace
     */
    @PostMapping("/switch")
    public ResponseEntity<ApiResponse<Void>> switchTeam(@RequestBody final Map<String, String> request) {
        try {
            final String teamName = request.get("teamName");

            if (teamName == null || teamName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Team name is required"));
            }

            // Validate team name for filesystem safety
            if (!PathUtils.isValidTeamName(teamName)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Team name contains invalid characters. Suggested: " + PathUtils.sanitizeTeamName(teamName)));
            }

            log.info("Switching to team: {}", teamName);

            // Update workspace paths for all services
            workspaceInitializer.setTeamName(teamName);
            agentConfigService.setTeamName(teamName);
            configurationLoader.setWorkspaceInitializer(workspaceInitializer);

            // Reinitialize directories for the new team
            configurationLoader.initializeDirectories();

            // Reload agents from the new team workspace
            agentManager.reloadAgentsForCurrentTeam(agentOrchestrator);

            log.info("Successfully switched to team: {}", teamName);
            return ResponseEntity.ok(ApiResponse.success("Switched to team: " + teamName, null));

        } catch (final Exception e) {
            log.error("Failed to switch team", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to switch team: " + e.getMessage()));
        }
    }

    /**
     * Rename a team and update all agent CHARACTER.md and USAGE.md files
     */
    @PostMapping("/rename")
    public ResponseEntity<ApiResponse<Void>> renameTeam(@RequestBody final Map<String, String> request) {
        try {
            final String oldTeamName = request.get("oldTeamName");
            final String newTeamName = request.get("newTeamName");

            if (oldTeamName == null || oldTeamName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Old team name is required"));
            }

            if (newTeamName == null || newTeamName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("New team name is required"));
            }

            if (oldTeamName.equals(newTeamName)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("New team name must be different from old team name"));
            }

            // Validate new team name for filesystem safety
            if (!PathUtils.isValidTeamName(newTeamName)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("New team name contains invalid characters. Suggested: " + PathUtils.sanitizeTeamName(newTeamName)));
            }

            log.info("Renaming team from '{}' to '{}'", oldTeamName, newTeamName);

            // Get paths
            final Path workspacesRoot = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "workspaces");
            final Path oldTeamPath = workspacesRoot.resolve(oldTeamName);
            final Path newTeamPath = workspacesRoot.resolve(newTeamName);

            // Check if old team exists
            if (!Files.exists(oldTeamPath)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Team '" + oldTeamName + "' does not exist"));
            }

            // Check if new team already exists
            if (Files.exists(newTeamPath)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Team '" + newTeamName + "' already exists"));
            }

            // Update all agent CHARACTER.md and USAGE.md files in the team
            final Path agentsDir = oldTeamPath.resolve("agents");
            if (Files.exists(agentsDir)) {
                try (Stream<Path> agentPaths = Files.list(agentsDir)) {
                    agentPaths.filter(Files::isDirectory).forEach(agentPath -> {
                        try {
                            updateAgentFiles(agentPath, oldTeamName, newTeamName);
                        } catch (final IOException e) {
                            log.error("Failed to update agent files in: {}", agentPath, e);
                        }
                    });
                }
            }

            // Rename the team directory
            Files.move(oldTeamPath, newTeamPath);

            // If this is the current team, switch to the new name
            if (workspaceInitializer.getTeamName().equals(oldTeamName)) {
                workspaceInitializer.setTeamName(newTeamName);
                agentConfigService.setTeamName(newTeamName);
                configurationLoader.setWorkspaceInitializer(workspaceInitializer);
                configurationLoader.initializeDirectories();
            }

            log.info("Successfully renamed team from '{}' to '{}'", oldTeamName, newTeamName);
            return ResponseEntity.ok(ApiResponse.success(
                    "Team renamed from '" + oldTeamName + "' to '" + newTeamName + "'. All agent files updated.", null));

        } catch (final Exception e) {
            log.error("Failed to rename team", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to rename team: " + e.getMessage()));
        }
    }

    /**
     * Update CHARACTER.md and USAGE.md files for an agent with new team name.
     * Uses PathUtils to safely handle special characters in team names.
     */
    private void updateAgentFiles(final Path agentPath, final String oldTeamName, final String newTeamName) throws IOException {
        // Update CHARACTER.md
        final Path characterMd = agentPath.resolve("CHARACTER.md");
        if (Files.exists(characterMd)) {
            String content = Files.readString(characterMd);

            // Use PathUtils for safe path replacement with proper regex escaping
            content = PathUtils.replaceTeamPathsInContent(content, oldTeamName, newTeamName);

            Files.writeString(characterMd, content);
            log.info("Updated CHARACTER.md for agent: {}", agentPath.getFileName());
        }

        // Update USAGE.md
        final Path usageMd = agentPath.resolve("USAGE.md");
        if (Files.exists(usageMd)) {
            String content = Files.readString(usageMd);

            // Use PathUtils for safe path replacement with proper regex escaping
            content = PathUtils.replaceTeamPathsInContent(content, oldTeamName, newTeamName);

            Files.writeString(usageMd, content);
            log.info("Updated USAGE.md for agent: {}", agentPath.getFileName());
        }
    }

    /**
     * Scan the workspaces directory to find all available teams
     */
    private List<String> getAvailableTeams() {
        final List<String> teams = new ArrayList<>();
        final Path workspacesRoot = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "workspaces");

        try {
            // Create workspaces root if it doesn't exist
            if (!Files.exists(workspacesRoot)) {
                Files.createDirectories(workspacesRoot);
                log.info("Created workspaces root directory: {}", workspacesRoot);
            }

            // Scan for team directories
            try (Stream<Path> paths = Files.list(workspacesRoot)) {
                paths.filter(Files::isDirectory)
                     .map(Path::getFileName)
                     .map(Path::toString)
                     .forEach(teams::add);
            }

            // If no teams found, add Default
            if (teams.isEmpty()) {
                teams.add("Default");
            }

            log.info("Found {} team(s): {}", teams.size(), teams);

        } catch (final IOException e) {
            log.error("Error scanning for teams", e);
            // Return default team on error
            teams.clear();
            teams.add("Default");
        }

        return teams;
    }
}
