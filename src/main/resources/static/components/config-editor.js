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
                        <h3>🎯 Task-Specific Models</h3>
                        <div class="info-box info-box-warning" style="margin-bottom: 1rem;">
                            <strong>⚠️ Required:</strong> You must configure the <strong>General Knowledge</strong> model.
                            This model serves as the fallback for any tasks that don't have a specific model configured.
                        </div>
                        <div id="taskModelsContainer" class="task-models-section">
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

        // Populate task models only (no separate provider configuration)
        this.renderTaskModels();
    }

    renderTaskModels() {
        const container = this.querySelector('#taskModelsContainer');
        if (!container || !this.config) return;

        // Show ALL providers in dropdowns (not just configured ones)
        const allProviders = ['OPENAI', 'ANTHROPIC', 'OLLAMA'];

        // Group tasks by category like in setup wizard
        container.innerHTML = `
            <h4>📝 Text & Reasoning Tasks</h4>
            <div class="info-box info-box-warning" style="margin-bottom: 1rem; font-size: 0.875rem;">
                <strong>Required:</strong> Configure General Knowledge model (serves as fallback for all tasks)
            </div>
            ${this.renderTaskModelItem(this.taskTypes[0], allProviders)}
            ${this.renderTaskModelItem(this.taskTypes[1], allProviders)}

            <h4>🎤 Audio Tasks</h4>
            <small class="form-hint">💡 Recommended: OpenAI Whisper for STT</small>
            ${this.renderTaskModelItem(this.taskTypes[2], allProviders)}
            ${this.renderTaskModelItem(this.taskTypes[3], allProviders)}

            <h4>🖼️ Image Tasks</h4>
            <small class="form-hint">💡 Analysis: GPT-4 Vision, Claude 3.5; Generation: DALL-E</small>
            ${this.renderTaskModelItem(this.taskTypes[4], allProviders)}
            ${this.renderTaskModelItem(this.taskTypes[5], allProviders)}

            <h4>🎬 Video Tasks</h4>
            ${this.renderTaskModelItem(this.taskTypes[6], allProviders)}
            ${this.renderTaskModelItem(this.taskTypes[7], allProviders)}
        `;
    }

    renderTaskModelItem(taskType, allProviders) {
        const taskModel = this.config.taskModels?.[taskType.key];
        const isRequired = taskType.key === 'GENERAL_KNOWLEDGE';

        return `
            <div class="task-model-item ${isRequired ? 'task-model-required' : ''}" data-task="${taskType.key}">
                <div class="task-model-header">
                    <strong>${taskType.name}${isRequired ? ' <span class="required">*</span>' : ''}</strong>
                    <small>${taskType.desc}</small>
                </div>
                <div class="task-model-controls">
                    <div class="task-model-row">
                        <label class="form-label">Provider${isRequired ? ' <span class="required">*</span>' : ''}</label>
                        <select class="form-input task-provider" data-task="${taskType.key}" ${isRequired ? 'required' : ''}>
                            <option value="">-- Select Provider --</option>
                            ${allProviders.map(provider => `
                                <option value="${provider}" ${taskModel?.provider === provider ? 'selected' : ''}>
                                    ${this.getProviderIcon(provider)} ${provider}
                                </option>
                            `).join('')}
                        </select>
                    </div>
                    <div class="task-model-row">
                        <label class="form-label">Model Name</label>
                        <input type="text"
                               class="form-input task-model"
                               data-task="${taskType.key}"
                               placeholder="Model name (optional)"
                               value="${taskModel?.model || ''}">
                    </div>
                    <div class="task-model-row">
                        <label class="form-label">API Key${isRequired ? ' <span class="required">*</span>' : ''}</label>
                        <input type="password"
                               class="form-input task-apikey"
                               data-task="${taskType.key}"
                               placeholder="${isRequired ? 'Required' : 'Optional - overrides provider default'}"
                               value="${taskModel?.apiKey || ''}"
                               ${isRequired ? 'required' : ''}>
                    </div>
                    <div class="task-model-row">
                        <label class="form-label">Base URL</label>
                        <input type="text"
                               class="form-input task-baseurl"
                               data-task="${taskType.key}"
                               placeholder="Optional - uses provider default"
                               value="${taskModel?.baseUrl || ''}">
                    </div>
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
        // Validate GENERAL_KNOWLEDGE is configured
        const genKnowledgeProvider = this.querySelector('.task-provider[data-task="GENERAL_KNOWLEDGE"]')?.value;
        if (!genKnowledgeProvider) {
            showToast('Please configure the General Knowledge model (required as fallback)', 'error');
            return;
        }

        // Validate API key for GENERAL_KNOWLEDGE if not Ollama
        if (genKnowledgeProvider !== 'OLLAMA') {
            const genKnowledgeApiKey = this.querySelector('.task-apikey[data-task="GENERAL_KNOWLEDGE"]')?.value?.trim();
            if (!genKnowledgeApiKey) {
                showToast('API key is required for the General Knowledge model', 'error');
                return;
            }
        }

        try {
            const taskModelUpdates = {};

            // Collect all task model configurations
            this.taskTypes.forEach(taskType => {
                const providerSelect = this.querySelector(`.task-provider[data-task="${taskType.key}"]`);
                const modelInput = this.querySelector(`.task-model[data-task="${taskType.key}"]`);
                const apiKeyInput = this.querySelector(`.task-apikey[data-task="${taskType.key}"]`);
                const baseUrlInput = this.querySelector(`.task-baseurl[data-task="${taskType.key}"]`);

                if (providerSelect) {
                    const taskProvider = providerSelect.value;
                    const taskModel = modelInput ? modelInput.value.trim() : '';
                    const taskApiKey = apiKeyInput ? apiKeyInput.value.trim() : '';
                    const taskBaseUrl = baseUrlInput ? baseUrlInput.value.trim() : '';

                    // Only include if provider is selected
                    if (taskProvider) {
                        taskModelUpdates[taskType.key] = {
                            provider: taskProvider,
                            model: taskModel || undefined,
                            apiKey: taskApiKey || undefined,
                            baseUrl: taskBaseUrl || undefined
                        };
                    }
                }
            });

            // Save task models
            const response = await fetch('/api/v1/config/task-models', {
                method: 'PUT',
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ taskModels: taskModelUpdates })
            });

            if (!response.ok) {
                throw new Error('Failed to update task-specific models');
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
