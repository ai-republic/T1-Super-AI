class ConfigEditor extends HTMLElement {
    constructor() {
        super();
        this.config = null;
        this.availableModels = [];
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
                                <option value="GOOGLE">Google</option>
                                <option value="AZURE_OPENAI">Azure OpenAI</option>
                            </select>
                        </div>

                        <div class="form-group">
                            <label for="defaultModel">Default Model</label>
                            <input type="text" id="defaultModel" class="form-control" required
                                placeholder="e.g., gpt-4, claude-3-opus-20240229">
                            <small class="form-help">Enter the model name for the selected provider</small>
                        </div>
                    </div>

                    <div class="form-section">
                        <h3>Advanced Settings</h3>
                        <div class="form-group">
                            <label>
                                <input type="checkbox" id="useAutoModelSelection">
                                Enable automatic model selection
                            </label>
                            <small class="form-help">Automatically select the best model based on task type (reasoning, coding, general)</small>
                        </div>
                    </div>

                    <div class="form-actions">
                        <button type="button" class="btn btn-secondary" onclick="closeConfigModal()">Cancel</button>
                        <button type="submit" class="btn btn-primary">Save Configuration</button>
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
        if (this.config.autoModelSelectionEnabled !== undefined) {
            const autoCheckbox = this.querySelector('#useAutoModelSelection');
            autoCheckbox.checked = this.config.autoModelSelectionEnabled;
        }
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

            showToast('Configuration saved successfully');
            closeConfigModal();
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
