package com.airepublic.t1.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for setup status check.
 */
@Data
@Builder
public class SetupStatusResponse {
    /**
     * Whether first-time setup is needed
     */
    private boolean needsSetup;

    /**
     * Whether the workspace has been initialized
     */
    private boolean workspaceInitialized;
}
