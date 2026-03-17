class ConfigEditor extends HTMLElement {
    constructor() {
        super();
        this.config = null;
        this.availableModels = [];
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
            <div class="config-editor">
                <div class="loading" id="configLoading">Loading configuration...</div>
                <form id="configForm" style="display: none;">
                    <div class="form-section">
                        <h3>Default Provider</h3>
                        <div class="form-group">
                            <label for="defaultProvider">Provider</label>
                            <select id="defaultProvider" class="form-control" required>
                                <option value="">Select Provider</option>
                                <option value="OPENAI">OpenAI</option>
                                <option value="ANTHROPIC">Anthropic</option>
                                <option value="OLLAMA">Ollama</option>
                            </select>
                        </div>

                        <div class="form-group">
                            <label for="defaultModel">Default Model</label>
                            <input type="text" id="defaultModel" class="form-control" required
                                placeholder="e.g., gpt-4o, claude-3-5-sonnet-20241022">
                            <small class="form-help">Enter the model name for the selected provider</small>
                        </div>
                    </div>

                    <div class="form-section">
                        <h3>Automatic Model Selection</h3>
                        <div class="form-group">
                            <label>
                                <input type="checkbox" id="useAutoModelSelection">
                                Enable automatic model selection
                            </label>
                            <small class="form-help">Automatically select the best model based on task type</small>
                        </div>
                    </div>

                    <div class="form-section" id="taskModelsSection" style="display: none;">
                        <h3>🎯 Task-Specific Models</h3>
                        <div class="info-box" style="margin-bottom: 1rem;">
                            <strong>💡 Configure different models for different tasks.</strong>
                            <p style="margin: 0.5rem 0 0; font-size: 0.875rem;">
                                When auto-model selection is enabled, the system will automatically
                                use these models based on your prompt type.
                            </p>
                        </div>
                        <div id="taskModelsContainer">
                            <!-- Task models will be inserted here -->
                        </div>
                    </div>

                    <div class="form-actions">
                        <button type="button" class="btn btn-secondary" onclick="closeConfigModal()">Cancel</button>
                        <button type="submit" class="btn btn-primary">💾 Save Configuration</button>
                    </div>
                </form>
            </div>
        `;

        this.setupEventListeners();
    }

    setupEventListeners() {
        const form = this.querySelector('#configForm');

        form?.addEventListener('submit', (e) => {
            e.preventDefault();
            this.saveConfiguration();
        });

        // Toggle task models section when auto-model checkbox changes
        const autoModelCheckbox = this.querySelector('#useAutoModelSelection');
        autoModelCheckbox?.addEventListener('change', (e) => {
            const taskModelsSection = this.querySelector('#taskModelsSection');
            if (taskModelsSection) {
                taskModelsSection.style.display = e.target.checked ? 'block' : 'none';
            }
        });
    }

    async loadConfiguration() {
        const loading = this.querySelector('#configLoading');
        const form = this.querySelector('#configForm');

        try {
            const response = await fetch('/api/v1/config', {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();
                // API returns wrapped response: { success: true, data: {...} }
                this.config = apiResponse.data;
                await this.loadAvailableModels();
                this.populateForm();

                loading.style.display = 'none';
                form.style.display = 'block';
            } else {
                loading.textContent = 'Failed to load configuration';
            }
        } catch (error) {
            console.error('Failed to load configuration:', error);
            loading.textContent = 'Failed to load configuration';
        }
    }

    async loadAvailableModels() {
        // No-op: We use text input for model names instead of dropdown
    }

    loadModelsForProvider(provider) {
        // No-op: We use text input for model names instead of dropdown
    }

    populateForm() {
        if (!this.config) return;

        const providerSelect = this.querySelector('#defaultProvider');
        const defaultModelInput = this.querySelector('#defaultModel');

        // Set default provider
        if (this.config.defaultProvider) {
            providerSelect.value = this.config.defaultProvider;
        }

        // Set current model
        if (this.config.currentModel) {
            defaultModelInput.value = this.config.currentModel;
        }

        // Set auto model selection checkbox
        const autoCheckbox = this.querySelector('#useAutoModelSelection');
        if (this.config.autoModelSelectionEnabled !== undefined) {
            autoCheckbox.checked = this.config.autoModelSelectionEnabled;
        }

        // Show/hide task models section
        const taskModelsSection = this.querySelector('#taskModelsSection');
        if (taskModelsSection) {
            taskModelsSection.style.display = this.config.autoModelSelectionEnabled ? 'block' : 'none';
        }

        // Populate task models
        this.renderTaskModels();
    }

    renderTaskModels() {
        const container = this.querySelector('#taskModelsContainer');
        if (!container || !this.config) return;

        const configuredProviders = Object.keys(this.config.llmConfigs || {});

        // Group tasks by category like in setup wizard
        container.innerHTML = `
            <div class="task-models-section">
                <h4>📝 Text & Reasoning Tasks</h4>
                ${this.renderTaskModelItem(this.taskTypes[0], configuredProviders)}
                ${this.renderTaskModelItem(this.taskTypes[1], configuredProviders)}

                <h4>🎤 Audio Tasks</h4>
                <small class="form-hint">💡 Recommended: OpenAI Whisper for STT</small>
                ${this.renderTaskModelItem(this.taskTypes[2], configuredProviders)}
                ${this.renderTaskModelItem(this.taskTypes[3], configuredProviders)}

                <h4>🖼️ Image Tasks</h4>
                <small class="form-hint">💡 Analysis: GPT-4 Vision, Claude 3.5; Generation: DALL-E</small>
                ${this.renderTaskModelItem(this.taskTypes[4], configuredProviders)}
                ${this.renderTaskModelItem(this.taskTypes[5], configuredProviders)}

                <h4>🎬 Video Tasks</h4>
                ${this.renderTaskModelItem(this.taskTypes[6], configuredProviders)}
                ${this.renderTaskModelItem(this.taskTypes[7], configuredProviders)}
            </div>
        `;
    }

    renderTaskModelItem(taskType, configuredProviders) {
        const taskModel = this.config.taskModels?.[taskType.key];

        return `
            <div class="task-model-item" data-task="${taskType.key}">
                <div class="task-model-header">
                    <strong>${taskType.name}</strong>
                    <small>${taskType.desc}</small>
                </div>
                <div class="task-model-controls">
                    <select class="form-input task-provider" data-task="${taskType.key}">
                        <option value="">Use default provider</option>
                        ${configuredProviders.map(provider => `
                            <option value="${provider}" ${taskModel?.provider === provider ? 'selected' : ''}>
                                ${this.getProviderIcon(provider)} ${provider}
                            </option>
                        `).join('')}
                    </select>
                    <input type="text"
                           class="form-input task-model"
                           data-task="${taskType.key}"
                           placeholder="Model name (optional)"
                           value="${taskModel?.model || ''}">
                </div>
            </div>
        `;
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

        return suggestions[taskType]?.[provider] || 'Leave empty for provider default';
    }

    async saveConfiguration() {
        const provider = this.querySelector('#defaultProvider').value;
        const model = this.querySelector('#defaultModel').value;
        const useAutoModel = this.querySelector('#useAutoModelSelection').checked;

        if (!provider || !model) {
            showToast('Please select both provider and model', 'error');
            return;
        }

        try {
            // Update default provider
            let response = await fetch('/api/v1/config/provider', {
                method: 'PUT',
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ provider })
            });

            if (!response.ok) {
                throw new Error('Failed to update provider');
            }

            // Update default model
            response = await fetch('/api/v1/config/model', {
                method: 'PUT',
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ model })
            });

            if (!response.ok) {
                throw new Error('Failed to update model');
            }

            // Update auto model selection if changed
            if (useAutoModel !== this.config.autoModelSelectionEnabled) {
                response = await fetch('/api/v1/config/auto-model', {
                    method: 'PUT',
                    headers: {
                        ...getAuthHeaders(),
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ enabled: useAutoModel })
                });

                if (!response.ok) {
                    console.warn('Failed to update auto model selection');
                }
            }

            // Update task-specific models if auto-model is enabled
            if (useAutoModel) {
                const taskModelUpdates = {};

                // Collect all task model configurations
                this.taskTypes.forEach(taskType => {
                    const providerSelect = this.querySelector(`.task-provider[data-task="${taskType.key}"]`);
                    const modelInput = this.querySelector(`.task-model[data-task="${taskType.key}"]`);

                    if (providerSelect && modelInput) {
                        const taskProvider = providerSelect.value;
                        const taskModel = modelInput.value.trim();

                        // Only include if provider is selected
                        if (taskProvider) {
                            // Get default model from provider config if not specified
                            const providerConfig = this.config.llmConfigs?.[taskProvider];
                            const defaultProviderModel = providerConfig?.model || '';

                            taskModelUpdates[taskType.key] = {
                                provider: taskProvider,
                                model: taskModel || defaultProviderModel
                            };
                        }
                    }
                });

                // Save task models
                response = await fetch('/api/v1/config/task-models', {
                    method: 'PUT',
                    headers: {
                        ...getAuthHeaders(),
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ taskModels: taskModelUpdates })
                });

                if (!response.ok) {
                    console.warn('Failed to update task-specific models');
                }
            }

            showToast('Configuration saved successfully');
            closeConfigModal();

            // Reload to show updated values
            setTimeout(() => {
                this.loadConfiguration();
            }, 500);

        } catch (error) {
            console.error('Failed to save configuration:', error);
            showToast('Failed to save configuration', 'error');
        }
    }
}

customElements.define('config-editor', ConfigEditor);

// Export for global access
window.configEditor = null;
document.addEventListener('DOMContentLoaded', () => {
    window.configEditor = document.querySelector('config-editor');
});
