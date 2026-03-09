/**
 * Setup Wizard Component
 *
 * A multi-step wizard for first-time T1 Super AI setup that guides users through:
 * 1. Welcome and introduction
 * 2. User profile setup
 * 3. Agent creation and configuration
 * 4. Completion and activation
 *
 * This component is automatically shown when no USER.md exists in the workspace.
 */
class SetupWizard extends HTMLElement {
    constructor() {
        super();
        this.currentStep = 1;
        this.totalSteps = 5;
        this.formData = {
            // LLM Configuration - Multiple providers
            providers: {
                OPENAI: { enabled: false, apiKey: '', baseUrl: '', model: '' },
                ANTHROPIC: { enabled: false, apiKey: '', baseUrl: '', model: '' },
                OLLAMA: { enabled: false, baseUrl: '', model: '' }
            },
            defaultProvider: '',
            configureTaskModels: false,

            // Task-specific models
            taskModels: {
                GENERAL_KNOWLEDGE: { provider: '', model: '' },
                CODING: { provider: '', model: '' },
                TEXT_TO_SPEECH: { provider: '', model: '' },
                SPEECH_TO_TEXT: { provider: '', model: '' },
                IMAGE_ANALYSIS: { provider: '', model: '' },
                IMAGE_GENERATION: { provider: '', model: '' },
                VIDEO_ANALYSIS: { provider: '', model: '' },
                VIDEO_GENERATION: { provider: '', model: '' }
            },

            // User data
            userName: '',
            userPronouns: 'they/them',
            userWorkFocus: 'Software Development',

            // Agent data
            agentName: '',
            agentRole: 'AI Assistant',
            agentPurpose: 'Help with development tasks',
            agentSpecialization: 'General purpose assistance',
            agentPersonality: 'Professional and helpful',
            communicationStyle: 'Clear and concise',
            emojiPreference: 'MODERATE'
        };
    }

    connectedCallback() {
        this.render();
        this.attachEventListeners();
    }

    render() {
        this.innerHTML = `
            <div class="wizard-overlay" id="wizardOverlay">
                <div class="wizard-container">
                    <div class="wizard-header">
                        <h1 class="wizard-title">🥚 Welcome to T1 Super AI</h1>
                        <div class="wizard-progress">
                            <div class="progress-bar">
                                <div class="progress-fill" id="progressFill" style="width: 25%"></div>
                            </div>
                            <div class="progress-text">Step <span id="currentStepNum">1</span> of ${this.totalSteps}</div>
                        </div>
                    </div>

                    <div class="wizard-body" id="wizardBody">
                        ${this.getStepContent(this.currentStep)}
                    </div>

                    <div class="wizard-footer">
                        <button class="btn btn-secondary" id="prevBtn" style="display: none;" onclick="window.setupWizard.previousStep()">
                            ← Previous
                        </button>
                        <button class="btn btn-primary" id="nextBtn" onclick="window.setupWizard.nextStep()">
                            Next →
                        </button>
                        <button class="btn btn-success" id="completeBtn" style="display: none;" onclick="window.setupWizard.completeSetup()">
                            🚀 Complete Setup
                        </button>
                    </div>
                </div>
            </div>
        `;
    }

    getStepContent(step) {
        switch(step) {
            case 1:
                return this.getWelcomeStep();
            case 2:
                return this.getLLMConfigStep();
            case 3:
                return this.getUserProfileStep();
            case 4:
                return this.getAgentConfigStep();
            case 5:
                return this.getConfirmationStep();
            default:
                return '';
        }
    }

    getWelcomeStep() {
        return `
            <div class="wizard-step" id="step1">
                <div class="welcome-content">
                    <div class="welcome-icon">🎉</div>
                    <h2>Let's Get Started!</h2>
                    <p class="welcome-text">
                        This appears to be your first time running T1 Super AI.
                        This wizard will help you set up your personalized AI assistant in just a few steps.
                    </p>

                    <div class="info-box">
                        <h3>What We'll Set Up:</h3>
                        <ul class="setup-checklist">
                            <li>✓ LLM provider configuration</li>
                            <li>✓ Your user profile</li>
                            <li>✓ Your first AI agent</li>
                            <li>✓ Agent personality and behavior</li>
                            <li>✓ Communication preferences</li>
                        </ul>
                    </div>

                    <div class="info-box info-box-tip">
                        <strong>💡 Tip:</strong> You can create additional agents later from the main interface.
                        Each agent can have a unique personality and specialization!
                    </div>
                </div>
            </div>
        `;
    }

    getLLMConfigStep() {
        return `
            <div class="wizard-step" id="step2">
                <h2>🤖 Configure LLM Providers</h2>
                <p class="step-description">
                    Configure one or more AI providers. You can use different providers for different tasks.
                </p>

                <div class="info-box info-box-tip">
                    <strong>💡 Tip:</strong> Configure multiple providers for flexibility. You can use OpenAI for coding,
                    Claude for writing, and Ollama for offline use!
                </div>

                <!-- Provider Selection with Inline Configuration -->
                <div class="form-group">
                    <label class="form-label">Select Providers <span class="required">*</span></label>

                    <!-- OpenAI Provider -->
                    <div class="provider-section">
                        <label class="checkbox-label">
                            <input type="checkbox" id="enableOpenAI"
                                ${this.formData.providers.OPENAI.enabled ? 'checked' : ''}
                                onchange="window.setupWizard.toggleProvider('OPENAI')">
                            <span>🟢 OpenAI (GPT-4, GPT-3.5)</span>
                        </label>
                        <div id="openaiConfig" class="provider-config" style="display: none;">
                            <div class="form-group">
                                <label for="openaiApiKey" class="form-label">
                                    API Key <span class="required">*</span>
                                </label>
                                <input type="password" id="openaiApiKey" class="form-input"
                                    placeholder="sk-..." value="${this.formData.providers.OPENAI.apiKey || ''}">
                                <small class="form-hint">Get your API key from <a href="https://platform.openai.com/api-keys" target="_blank">platform.openai.com/api-keys</a></small>
                            </div>
                            <div class="form-group">
                                <label for="openaiModel" class="form-label">Model (optional)</label>
                                <input type="text" id="openaiModel" class="form-input"
                                    placeholder="gpt-4o (default)" value="${this.formData.providers.OPENAI.model || ''}">
                            </div>
                        </div>
                    </div>

                    <!-- Anthropic Provider -->
                    <div class="provider-section">
                        <label class="checkbox-label">
                            <input type="checkbox" id="enableAnthropic"
                                ${this.formData.providers.ANTHROPIC.enabled ? 'checked' : ''}
                                onchange="window.setupWizard.toggleProvider('ANTHROPIC')">
                            <span>🟣 Anthropic (Claude)</span>
                        </label>
                        <div id="anthropicConfig" class="provider-config" style="display: none;">
                            <div class="form-group">
                                <label for="anthropicApiKey" class="form-label">
                                    API Key <span class="required">*</span>
                                </label>
                                <input type="password" id="anthropicApiKey" class="form-input"
                                    placeholder="sk-ant-..." value="${this.formData.providers.ANTHROPIC.apiKey || ''}">
                                <small class="form-hint">Get your API key from <a href="https://console.anthropic.com/settings/keys" target="_blank">console.anthropic.com/settings/keys</a></small>
                            </div>
                            <div class="form-group">
                                <label for="anthropicModel" class="form-label">Model (optional)</label>
                                <input type="text" id="anthropicModel" class="form-input"
                                    placeholder="claude-3-5-sonnet-20241022 (default)" value="${this.formData.providers.ANTHROPIC.model || ''}">
                            </div>
                        </div>
                    </div>

                    <!-- Ollama Provider -->
                    <div class="provider-section">
                        <label class="checkbox-label">
                            <input type="checkbox" id="enableOllama"
                                ${this.formData.providers.OLLAMA.enabled ? 'checked' : ''}
                                onchange="window.setupWizard.toggleProvider('OLLAMA')">
                            <span>🦙 Ollama (Local Models)</span>
                        </label>
                        <div id="ollamaConfig" class="provider-config" style="display: none;">
                            <div class="form-group">
                                <label for="ollamaBaseUrl" class="form-label">Base URL</label>
                                <input type="text" id="ollamaBaseUrl" class="form-input"
                                    placeholder="http://localhost:11434" value="${this.formData.providers.OLLAMA.baseUrl || ''}">
                                <small class="form-hint">Download Ollama from <a href="https://ollama.ai" target="_blank">ollama.ai</a></small>
                            </div>
                            <div class="form-group">
                                <label for="ollamaModel" class="form-label">Model (optional)</label>
                                <input type="text" id="ollamaModel" class="form-input"
                                    placeholder="llama3.2 (default)" value="${this.formData.providers.OLLAMA.model || ''}">
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Default Provider Selection -->
                <div id="defaultProviderGroup" class="form-group" style="display: ${this.hasEnabledProviders() ? 'block' : 'none'};">
                    <label for="defaultProvider" class="form-label">
                        Default Provider <span class="required">*</span>
                    </label>
                    <select id="defaultProvider" class="form-input" onchange="window.setupWizard.onDefaultProviderChange()">
                        ${this.renderDefaultProviderOptions()}
                    </select>
                    <small class="form-hint">This provider will be used by default for all tasks</small>
                </div>

                <!-- Task-Specific Models Configuration -->
                <div class="form-group">
                    <label class="checkbox-label">
                        <input type="checkbox" id="configureTaskModels"
                            ${this.formData.configureTaskModels ? 'checked' : ''}
                            onchange="window.setupWizard.toggleTaskModels()">
                        <span>⚙️ Configure task-specific models (optional)</span>
                    </label>
                    <small class="form-hint">Use different providers/models for different task types</small>
                </div>

                <div id="taskModelsConfig" style="display: none;">
                    <div class="task-models-section">
                        <h4>📝 Text & Reasoning Tasks</h4>
                        ${this.renderTaskModelConfig('GENERAL_KNOWLEDGE', 'General Knowledge', 'General purpose tasks, Q&A, reasoning')}
                        ${this.renderTaskModelConfig('CODING', 'Coding', 'Code generation, debugging, refactoring')}

                        <h4>🎤 Audio Tasks</h4>
                        <small class="form-hint">💡 Recommended: OpenAI Whisper for STT</small>
                        ${this.renderTaskModelConfig('SPEECH_TO_TEXT', 'Speech-to-Text (STT)', 'Audio transcription')}
                        ${this.renderTaskModelConfig('TEXT_TO_SPEECH', 'Text-to-Speech (TTS)', 'Convert text to audio')}

                        <h4>🖼️ Image Tasks</h4>
                        <small class="form-hint">💡 Analysis: GPT-4 Vision, Claude 3.5; Generation: DALL-E</small>
                        ${this.renderTaskModelConfig('IMAGE_ANALYSIS', 'Image Analysis', 'Image understanding and vision')}
                        ${this.renderTaskModelConfig('IMAGE_GENERATION', 'Image Generation', 'Create images from text')}

                        <h4>🎬 Video Tasks</h4>
                        ${this.renderTaskModelConfig('VIDEO_ANALYSIS', 'Video Analysis', 'Video understanding')}
                        ${this.renderTaskModelConfig('VIDEO_GENERATION', 'Video Generation', 'Create/edit videos')}
                    </div>
                </div>
            </div>
        `;
    }

    getDefaultBaseUrl(provider) {
        switch(provider) {
            case 'OPENAI':
                return 'https://api.openai.com/v1';
            case 'ANTHROPIC':
                return 'https://api.anthropic.com';
            case 'OLLAMA':
                return 'http://localhost:11434';
            default:
                return '';
        }
    }

    getDefaultModel(provider) {
        switch(provider) {
            case 'OPENAI':
                return 'gpt-4o';
            case 'ANTHROPIC':
                return 'claude-3-5-sonnet-20241022';
            case 'OLLAMA':
                return 'llama3';
            default:
                return '';
        }
    }

    getProviderInstructions(provider) {
        switch(provider) {
            case 'OPENAI':
                return '<strong>OpenAI:</strong> Get your API key from <a href="https://platform.openai.com/api-keys" target="_blank">platform.openai.com/api-keys</a>';
            case 'ANTHROPIC':
                return '<strong>Anthropic:</strong> Get your API key from <a href="https://console.anthropic.com/settings/keys" target="_blank">console.anthropic.com/settings/keys</a>';
            case 'OLLAMA':
                return '<strong>Ollama:</strong> No API key needed! Install Ollama from <a href="https://ollama.ai" target="_blank">ollama.ai</a> and run it locally.';
            default:
                return '';
        }
    }

    toggleProvider(provider) {
        // Get checkbox state BEFORE re-rendering
        const checkboxId = provider === 'OPENAI' ? 'enableOpenAI' :
                          provider === 'ANTHROPIC' ? 'enableAnthropic' :
                          'enableOllama';

        const checkbox = document.getElementById(checkboxId);
        if (!checkbox) return;

        // Update formData
        this.formData.providers[provider].enabled = checkbox.checked;

        // Save current form values before re-rendering
        this.saveStep2FormValues();

        // Re-render the step to update everything
        this.updateWizard();
    }

    saveStep2FormValues() {
        // Save LLM provider configs
        if (this.formData.providers.OPENAI.enabled) {
            this.formData.providers.OPENAI.apiKey = document.getElementById('openaiApiKey')?.value || '';
            this.formData.providers.OPENAI.model = document.getElementById('openaiModel')?.value || '';
        }
        if (this.formData.providers.ANTHROPIC.enabled) {
            this.formData.providers.ANTHROPIC.apiKey = document.getElementById('anthropicApiKey')?.value || '';
            this.formData.providers.ANTHROPIC.model = document.getElementById('anthropicModel')?.value || '';
        }
        if (this.formData.providers.OLLAMA.enabled) {
            this.formData.providers.OLLAMA.baseUrl = document.getElementById('ollamaBaseUrl')?.value || '';
            this.formData.providers.OLLAMA.model = document.getElementById('ollamaModel')?.value || '';
        }

        // Save default provider
        const defaultSelect = document.getElementById('defaultProvider');
        if (defaultSelect && defaultSelect.value) {
            this.formData.defaultProvider = defaultSelect.value;
        }

        // Save task models checkbox
        const taskModelsCheckbox = document.getElementById('configureTaskModels');
        if (taskModelsCheckbox) {
            this.formData.configureTaskModels = taskModelsCheckbox.checked;
        }
    }

    onDefaultProviderChange() {
        const select = document.getElementById('defaultProvider');
        if (select && select.value) {
            this.formData.defaultProvider = select.value;
        }
    }


    hasEnabledProviders() {
        return this.formData.providers.OPENAI.enabled ||
               this.formData.providers.ANTHROPIC.enabled ||
               this.formData.providers.OLLAMA.enabled;
    }

    renderDefaultProviderOptions() {
        const enabledProviders = [];
        if (this.formData.providers.OPENAI.enabled) enabledProviders.push('OPENAI');
        if (this.formData.providers.ANTHROPIC.enabled) enabledProviders.push('ANTHROPIC');
        if (this.formData.providers.OLLAMA.enabled) enabledProviders.push('OLLAMA');

        console.log('[renderDefaultProviderOptions] Enabled providers:', enabledProviders);
        console.log('[renderDefaultProviderOptions] Current formData.defaultProvider:', this.formData.defaultProvider);

        if (enabledProviders.length === 0) {
            return '<option value="">Please enable at least one provider above</option>';
        }

        // Auto-select first if none selected
        if (!this.formData.defaultProvider || !enabledProviders.includes(this.formData.defaultProvider)) {
            this.formData.defaultProvider = enabledProviders[0];
            console.log('[renderDefaultProviderOptions] Auto-selected:', this.formData.defaultProvider);
        }

        let html = '';
        enabledProviders.forEach(provider => {
            const selected = this.formData.defaultProvider === provider ? 'selected' : '';
            const name = provider === 'OPENAI' ? '🟢 OpenAI' :
                        provider === 'ANTHROPIC' ? '🟣 Anthropic' :
                        '🦙 Ollama';
            html += `<option value="${provider}" ${selected}>${name}</option>`;
            console.log('[renderDefaultProviderOptions] Added option:', provider, 'selected:', selected);
        });

        console.log('[renderDefaultProviderOptions] Final HTML:', html);
        return html;
    }

    toggleTaskModels() {
        const checkbox = document.getElementById('configureTaskModels');
        const config = document.getElementById('taskModelsConfig');

        if (checkbox) {
            this.formData.configureTaskModels = checkbox.checked;
        }

        if (config) {
            config.style.display = this.formData.configureTaskModels ? 'block' : 'none';
        }
    }

    renderTaskModelConfig(taskType, displayName, description) {
        const enabledProviders = this.getEnabledProvidersList();
        const currentSelection = this.formData.taskModels[taskType];

        return `
            <div class="task-model-item">
                <div class="task-model-header">
                    <strong>${displayName}</strong>
                    <small>${description}</small>
                </div>
                <div class="task-model-controls">
                    <select id="taskModel_${taskType}_provider" class="form-input"
                        onchange="window.setupWizard.updateTaskModelProvider('${taskType}')">
                        <option value="">Use default provider</option>
                        ${enabledProviders.map(p => `<option value="${p}" ${currentSelection.provider === p ? 'selected' : ''}>${this.getProviderDisplayName(p)}</option>`).join('')}
                    </select>
                    <input type="text" id="taskModel_${taskType}_model" class="form-input"
                        placeholder="Model name (optional)"
                        value="${currentSelection.model || ''}"
                        onchange="window.setupWizard.updateTaskModelModel('${taskType}')">
                </div>
            </div>
        `;
    }

    getEnabledProvidersList() {
        const providers = [];
        if (this.formData.providers.OPENAI.enabled) providers.push('OPENAI');
        if (this.formData.providers.ANTHROPIC.enabled) providers.push('ANTHROPIC');
        if (this.formData.providers.OLLAMA.enabled) providers.push('OLLAMA');
        return providers;
    }

    getProviderDisplayName(provider) {
        switch(provider) {
            case 'OPENAI': return '🟢 OpenAI';
            case 'ANTHROPIC': return '🟣 Anthropic';
            case 'OLLAMA': return '🦙 Ollama';
            default: return provider;
        }
    }

    updateTaskModelProvider(taskType) {
        const select = document.getElementById(`taskModel_${taskType}_provider`);
        if (select) {
            this.formData.taskModels[taskType].provider = select.value;
        }
    }

    updateTaskModelModel(taskType) {
        const input = document.getElementById(`taskModel_${taskType}_model`);
        if (input) {
            this.formData.taskModels[taskType].model = input.value.trim();
        }
    }


    getUserProfileStep() {
        return `
            <div class="wizard-step" id="step2">
                <h2>👤 Tell Us About Yourself</h2>
                <p class="step-description">
                    This information helps your AI agents personalize their interactions with you.
                </p>

                <div class="form-group">
                    <label for="userName" class="form-label">
                        Your Name <span class="required">*</span>
                    </label>
                    <input
                        type="text"
                        id="userName"
                        class="form-input"
                        placeholder="e.g., Alex"
                        value="${this.formData.userName}"
                        required
                    >
                    <small class="form-hint">How should your agents address you?</small>
                </div>

                <div class="form-group">
                    <label for="userPronouns" class="form-label">
                        Preferred Pronouns
                    </label>
                    <select id="userPronouns" class="form-input">
                        <option value="they/them" ${this.formData.userPronouns === 'they/them' ? 'selected' : ''}>they/them</option>
                        <option value="he/him" ${this.formData.userPronouns === 'he/him' ? 'selected' : ''}>he/him</option>
                        <option value="she/her" ${this.formData.userPronouns === 'she/her' ? 'selected' : ''}>she/her</option>
                        <option value="other" ${this.formData.userPronouns === 'other' ? 'selected' : ''}>other</option>
                    </select>
                </div>

                <div class="form-group">
                    <label for="userWorkFocus" class="form-label">
                        Primary Work Focus
                    </label>
                    <input
                        type="text"
                        id="userWorkFocus"
                        class="form-input"
                        placeholder="e.g., Software Development, Data Science, Writing"
                        value="${this.formData.userWorkFocus}"
                    >
                    <small class="form-hint">What do you primarily work on? This helps agents provide relevant assistance.</small>
                </div>
            </div>
        `;
    }

    getAgentConfigStep() {
        return `
            <div class="wizard-step" id="step3">
                <h2>🤖 Configure Your First Agent</h2>
                <p class="step-description">
                    Let's create your first AI agent. You can customize its personality and purpose.
                </p>

                <div class="form-group">
                    <label for="agentName" class="form-label">
                        Agent Name <span class="required">*</span>
                    </label>
                    <input
                        type="text"
                        id="agentName"
                        class="form-input"
                        placeholder="e.g., CodeHelper, DataWizard, WritingBuddy"
                        value="${this.formData.agentName}"
                        required
                    >
                    <small class="form-hint">Choose a memorable name for your agent</small>
                </div>

                <div class="form-group">
                    <label for="agentRole" class="form-label">
                        Agent Role <span class="required">*</span>
                    </label>
                    <input
                        type="text"
                        id="agentRole"
                        class="form-input"
                        placeholder="e.g., Software Development Assistant, Code Reviewer"
                        value="${this.formData.agentRole}"
                        required
                    >
                    <small class="form-hint">What is this agent's primary role?</small>
                </div>

                <div class="form-group">
                    <label for="agentPurpose" class="form-label">
                        Purpose
                    </label>
                    <textarea
                        id="agentPurpose"
                        class="form-input form-textarea"
                        rows="3"
                        placeholder="e.g., Help with code reviews, debug issues, and suggest improvements"
                    >${this.formData.agentPurpose}</textarea>
                    <small class="form-hint">What will this agent help you with?</small>
                </div>

                <div class="form-group">
                    <label for="agentSpecialization" class="form-label">
                        Specialization
                    </label>
                    <input
                        type="text"
                        id="agentSpecialization"
                        class="form-input"
                        placeholder="e.g., Java Spring Boot, React, Python ML"
                        value="${this.formData.agentSpecialization}"
                    >
                    <small class="form-hint">Any specific technical areas of expertise?</small>
                </div>

                <div class="form-group">
                    <label for="agentPersonality" class="form-label">
                        Personality Traits
                    </label>
                    <input
                        type="text"
                        id="agentPersonality"
                        class="form-input"
                        placeholder="e.g., Professional and helpful, Friendly and encouraging"
                        value="${this.formData.agentPersonality}"
                    >
                </div>

                <div class="form-group">
                    <label for="communicationStyle" class="form-label">
                        Communication Style
                    </label>
                    <select id="communicationStyle" class="form-input">
                        <option value="Clear and concise" ${this.formData.communicationStyle === 'Clear and concise' ? 'selected' : ''}>Clear and concise</option>
                        <option value="Detailed and thorough" ${this.formData.communicationStyle === 'Detailed and thorough' ? 'selected' : ''}>Detailed and thorough</option>
                        <option value="Casual and friendly" ${this.formData.communicationStyle === 'Casual and friendly' ? 'selected' : ''}>Casual and friendly</option>
                        <option value="Technical and precise" ${this.formData.communicationStyle === 'Technical and precise' ? 'selected' : ''}>Technical and precise</option>
                    </select>
                </div>

                <div class="form-group">
                    <label for="emojiPreference" class="form-label">
                        Emoji Usage
                    </label>
                    <select id="emojiPreference" class="form-input">
                        <option value="NONE" ${this.formData.emojiPreference === 'NONE' ? 'selected' : ''}>None - Plain text only</option>
                        <option value="MINIMAL" ${this.formData.emojiPreference === 'MINIMAL' ? 'selected' : ''}>Minimal - Rare usage</option>
                        <option value="MODERATE" ${this.formData.emojiPreference === 'MODERATE' ? 'selected' : ''}>Moderate - Balanced</option>
                        <option value="ENTHUSIASTIC" ${this.formData.emojiPreference === 'ENTHUSIASTIC' ? 'selected' : ''}>Enthusiastic - Frequent</option>
                    </select>
                </div>
            </div>
        `;
    }

    getConfirmationStep() {
        console.log('Rendering confirmation step with formData:', this.formData);
        return `
            <div class="wizard-step" id="step5">
                <h2>✅ Review Your Configuration</h2>
                <p class="step-description">
                    Please review your settings before completing the setup.
                </p>

                <div class="confirmation-section">
                    <h3>LLM Configuration</h3>
                    <div class="confirmation-grid">
                        <div class="confirmation-item">
                            <span class="confirmation-label">Default Provider:</span>
                            <span class="confirmation-value">${this.formData.defaultProvider}</span>
                        </div>
                        ${this.formData.providers.OPENAI.enabled ? `
                        <div class="confirmation-item">
                            <span class="confirmation-label">🟢 OpenAI:</span>
                            <span class="confirmation-value">Configured (${this.formData.providers.OPENAI.model || 'default model'})</span>
                        </div>
                        ` : ''}
                        ${this.formData.providers.ANTHROPIC.enabled ? `
                        <div class="confirmation-item">
                            <span class="confirmation-label">🟣 Anthropic:</span>
                            <span class="confirmation-value">Configured (${this.formData.providers.ANTHROPIC.model || 'default model'})</span>
                        </div>
                        ` : ''}
                        ${this.formData.providers.OLLAMA.enabled ? `
                        <div class="confirmation-item">
                            <span class="confirmation-label">🦙 Ollama:</span>
                            <span class="confirmation-value">Configured (${this.formData.providers.OLLAMA.model || 'default model'})</span>
                        </div>
                        ` : ''}
                    </div>
                </div>

                <div class="confirmation-section">
                    <h3>Your Profile</h3>
                    <div class="confirmation-grid">
                        <div class="confirmation-item">
                            <span class="confirmation-label">Name:</span>
                            <span class="confirmation-value">${this.formData.userName || 'Not set'}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Pronouns:</span>
                            <span class="confirmation-value">${this.formData.userPronouns}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Work Focus:</span>
                            <span class="confirmation-value">${this.formData.userWorkFocus}</span>
                        </div>
                    </div>
                </div>

                <div class="confirmation-section">
                    <h3>Agent Configuration</h3>
                    <div class="confirmation-grid">
                        <div class="confirmation-item">
                            <span class="confirmation-label">Name:</span>
                            <span class="confirmation-value">${this.formData.agentName || 'Not set'}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Role:</span>
                            <span class="confirmation-value">${this.formData.agentRole}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Purpose:</span>
                            <span class="confirmation-value">${this.formData.agentPurpose}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Specialization:</span>
                            <span class="confirmation-value">${this.formData.agentSpecialization}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Personality:</span>
                            <span class="confirmation-value">${this.formData.agentPersonality}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Style:</span>
                            <span class="confirmation-value">${this.formData.communicationStyle}</span>
                        </div>
                        <div class="confirmation-item">
                            <span class="confirmation-label">Emoji Usage:</span>
                            <span class="confirmation-value">${this.formData.emojiPreference}</span>
                        </div>
                    </div>
                </div>

                <div class="info-box info-box-success">
                    <strong>🎉 Almost there!</strong> Click "Complete Setup" to create your agent and start chatting!
                </div>
            </div>
        `;
    }

    attachEventListeners() {
        // Store reference to this wizard globally for button onclick handlers
        window.setupWizard = this;
    }

    async nextStep() {
        console.log('nextStep called, current step:', this.currentStep);

        // Validate current step
        if (!this.validateCurrentStep()) {
            console.log('Validation failed for step', this.currentStep);
            return;
        }

        console.log('Validation passed for step', this.currentStep);

        // Clear any error messages
        this.clearError();

        // Save current step data
        this.saveCurrentStepData();

        if (this.currentStep < this.totalSteps) {
            this.currentStep++;
            console.log('Moving to step', this.currentStep);
            this.updateWizard();
        }
    }

    previousStep() {
        if (this.currentStep > 1) {
            this.currentStep--;
            this.updateWizard();
        }
    }

    validateCurrentStep() {
        switch(this.currentStep) {
            case 1:
                // Welcome step - no validation needed
                return true;

            case 2:
                // LLM Configuration validation
                const enabledProviders = [];
                if (document.getElementById('enableOpenAI')?.checked) enabledProviders.push('OPENAI');
                if (document.getElementById('enableAnthropic')?.checked) enabledProviders.push('ANTHROPIC');
                if (document.getElementById('enableOllama')?.checked) enabledProviders.push('OLLAMA');

                console.log('Step 2 validation - enabled providers:', enabledProviders);

                if (enabledProviders.length === 0) {
                    this.showError('Please select at least one LLM provider');
                    return false;
                }

                // Validate API keys for enabled providers
                if (enabledProviders.includes('OPENAI')) {
                    const openaiKey = document.getElementById('openaiApiKey')?.value?.trim();
                    if (!openaiKey) {
                        this.showError('Please enter your OpenAI API key');
                        return false;
                    }
                }

                if (enabledProviders.includes('ANTHROPIC')) {
                    const anthropicKey = document.getElementById('anthropicApiKey')?.value?.trim();
                    if (!anthropicKey) {
                        this.showError('Please enter your Anthropic API key');
                        return false;
                    }
                }

                // Check default provider is selected - use formData since it's the source of truth
                console.log('Validating default provider:');
                console.log('  formData.defaultProvider:', this.formData.defaultProvider);

                if (!this.formData.defaultProvider) {
                    this.showError('Please select a default provider');
                    return false;
                }

                console.log('Step 2 validation passed');
                return true;

            case 3:
                // User profile validation
                const userName = document.getElementById('userName')?.value?.trim();
                if (!userName) {
                    this.showError('Please enter your name');
                    return false;
                }
                return true;

            case 4:
                // Agent config validation
                console.log('=== STEP 3 VALIDATION START ===');
                console.log('Current wizard body HTML length:', document.getElementById('wizardBody')?.innerHTML?.length);

                // Try multiple methods to get the elements
                const agentNameEl = document.getElementById('agentName');
                const agentRoleEl = document.getElementById('agentRole');

                // Also try querySelector as backup
                const agentNameEl2 = document.querySelector('#agentName');
                const agentRoleEl2 = document.querySelector('#agentRole');

                // Check if elements are in the wizard body
                const wizardBody = document.getElementById('wizardBody');
                const agentNameInBody = wizardBody?.querySelector('#agentName');
                const agentRoleInBody = wizardBody?.querySelector('#agentRole');

                console.log('Step 3 validation - agentNameEl:', agentNameEl);
                console.log('Step 3 validation - agentNameEl2 (querySelector):', agentNameEl2);
                console.log('Step 3 validation - agentNameInBody:', agentNameInBody);
                console.log('Step 3 validation - agentNameEl exists?:', agentNameEl !== null);
                console.log('Step 3 validation - agentNameEl value:', agentNameEl?.value);
                console.log('Step 3 validation - agentNameEl value type:', typeof agentNameEl?.value);

                console.log('Step 3 validation - agentRoleEl:', agentRoleEl);
                console.log('Step 3 validation - agentRoleEl2 (querySelector):', agentRoleEl2);
                console.log('Step 3 validation - agentRoleInBody:', agentRoleInBody);
                console.log('Step 3 validation - agentRoleEl exists?:', agentRoleEl !== null);
                console.log('Step 3 validation - agentRoleEl value:', agentRoleEl?.value);

                const agentName = agentNameEl?.value?.trim();
                const agentRole = agentRoleEl?.value?.trim();

                console.log('Step 3 validation - agentName after trim:', `"${agentName}"`);
                console.log('Step 3 validation - agentName length:', agentName?.length);
                console.log('Step 3 validation - agentRole after trim:', `"${agentRole}"`);
                console.log('Step 3 validation - agentRole length:', agentRole?.length);
                console.log('Step 3 validation - !agentName:', !agentName);
                console.log('Step 3 validation - !agentRole:', !agentRole);

                // If getElementById fails, try the wizard body querySelector
                const finalAgentName = agentName || agentNameInBody?.value?.trim();
                const finalAgentRole = agentRole || agentRoleInBody?.value?.trim();

                console.log('Step 3 validation - FINAL agentName:', `"${finalAgentName}"`);
                console.log('Step 3 validation - FINAL agentRole:', `"${finalAgentRole}"`);

                if (!finalAgentName) {
                    console.error('Validation failed: agentName is empty or undefined');
                    this.showError('Please enter an agent name');
                    return false;
                }

                if (!finalAgentRole) {
                    console.error('Validation failed: agentRole is empty or undefined');
                    this.showError('Please enter an agent role');
                    return false;
                }

                // Validate agent name format (alphanumeric, spaces, underscores, and hyphens)
                if (!/^[a-zA-Z0-9_\- ]+$/.test(finalAgentName)) {
                    console.error('Validation failed: agentName contains invalid characters:', finalAgentName);
                    this.showError('Agent name can only contain letters, numbers, spaces, underscores, and hyphens');
                    return false;
                }

                console.log('Step 4 validation passed');
                return true;

            case 5:
                // Confirmation step - no validation needed
                return true;

            default:
                return true;
        }
    }

    saveCurrentStepData() {
        switch(this.currentStep) {
            case 2:
                // Save LLM configuration for each enabled provider
                this.formData.providers.OPENAI.enabled = document.getElementById('enableOpenAI')?.checked || false;
                if (this.formData.providers.OPENAI.enabled) {
                    this.formData.providers.OPENAI.apiKey = document.getElementById('openaiApiKey')?.value?.trim() || '';
                    this.formData.providers.OPENAI.model = document.getElementById('openaiModel')?.value?.trim() || '';
                    this.formData.providers.OPENAI.baseUrl = 'https://api.openai.com/v1';
                }

                this.formData.providers.ANTHROPIC.enabled = document.getElementById('enableAnthropic')?.checked || false;
                if (this.formData.providers.ANTHROPIC.enabled) {
                    this.formData.providers.ANTHROPIC.apiKey = document.getElementById('anthropicApiKey')?.value?.trim() || '';
                    this.formData.providers.ANTHROPIC.model = document.getElementById('anthropicModel')?.value?.trim() || '';
                    this.formData.providers.ANTHROPIC.baseUrl = 'https://api.anthropic.com';
                }

                this.formData.providers.OLLAMA.enabled = document.getElementById('enableOllama')?.checked || false;
                if (this.formData.providers.OLLAMA.enabled) {
                    this.formData.providers.OLLAMA.baseUrl = document.getElementById('ollamaBaseUrl')?.value?.trim() || 'http://localhost:11434';
                    this.formData.providers.OLLAMA.model = document.getElementById('ollamaModel')?.value?.trim() || '';
                }

                const defaultProviderSelect = document.getElementById('defaultProvider');
                const defaultProviderValue = defaultProviderSelect?.value;

                console.log('[saveCurrentStepData] Step 2:');
                console.log('  defaultProvider select:', defaultProviderSelect);
                console.log('  defaultProvider value from DOM:', defaultProviderValue);
                console.log('  formData.defaultProvider before:', this.formData.defaultProvider);

                // Prioritize formData if select value is empty
                this.formData.defaultProvider = defaultProviderValue || this.formData.defaultProvider || '';

                console.log('  formData.defaultProvider after:', this.formData.defaultProvider);

                // Save task models if configured
                this.formData.configureTaskModels = document.getElementById('configureTaskModels')?.checked || false;
                if (this.formData.configureTaskModels) {
                    // Task models are saved in real-time via updateTaskModelProvider/Model functions
                    // No need to save here
                }

                console.log('Saved step 2 data:', {
                    providers: this.formData.providers,
                    defaultProvider: this.formData.defaultProvider,
                    configureTaskModels: this.formData.configureTaskModels,
                    taskModels: this.formData.taskModels
                });
                break;

            case 3:
                // Save user profile
                this.formData.userName = document.getElementById('userName')?.value?.trim() || '';
                this.formData.userPronouns = document.getElementById('userPronouns')?.value || 'they/them';
                this.formData.userWorkFocus = document.getElementById('userWorkFocus')?.value?.trim() || 'Software Development';

                console.log('Saved step 3 data:', {
                    userName: this.formData.userName,
                    userPronouns: this.formData.userPronouns,
                    userWorkFocus: this.formData.userWorkFocus
                });
                break;

            case 4:
                // Use fallback to wizard body querySelector if getElementById fails
                const wizardBody = document.getElementById('wizardBody');

                this.formData.agentName = document.getElementById('agentName')?.value?.trim()
                    || wizardBody?.querySelector('#agentName')?.value?.trim() || '';
                this.formData.agentRole = document.getElementById('agentRole')?.value?.trim()
                    || wizardBody?.querySelector('#agentRole')?.value?.trim() || '';
                this.formData.agentPurpose = document.getElementById('agentPurpose')?.value?.trim()
                    || wizardBody?.querySelector('#agentPurpose')?.value?.trim() || '';
                this.formData.agentSpecialization = document.getElementById('agentSpecialization')?.value?.trim()
                    || wizardBody?.querySelector('#agentSpecialization')?.value?.trim() || '';
                this.formData.agentPersonality = document.getElementById('agentPersonality')?.value?.trim()
                    || wizardBody?.querySelector('#agentPersonality')?.value?.trim() || '';
                this.formData.communicationStyle = document.getElementById('communicationStyle')?.value
                    || wizardBody?.querySelector('#communicationStyle')?.value || 'Clear and concise';
                this.formData.emojiPreference = document.getElementById('emojiPreference')?.value
                    || wizardBody?.querySelector('#emojiPreference')?.value || 'MODERATE';

                console.log('Saved step 4 data:', {
                    agentName: this.formData.agentName,
                    agentRole: this.formData.agentRole,
                    agentPurpose: this.formData.agentPurpose,
                    agentSpecialization: this.formData.agentSpecialization,
                    agentPersonality: this.formData.agentPersonality,
                    communicationStyle: this.formData.communicationStyle,
                    emojiPreference: this.formData.emojiPreference
                });
                break;
        }
    }

    updateWizard() {
        // Update step content
        const wizardBody = document.getElementById('wizardBody');
        if (wizardBody) {
            wizardBody.innerHTML = this.getStepContent(this.currentStep);
        }

        // Update progress bar
        const progressFill = document.getElementById('progressFill');
        if (progressFill) {
            const progressPercent = (this.currentStep / this.totalSteps) * 100;
            progressFill.style.width = progressPercent + '%';
        }

        // Update step number
        const stepNum = document.getElementById('currentStepNum');
        if (stepNum) {
            stepNum.textContent = this.currentStep;
        }

        // Update buttons
        const prevBtn = document.getElementById('prevBtn');
        const nextBtn = document.getElementById('nextBtn');
        const completeBtn = document.getElementById('completeBtn');

        if (prevBtn) {
            prevBtn.style.display = this.currentStep > 1 ? 'inline-block' : 'none';
        }

        if (nextBtn && completeBtn) {
            if (this.currentStep === this.totalSteps) {
                nextBtn.style.display = 'none';
                completeBtn.style.display = 'inline-block';
            } else {
                nextBtn.style.display = 'inline-block';
                completeBtn.style.display = 'none';
            }
        }

        // Initialize step-specific UI after rendering
        if (this.currentStep === 2) {
            // Show provider config panels if already enabled
            if (this.formData.providers.OPENAI.enabled) {
                const el = document.getElementById('openaiConfig');
                if (el) el.style.display = 'block';
            }
            if (this.formData.providers.ANTHROPIC.enabled) {
                const el = document.getElementById('anthropicConfig');
                if (el) el.style.display = 'block';
            }
            if (this.formData.providers.OLLAMA.enabled) {
                const el = document.getElementById('ollamaConfig');
                if (el) el.style.display = 'block';
            }
            // Show task models if already checked
            if (this.formData.configureTaskModels) {
                const el = document.getElementById('taskModelsConfig');
                if (el) el.style.display = 'block';
            }
        }
    }

    async completeSetup() {
        // Show loading state
        const completeBtn = document.getElementById('completeBtn');
        if (completeBtn) {
            completeBtn.disabled = true;
            completeBtn.innerHTML = '⏳ Setting up...';
        }

        try {
            // Send setup data to backend
            const response = await fetch('/api/v1/setup/complete', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(this.formData)
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Setup failed');
            }

            const result = await response.json();

            // Show success message
            this.showSuccess();

            // Wait a moment, then close wizard and reload
            setTimeout(() => {
                this.closeWizard();
                window.location.reload();
            }, 2000);

        } catch (error) {
            console.error('Setup failed:', error);
            this.showError('Setup failed: ' + error.message);

            // Re-enable button
            if (completeBtn) {
                completeBtn.disabled = false;
                completeBtn.innerHTML = '🚀 Complete Setup';
            }
        }
    }

    showSuccess() {
        const wizardBody = document.getElementById('wizardBody');
        if (wizardBody) {
            wizardBody.innerHTML = `
                <div class="wizard-step">
                    <div class="success-content">
                        <div class="success-icon">🎉</div>
                        <h2>Setup Complete!</h2>
                        <p class="success-text">
                            Your agent <strong>${this.formData.agentName}</strong> has been created successfully.
                        </p>
                        <p class="success-text">
                            Redirecting to chat interface...
                        </p>
                    </div>
                </div>
            `;
        }
    }

    showError(message) {
        console.log('showError:', message);
        // Create or update error message
        let errorDiv = document.querySelector('.wizard-error');

        if (!errorDiv) {
            errorDiv = document.createElement('div');
            errorDiv.className = 'wizard-error';
            const wizardBody = document.getElementById('wizardBody');
            if (wizardBody) {
                wizardBody.insertBefore(errorDiv, wizardBody.firstChild);
            }
        }

        errorDiv.textContent = '⚠️ ' + message;
        errorDiv.style.display = 'block';

        // Auto-hide after 5 seconds
        setTimeout(() => {
            errorDiv.style.display = 'none';
        }, 5000);
    }

    clearError() {
        const errorDiv = document.querySelector('.wizard-error');
        if (errorDiv) {
            errorDiv.style.display = 'none';
        }
    }

    show() {
        const overlay = document.getElementById('wizardOverlay');
        if (overlay) {
            overlay.style.display = 'flex';
        }
    }

    closeWizard() {
        const overlay = document.getElementById('wizardOverlay');
        if (overlay) {
            overlay.style.display = 'none';
        }
    }

    /**
     * Check if setup is needed by querying the backend
     */
    static async checkIfSetupNeeded() {
        try {
            const response = await fetch('/api/v1/setup/status');
            if (response.ok) {
                const result = await response.json();
                return result.data?.needsSetup || false;
            }
            return false;
        } catch (error) {
            console.error('Failed to check setup status:', error);
            return false;
        }
    }
}

// Register the custom element
customElements.define('setup-wizard', SetupWizard);
