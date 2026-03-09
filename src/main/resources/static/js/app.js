/**
 * T1 Super AI - Main Application Script
 */

// Application state
const AppState = {
    currentAgent: null,
    isConnected: false,
    agents: [],
    config: null
};

/**
 * Initialize application
 */
document.addEventListener('DOMContentLoaded', async () => {
    console.log('T1 Super AI initializing...');

    // Initialize HTMX configuration
    initializeHTMX();

    // Setup authentication check
    checkAuthentication();

    // Check if first-time setup is needed
    await checkAndShowSetupWizard();

    // Setup global error handlers
    setupErrorHandlers();

    // Setup keyboard shortcuts
    setupKeyboardShortcuts();

    // Setup service worker if available
    if ('serviceWorker' in navigator) {
        // Uncomment to enable service worker
        // navigator.serviceWorker.register('/sw.js');
    }

    console.log('T1 Super AI initialized successfully');
});

/**
 * Initialize HTMX configuration
 */
function initializeHTMX() {
    if (typeof htmx !== 'undefined') {
        // Configure HTMX to include auth headers
        document.body.addEventListener('htmx:configRequest', (event) => {
            const authHeaders = getAuthHeaders();
            Object.keys(authHeaders).forEach(key => {
                event.detail.headers[key] = authHeaders[key];
            });
        });

        // Handle HTMX errors
        document.body.addEventListener('htmx:responseError', (event) => {
            console.error('HTMX error:', event.detail);
            showToast('Request failed: ' + event.detail.error, 'error');
        });

        // Handle HTMX before request
        document.body.addEventListener('htmx:beforeRequest', (event) => {
            console.log('HTMX request:', event.detail.path);
        });

        // Handle HTMX after request
        document.body.addEventListener('htmx:afterRequest', (event) => {
            console.log('HTMX response:', event.detail.successful);
        });
    }
}

/**
 * Check if first-time setup wizard is needed
 */
async function checkAndShowSetupWizard() {
    try {
        const response = await fetch('/api/v1/setup/status');

        if (response.ok) {
            const result = await response.json();

            if (result.success && result.data && result.data.needsSetup) {
                console.log('🥚 First-time setup needed - showing wizard');

                // Show the setup wizard
                const wizard = document.querySelector('setup-wizard');
                if (wizard) {
                    wizard.show();
                } else {
                    console.error('Setup wizard component not found');
                }
            } else {
                console.log('✅ Setup already completed');
            }
        }
    } catch (error) {
        console.error('Failed to check setup status:', error);
        // Don't show error toast - setup check is optional
    }
}

/**
 * Check authentication status
 */
async function checkAuthentication() {
    try {
        const response = await fetch('/api/v1/agents', {
            headers: getAuthHeaders()
        });

        if (response.status === 401) {
            showAuthModal();
            return false;
        }

        if (response.ok) {
            AppState.isConnected = true;
            return true;
        }
    } catch (error) {
        console.error('Authentication check failed:', error);
        showToast('Failed to connect to server', 'error');
    }

    return false;
}

/**
 * Show authentication modal
 */
function showAuthModal() {
    const modal = document.createElement('div');
    modal.className = 'modal active';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h2>Authentication Required</h2>
            </div>
            <div style="padding: var(--spacing-xl);">
                <form id="authForm">
                    <div class="form-group">
                        <label for="auth-username">Username</label>
                        <input type="text" id="auth-username" class="form-control" value="admin" required>
                    </div>
                    <div class="form-group">
                        <label for="auth-password">Password</label>
                        <input type="password" id="auth-password" class="form-control" value="admin123" required>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Login</button>
                    </div>
                </form>
            </div>
        </div>
    `;

    document.body.appendChild(modal);

    const form = modal.querySelector('#authForm');
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = modal.querySelector('#auth-username').value;
        const password = modal.querySelector('#auth-password').value;

        setAuthCredentials(username, password);

        const success = await checkAuthentication();
        if (success) {
            modal.remove();
            showToast('Authentication successful');
            location.reload();
        } else {
            showToast('Authentication failed', 'error');
        }
    });
}

/**
 * Setup global error handlers
 */
function setupErrorHandlers() {
    // Handle unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
        console.error('Unhandled promise rejection:', event.reason);
        showToast('An unexpected error occurred', 'error');
    });

    // Handle global errors
    window.addEventListener('error', (event) => {
        console.error('Global error:', event.error);
    });
}

/**
 * Setup keyboard shortcuts
 */
function setupKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
        // Cmd/Ctrl + K: Focus message input
        if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
            e.preventDefault();
            const input = document.querySelector('#messageInput');
            if (input) {
                input.focus();
            }
        }

        // Cmd/Ctrl + /: Show keyboard shortcuts help
        if ((e.metaKey || e.ctrlKey) && e.key === '/') {
            e.preventDefault();
            showKeyboardShortcuts();
        }

        // Cmd/Ctrl + ,: Open settings
        if ((e.metaKey || e.ctrlKey) && e.key === ',') {
            e.preventDefault();
            showConfigModal();
        }

        // Cmd/Ctrl + E: Edit character
        if ((e.metaKey || e.ctrlKey) && e.key === 'e') {
            e.preventDefault();
            showCharacterEditor();
        }

        // Cmd/Ctrl + N: New agent
        if ((e.metaKey || e.ctrlKey) && e.key === 'n') {
            e.preventDefault();
            showCreateAgentModal();
        }
    });
}

/**
 * Show keyboard shortcuts help
 */
function showKeyboardShortcuts() {
    const modal = document.createElement('div');
    modal.className = 'modal active';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h2>Keyboard Shortcuts</h2>
                <button class="btn-close" onclick="this.closest('.modal').remove()">&times;</button>
            </div>
            <div style="padding: var(--spacing-xl);">
                <div class="shortcuts-list">
                    <div class="shortcut-item">
                        <kbd>Ctrl/Cmd + K</kbd>
                        <span>Focus message input</span>
                    </div>
                    <div class="shortcut-item">
                        <kbd>Ctrl/Cmd + ,</kbd>
                        <span>Open configuration</span>
                    </div>
                    <div class="shortcut-item">
                        <kbd>Ctrl/Cmd + E</kbd>
                        <span>Edit character profile</span>
                    </div>
                    <div class="shortcut-item">
                        <kbd>Ctrl/Cmd + N</kbd>
                        <span>Create new agent</span>
                    </div>
                    <div class="shortcut-item">
                        <kbd>Ctrl/Cmd + /</kbd>
                        <span>Show this help</span>
                    </div>
                    <div class="shortcut-item">
                        <kbd>Esc</kbd>
                        <span>Close modal</span>
                    </div>
                    <div class="shortcut-item">
                        <kbd>Shift + Enter</kbd>
                        <span>New line in message</span>
                    </div>
                    <div class="shortcut-item">
                        <kbd>Enter</kbd>
                        <span>Send message</span>
                    </div>
                </div>
            </div>
        </div>
    `;

    document.body.appendChild(modal);

    // Add styles for shortcuts
    const style = document.createElement('style');
    style.textContent = `
        .shortcuts-list {
            display: grid;
            gap: var(--spacing-md);
        }
        .shortcut-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: var(--spacing-sm);
            background: var(--bg-tertiary);
            border-radius: var(--radius-md);
        }
        kbd {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-sm);
            padding: 0.25rem 0.5rem;
            font-family: monospace;
            font-size: 0.875rem;
        }
    `;
    document.head.appendChild(style);
}

/**
 * Connection status monitor
 */
let connectionCheckInterval;

function startConnectionMonitor() {
    if (connectionCheckInterval) {
        clearInterval(connectionCheckInterval);
    }

    connectionCheckInterval = setInterval(async () => {
        try {
            const response = await fetch('/api/v1/agents', {
                headers: getAuthHeaders()
            });

            const wasConnected = AppState.isConnected;
            AppState.isConnected = response.ok;

            // Show notification if connection status changed
            if (wasConnected && !AppState.isConnected) {
                showToast('Lost connection to server', 'error');
            } else if (!wasConnected && AppState.isConnected) {
                showToast('Connection restored', 'success');
            }
        } catch (error) {
            if (AppState.isConnected) {
                AppState.isConnected = false;
                showToast('Lost connection to server', 'error');
            }
        }
    }, 30000); // Check every 30 seconds
}

// Start connection monitor
startConnectionMonitor();

/**
 * Handle visibility change
 */
document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
        // Page is hidden, reduce activity
        if (connectionCheckInterval) {
            clearInterval(connectionCheckInterval);
        }
    } else {
        // Page is visible again
        startConnectionMonitor();
        checkAuthentication();
    }
});

/**
 * Handle online/offline events
 */
window.addEventListener('online', () => {
    showToast('Back online', 'success');
    checkAuthentication();
});

window.addEventListener('offline', () => {
    showToast('You are offline', 'warning');
    AppState.isConnected = false;
});

/**
 * Export app state for debugging
 */
window.AppState = AppState;

// Log version info
console.log('%cT1 Super AI', 'font-size: 24px; font-weight: bold; color: #6366f1;');
console.log('%cVersion 1.0.0', 'font-size: 12px; color: #94a3b8;');
console.log('%cBuilt with Thymeleaf + HTMX + Web Components', 'font-size: 10px; color: #64748b;');
