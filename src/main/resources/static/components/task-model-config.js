/**
 * Task-Specific Model Configuration Component
 *
 * Allows users to configure different models for different task types after initial setup.
 */
class TaskModelConfig extends HTMLElement {
    constructor() {
        super();
        this.config = null;
        this.taskModels = {};
        this.taskTypes = [
            { key: 'GENERAL_KNOWLEDGE', name: 'General Knowledge', desc: 'General purpose tasks, Q&A, reasoning', icon: '📝' },
            { key: 'CODING', name: 'Coding', desc: 'Code generation, debugging, refactoring', icon: '💻' },
            { key: 'SPEECH_TO_TEXT', name: 'Speech-to-Text (STT)', desc: 'Audio transcription', icon: '🎤' },
            { key: 'TEXT_TO_SPEECH', name: 'Text-to-Speech (TTS)', desc: 'Convert text to audio', icon: '🔊' },
            { key: 'IMAGE_ANALYSIS', name: 'Image Analysis', desc: 'Image understanding and vision', icon: '🖼️' },
            { key: 'IMAGE_GENERATION', name: 'Image Generation', desc: 'Create images from text', icon: '🎨' },
            { key: 'VIDEO_ANALYSIS', name: 'Video Analysis', desc: 'Video understanding', icon: '🎬' },
            { key: 'VIDEO_GENERATION', name: 'Video Generation', desc: 'Create/edit videos', icon: '📹' }
        ];
    }

    connectedCallback() {
        this.render();
        this.loadConfiguration();
    }

    render() {
        this.innerHTML = `
            <div class="task-model-config">
                <div class="config-header">
                    <h2>🎯 Task-Specific Model Configuration</h2>
                    <p class="subtitle">Configure different AI models for different types of tasks</p>
                </div>

                <div id="loadingState" class="loading-state">
                    <div class="spinner"></div>
                    <p>Loading configuration...</p>
                </div>

                <div id="errorState" class="error-state" style="display: none;">
                    <p class="error-message"></p>
                    <button class="btn btn-secondary" onclick="this.closest('task-model-config').loadConfiguration()">Retry</button>
                </div>

                <div id="configContent" style="display: none;">
                    <div class="info-box">
                        <strong>💡 How it works:</strong>
                        <p>Assign specific AI models to different task types. When auto-model selection is enabled,
                           the system will automatically use the right model based on your prompt.</p>
                    </div>

                    <div class="auto-model-toggle">
                        <label class="toggle-label">
                            <input type="checkbox" id="autoModelEnabled">
                            <span>Enable Automatic Model Selection</span>
                        </label>
                        <small>Automatically select task-specific models based on prompt analysis</small>
                    </div>

                    <div class="task-models-grid" id="taskModelsGrid">
                        <!-- Task model cards will be inserted here -->
                    </div>

                    <div class="config-actions">
                        <button class="btn btn-secondary" onclick="this.closest('task-model-config').resetToDefaults()">
                            Reset to Defaults
                        </button>
                        <button class="btn btn-primary" onclick="this.closest('task-model-config').saveConfiguration()">
                            💾 Save Configuration
                        </button>
                    </div>
                </div>
            </div>
        `;

        this.setupEventListeners();
    }

    setupEventListeners() {
        const autoModelToggle = this.querySelector('#autoModelEnabled');
        autoModelToggle?.addEventListener('change', (e) => {
            this.updateAutoModelSelection(e.target.checked);
        });
    }

    async loadConfiguration() {
        const loading = this.querySelector('#loadingState');
        const error = this.querySelector('#errorState');
        const content = this.querySelector('#configContent');

        loading.style.display = 'block';
        error.style.display = 'none';
        content.style.display = 'none';

        try {
            const response = await fetch('/api/v1/config');

            if (response.ok) {
                const apiResponse = await response.json();
                this.config = apiResponse.data;
                this.taskModels = this.config.taskModels || {};

                // Update auto-model toggle
                const autoModelToggle = this.querySelector('#autoModelEnabled');
                if (autoModelToggle) {
                    autoModelToggle.checked = this.config.autoModelSelectionEnabled || false;
                }

                this.renderTaskModels();

                loading.style.display = 'none';
                content.style.display = 'block';
            } else {
                throw new Error('Failed to load configuration');
            }
        } catch (err) {
            console.error('Failed to load configuration:', err);
            loading.style.display = 'none';
            error.style.display = 'block';
            error.querySelector('.error-message').textContent = 'Failed to load configuration: ' + err.message;
        }
    }

    renderTaskModels() {
        const grid = this.querySelector('#taskModelsGrid');
        if (!grid) return;

        grid.innerHTML = this.taskTypes.map(taskType => this.renderTaskModelCard(taskType)).join('');
    }

    renderTaskModelCard(taskType) {
        const currentConfig = this.taskModels[taskType.key];
        const providers = this.getConfiguredProviders();

        // Get default provider info
        const defaultProvider = this.config.defaultProvider;
        const defaultModel = this.config.llmConfigs[defaultProvider]?.model || 'default';

        return `
            <div class="task-model-card" data-task="${taskType.key}">
                <div class="task-header">
                    <span class="task-icon">${taskType.icon}</span>
                    <div class="task-info">
                        <h4>${taskType.name}</h4>
                        <p class="task-desc">${taskType.desc}</p>
                    </div>
                </div>

                <div class="task-config">
                    <div class="form-group">
                        <label>Provider</label>
                        <select class="form-select provider-select" data-task="${taskType.key}">
                            <option value="">Use Default (${defaultProvider})</option>
                            ${providers.map(p => `
                                <option value="${p}" ${currentConfig?.provider === p ? 'selected' : ''}>
                                    ${this.getProviderIcon(p)} ${p}
                                </option>
                            `).join('')}
                        </select>
                    </div>

                    <div class="form-group">
                        <label>Model</label>
                        <input type="text"
                               class="form-input model-input"
                               data-task="${taskType.key}"
                               placeholder="${this.getModelSuggestion(taskType.key, currentConfig?.provider || defaultProvider)}"
                               value="${currentConfig?.model || ''}">
                        <small class="model-hint">Leave empty to use provider default</small>
                    </div>

                    ${currentConfig ? `
                        <button class="btn-remove" onclick="this.closest('task-model-config').removeTaskModel('${taskType.key}')">
                            🗑️ Remove
                        </button>
                    ` : ''}
                </div>

                <div class="current-config">
                    ${currentConfig ?
                        `<span class="badge badge-success">Configured: ${currentConfig.provider} / ${currentConfig.model}</span>` :
                        `<span class="badge badge-secondary">Using default: ${defaultProvider} / ${defaultModel}</span>`
                    }
                </div>
            </div>
        `;
    }

    getConfiguredProviders() {
        if (!this.config || !this.config.llmConfigs) return [];
        return Object.keys(this.config.llmConfigs);
    }

    getProviderIcon(provider) {
        const icons = {
            'OPENAI': '🟢',
            'ANTHROPIC': '🟣',
            'OLLAMA': '🦙'
        };
        return icons[provider] || '🔵';
    }

    getModelSuggestion(taskType, provider) {
        const suggestions = {
            'CODING': {
                'OPENAI': 'gpt-4o or o1-preview',
                'ANTHROPIC': 'claude-3-5-sonnet-20241022',
                'OLLAMA': 'codellama or deepseek-coder'
            },
            'SPEECH_TO_TEXT': {
                'OPENAI': 'whisper-1'
            },
            'TEXT_TO_SPEECH': {
                'OPENAI': 'tts-1 or tts-1-hd'
            },
            'IMAGE_ANALYSIS': {
                'OPENAI': 'gpt-4o or gpt-4-vision-preview',
                'ANTHROPIC': 'claude-3-5-sonnet-20241022'
            },
            'IMAGE_GENERATION': {
                'OPENAI': 'dall-e-3'
            },
            'VIDEO_ANALYSIS': {
                'OPENAI': 'gpt-4o',
                'ANTHROPIC': 'claude-3-5-sonnet-20241022'
            }
        };

        return suggestions[taskType]?.[provider] || 'Use provider default';
    }

    async updateAutoModelSelection(enabled) {
        try {
            const response = await fetch('/api/v1/config/auto-model', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ enabled })
            });

            if (response.ok) {
                this.showNotification('success', `Auto-model selection ${enabled ? 'enabled' : 'disabled'}`);
            } else {
                throw new Error('Failed to update auto-model selection');
            }
        } catch (err) {
            console.error('Failed to update auto-model:', err);
            this.showNotification('error', 'Failed to update auto-model selection');
            // Revert toggle
            const toggle = this.querySelector('#autoModelEnabled');
            if (toggle) toggle.checked = !enabled;
        }
    }

    async saveConfiguration() {
        const taskModelUpdates = {};

        // Collect all task model configurations
        this.taskTypes.forEach(taskType => {
            const providerSelect = this.querySelector(`.provider-select[data-task="${taskType.key}"]`);
            const modelInput = this.querySelector(`.model-input[data-task="${taskType.key}"]`);

            if (providerSelect && modelInput) {
                const provider = providerSelect.value;
                const model = modelInput.value.trim();

                // Only include if provider is selected
                if (provider) {
                    taskModelUpdates[taskType.key] = {
                        provider: provider,
                        model: model || this.config.llmConfigs[provider]?.model || ''
                    };
                }
            }
        });

        try {
            const response = await fetch('/api/v1/config/task-models', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ taskModels: taskModelUpdates })
            });

            if (response.ok) {
                this.showNotification('success', 'Task-specific models saved successfully!');
                // Reload configuration to reflect changes
                await this.loadConfiguration();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to save configuration');
            }
        } catch (err) {
            console.error('Failed to save configuration:', err);
            this.showNotification('error', 'Failed to save configuration: ' + err.message);
        }
    }

    async removeTaskModel(taskType) {
        if (!confirm(`Remove task-specific model for ${taskType}? It will use the default provider instead.`)) {
            return;
        }

        try {
            const response = await fetch(`/api/v1/config/task-model/${taskType}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                this.showNotification('success', `Task model for ${taskType} removed`);
                await this.loadConfiguration();
            } else {
                throw new Error('Failed to remove task model');
            }
        } catch (err) {
            console.error('Failed to remove task model:', err);
            this.showNotification('error', 'Failed to remove task model');
        }
    }

    resetToDefaults() {
        if (!confirm('Reset all task-specific models to default? This will remove all custom configurations.')) {
            return;
        }

        // Clear all selections
        this.querySelectorAll('.provider-select').forEach(select => select.value = '');
        this.querySelectorAll('.model-input').forEach(input => input.value = '');

        this.showNotification('info', 'Configuration reset. Click Save to apply changes.');
    }

    showNotification(type, message) {
        // Use existing notification system if available
        if (window.showNotification) {
            window.showNotification(message, type);
        } else {
            // Fallback to console
            console.log(`[${type.toUpperCase()}] ${message}`);
            alert(message);
        }
    }
}

// Register the custom element
customElements.define('task-model-config', TaskModelConfig);

// Global helper to open task model configuration
function openTaskModelConfig() {
    const modal = document.getElementById('taskModelModal');
    if (modal) {
        modal.style.display = 'block';
    }
}

function closeTaskModelConfig() {
    const modal = document.getElementById('taskModelModal');
    if (modal) {
        modal.style.display = 'none';
    }
}
