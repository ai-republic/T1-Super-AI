/**
 * Utility functions for T1 Super AI
 */

// Authentication
let authCredentials = {
    username: 'admin',
    password: 'admin123'
};

/**
 * Get authentication headers for API requests
 */
function getAuthHeaders() {
    const encoded = btoa(`${authCredentials.username}:${authCredentials.password}`);
    return {
        'Authorization': `Basic ${encoded}`
    };
}

/**
 * Set authentication credentials
 */
function setAuthCredentials(username, password) {
    authCredentials = { username, password };
    localStorage.setItem('auth-username', username);
    localStorage.setItem('auth-password', password);
}

/**
 * Load stored credentials from localStorage
 */
function loadStoredCredentials() {
    const username = localStorage.getItem('auth-username');
    const password = localStorage.getItem('auth-password');

    if (username && password) {
        authCredentials = { username, password };
    }
}

// Load credentials on startup
loadStoredCredentials();

/**
 * Modal Management
 */

function showConfigModal() {
    const modal = document.getElementById('configModal');
    if (modal) {
        modal.classList.add('active');
        // Reload config data
        if (window.configEditor) {
            window.configEditor.loadConfiguration();
        }
    }
}

function closeConfigModal() {
    const modal = document.getElementById('configModal');
    if (modal) {
        modal.classList.remove('active');
    }
}

function showCharacterEditor() {
    const modal = document.getElementById('characterModal');
    if (modal) {
        modal.classList.add('active');
        // Load current agent if not already loaded
        if (window.characterEditor && !window.characterEditor.agentDetails) {
            window.characterEditor.loadCurrentAgent();
        }
    }
}

function closeCharacterModal() {
    const modal = document.getElementById('characterModal');
    if (modal) {
        modal.classList.remove('active');
    }
}

function showCreateAgentModal() {
    const modal = document.getElementById('createAgentModal');
    if (modal) {
        modal.classList.add('active');
    }
}

function closeCreateAgentModal() {
    const modal = document.getElementById('createAgentModal');
    if (modal) {
        modal.classList.remove('active');
    }
}

// Close modals when clicking outside
document.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal')) {
        e.target.classList.remove('active');
    }
});

// Close modals on Escape key
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal.active').forEach(modal => {
            modal.classList.remove('active');
        });
    }
});

/**
 * Toast Notifications
 */

let toastTimeout;

function showToast(message, type = 'success') {
    // Remove existing toast
    const existingToast = document.querySelector('.toast');
    if (existingToast) {
        existingToast.remove();
    }

    // Clear existing timeout
    if (toastTimeout) {
        clearTimeout(toastTimeout);
    }

    // Create toast element
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    // Add to document
    document.body.appendChild(toast);

    // Auto-remove after 3 seconds
    toastTimeout = setTimeout(() => {
        toast.style.animation = 'slideOutRight 0.3s ease-out';
        setTimeout(() => {
            if (toast.parentNode) {
                toast.remove();
            }
        }, 300);
    }, 3000);
}

// Add slideOutRight animation
const style = document.createElement('style');
style.textContent = `
    @keyframes slideOutRight {
        from {
            opacity: 1;
            transform: translateX(0);
        }
        to {
            opacity: 0;
            transform: translateX(100%);
        }
    }
`;
document.head.appendChild(style);

/**
 * Format timestamp
 */
function formatTimestamp(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;

    // Less than 1 minute
    if (diff < 60000) {
        return 'Just now';
    }

    // Less than 1 hour
    if (diff < 3600000) {
        const minutes = Math.floor(diff / 60000);
        return `${minutes}m ago`;
    }

    // Less than 24 hours
    if (diff < 86400000) {
        const hours = Math.floor(diff / 3600000);
        return `${hours}h ago`;
    }

    // Show time
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/**
 * Debounce function
 */
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Copy text to clipboard
 */
async function copyToClipboard(text) {
    try {
        await navigator.clipboard.writeText(text);
        showToast('Copied to clipboard');
    } catch (err) {
        console.error('Failed to copy:', err);
        showToast('Failed to copy', 'error');
    }
}

/**
 * Format file size
 */
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

/**
 * Validate email
 */
function isValidEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(email);
}

/**
 * Generate random ID
 */
function generateId(prefix = 'id') {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Check if element is in viewport
 */
function isInViewport(element) {
    const rect = element.getBoundingClientRect();
    return (
        rect.top >= 0 &&
        rect.left >= 0 &&
        rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
        rect.right <= (window.innerWidth || document.documentElement.clientWidth)
    );
}

/**
 * Smooth scroll to element
 */
function scrollToElement(element, offset = 0) {
    const elementPosition = element.getBoundingClientRect().top;
    const offsetPosition = elementPosition + window.pageYOffset - offset;

    window.scrollTo({
        top: offsetPosition,
        behavior: 'smooth'
    });
}

/**
 * API Error Handler
 */
async function handleApiError(response) {
    let errorMessage = 'An error occurred';

    try {
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            const errorData = await response.json();
            errorMessage = errorData.message || errorData.error || errorMessage;
        } else {
            errorMessage = await response.text() || errorMessage;
        }
    } catch (e) {
        console.error('Failed to parse error response:', e);
    }

    showToast(errorMessage, 'error');
    return errorMessage;
}

/**
 * Retry failed requests
 */
async function retryFetch(url, options = {}, maxRetries = 3) {
    for (let i = 0; i < maxRetries; i++) {
        try {
            const response = await fetch(url, options);
            if (response.ok) {
                return response;
            }

            // Don't retry on 4xx errors
            if (response.status >= 400 && response.status < 500) {
                return response;
            }

            // Wait before retrying
            if (i < maxRetries - 1) {
                await new Promise(resolve => setTimeout(resolve, 1000 * (i + 1)));
            }
        } catch (error) {
            if (i === maxRetries - 1) {
                throw error;
            }
        }
    }
}

/**
 * Export utilities for testing
 */
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        getAuthHeaders,
        setAuthCredentials,
        showToast,
        formatTimestamp,
        debounce,
        copyToClipboard,
        formatFileSize,
        isValidEmail,
        generateId,
        isInViewport,
        scrollToElement,
        handleApiError,
        retryFetch
    };
}
