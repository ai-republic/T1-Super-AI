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
        this.totalSteps = 4;
        this.formData = {
            // Task-specific models (GENERAL_KNOWLEDGE is mandatory as fallback)
            taskModels: {
                GENERAL_KNOWLEDGE: { provider: '', model: '', apiKey: '', baseUrl: '' },
                CODING: { provider: '', model: '', apiKey: '', baseUrl: '' },
                TEXT_TO_SPEECH: { provider: '', model: '', apiKey: '', baseUrl: '' },
                SPEECH_TO_TEXT: { provider: '', model: '', apiKey: '', baseUrl: '' },
                IMAGE_ANALYSIS: { provider: '', model: '', apiKey: '', baseUrl: '' },
                IMAGE_GENERATION: { provider: '', model: '', apiKey: '', baseUrl: '' },
                VIDEO_ANALYSIS: { provider: '', model: '', apiKey: '', baseUrl: '' },
                VIDEO_GENERATION: { provider: '', model: '', apiKey: '', baseUrl: '' }
            },

            // User data
            userName: '',
            userPronouns: 'they/them',
            userWorkFocus: 'Software Development',

            // Team data
            teamName: '',

            // Agent data
            agentName: '',
            agentRole: 'AI Assistant',
            agentPurpose: 'Help with development tasks',
            agentSpecialization: 'General purpose assistance',
            agentPersonality: 'Professional and helpful',
            communicationStyle: 'Clear and concise',
            emojiPreference: 'MODERATE',
            guidelines: ''
        };
    }

    connectedCallback() {
        // Set default guidelines
        if (!this.formData.guidelines) {
            this.formData.guidelines = `- Always align with the user's preferences and work focus (see USER.md)
- Do not ask too many questions unless you are unsure. Solve the task yourself. Use your tools and skills. Or create the necessary plugin or tool or skill. Be creative and inventive!
- Ask if you need credentials to access resources unless they are already defined in your agent folder 'credentials.json' file.`;
        }
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
                <h2>🤖 Configure AI Models</h2>
                <p class="step-description">
                    Configure AI models for different tasks. At minimum, you must configure a <strong>General Knowledge</strong> model that will serve as the fallback for all tasks.
                </p>

                <div class="info-box info-box-tip">
                    <strong>💡 Tip:</strong> You can configure different providers and models for different tasks.
                    For example, use OpenAI GPT-4 for coding, Claude for writing, and DALL-E for image generation!
                </div>

                <div id="taskModelsConfig">
                    <div class="task-models-section">
                        <h4>📝 Text & Reasoning Tasks</h4>
                        <div class="info-box info-box-warning" style="margin-bottom: 1rem; font-size: 0.875rem;">
                            <strong>Required:</strong> Configure General Knowledge model (serves as fallback for all tasks)
                        </div>
                        ${this.renderTaskModelConfig('GENERAL_KNOWLEDGE', 'General Knowledge', 'General purpose tasks, Q&A, reasoning (REQUIRED FALLBACK)')}
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


    renderTaskModelConfig(taskType, displayName, description) {
        const currentSelection = this.formData.taskModels[taskType];
        const isRequired = taskType === 'GENERAL_KNOWLEDGE';

        return `
            <div class="task-model-item ${isRequired ? 'task-model-required' : ''}">
                <div class="task-model-header">
                    <strong>${displayName}${isRequired ? ' <span class="required">*</span>' : ''}</strong>
                    <small>${description}</small>
                </div>
                <div class="task-model-controls">
                    <div class="task-model-row">
                        <label class="form-label">Provider${isRequired ? ' <span class="required">*</span>' : ''}</label>
                        <select id="taskModel_${taskType}_provider" class="form-input"
                            onchange="window.setupWizard.updateTaskModelProvider('${taskType}')"
                            ${isRequired ? 'required' : ''}>
                            <option value="">-- Select Provider --</option>
                            <option value="OPENAI" ${currentSelection.provider === 'OPENAI' ? 'selected' : ''}>🟢 OpenAI (GPT-4, DALL-E)</option>
                            <option value="ANTHROPIC" ${currentSelection.provider === 'ANTHROPIC' ? 'selected' : ''}>🟣 Anthropic (Claude)</option>
                            <option value="OLLAMA" ${currentSelection.provider === 'OLLAMA' ? 'selected' : ''}>🦙 Ollama (Local)</option>
                        </select>
                    </div>
                    <div class="task-model-row">
                        <label class="form-label">Model Name (optional)</label>
                        <input type="text" id="taskModel_${taskType}_model" class="form-input"
                            placeholder="${this.getDefaultModel(currentSelection.provider)}"
                            value="${currentSelection.model || ''}"
                            onchange="window.setupWizard.updateTaskModelModel('${taskType}')">
                        <small class="form-hint">${this.getModelSuggestion(taskType, currentSelection.provider)}</small>
                    </div>
                    <div class="task-model-row">
                        <label class="form-label">API Key${currentSelection.provider !== 'OLLAMA' && isRequired ? ' <span class="required">*</span>' : ' (optional)'}</label>
                        <input type="password" id="taskModel_${taskType}_apiKey" class="form-input"
                            placeholder="${currentSelection.provider === 'OLLAMA' ? 'Not required for Ollama' : 'Enter API key'}"
                            value="${currentSelection.apiKey || ''}"
                            onchange="window.setupWizard.updateTaskModelApiKey('${taskType}')"
                            ${currentSelection.provider !== 'OLLAMA' && isRequired ? 'required' : ''}>
                        <small class="form-hint">${this.getProviderInstructions(currentSelection.provider)}</small>
                    </div>
                    <div class="task-model-row">
                        <label class="form-label">Base URL (optional)</label>
                        <input type="text" id="taskModel_${taskType}_baseUrl" class="form-input"
                            placeholder="${this.getDefaultBaseUrl(currentSelection.provider)}"
                            value="${currentSelection.baseUrl || ''}"
                            onchange="window.setupWizard.updateTaskModelBaseUrl('${taskType}')">
                        <small class="form-hint">Leave empty to use default</small>
                    </div>
                </div>
            </div>
        `;
    }

    getModelSuggestion(taskType, provider) {
        if (!provider) return 'Select a provider first';

        const suggestions = {
            'GENERAL_KNOWLEDGE': {
                'OPENAI': 'gpt-4o or gpt-4',
                'ANTHROPIC': 'claude-3-5-sonnet-20241022',
                'OLLAMA': 'llama3.2 or mistral'
            },
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

    updateTaskModelProvider(taskType) {
        const select = document.getElementById(`taskModel_${taskType}_provider`);
        if (select) {
            this.formData.taskModels[taskType].provider = select.value;
            // Re-render to update placeholders and suggestions
            this.updateWizard();
        }
    }

    updateTaskModelModel(taskType) {
        const input = document.getElementById(`taskModel_${taskType}_model`);
        if (input) {
            this.formData.taskModels[taskType].model = input.value.trim();
        }
    }

    updateTaskModelApiKey(taskType) {
        const input = document.getElementById(`taskModel_${taskType}_apiKey`);
        if (input) {
            this.formData.taskModels[taskType].apiKey = input.value.trim();
        }
    }

    updateTaskModelBaseUrl(taskType) {
        const input = document.getElementById(`taskModel_${taskType}_baseUrl`);
        if (input) {
            this.formData.taskModels[taskType].baseUrl = input.value.trim();
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
                <h2>👥 Set up your first team</h2>
                <p class="step-description">
                    Configure your team workspace and create your first AI agent.
                </p>

                <div class="form-group">
                    <label for="teamName" class="form-label">
                        Team Name
                    </label>
                    <input
                        type="text"
                        id="teamName"
                        class="form-input"
                        placeholder="e.g., DevOps, Frontend, DataScience (leave empty for 'Default')"
                        value="${this.formData.teamName}"
                    >
                    <small class="form-hint">
                        Your workspace will be created at: <code>~/t1-super-ai/workspaces/<span id="teamNamePreview">${this.formData.teamName || 'Default'}</span>/</code>
                    </small>
                </div>

                <div style="margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--border-color);">
                    <h3 style="margin-bottom: 16px;">🤖 Configure Your First Agent</h3>
                    <agent-config-form mode="create" id="wizardAgentConfig"></agent-config-form>
                </div>
            </div>
        `;
    }

    populateAgentConfigForm() {
        const configForm = document.getElementById('wizardAgentConfig');
        if (configForm) {
            configForm.setData({
                name: this.formData.agentName,
                role: this.formData.agentRole,
                purpose: this.formData.agentPurpose,
                specialization: this.formData.agentSpecialization,
                personality: this.formData.agentPersonality,
                communicationStyle: this.formData.communicationStyle,
                emojiPreference: this.formData.emojiPreference,
                guidelines: this.formData.guidelines
            });
        }
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
                            <span class="confirmation-label">Fallback Model:</span>
                            <span class="confirmation-value">${this.formData.taskModels.GENERAL_KNOWLEDGE.provider} / ${this.formData.taskModels.GENERAL_KNOWLEDGE.model || 'default'}</span>
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
                // Task Model Configuration validation
                // Check that GENERAL_KNOWLEDGE is configured (mandatory fallback)
                const genKnowledgeProvider = document.getElementById('taskModel_GENERAL_KNOWLEDGE_provider')?.value;
                console.log('Validating GENERAL_KNOWLEDGE provider:', genKnowledgeProvider);

                if (!genKnowledgeProvider) {
                    this.showError('Please select a provider for the General Knowledge model (required as fallback for all tasks)');
                    return false;
                }

                // Validate API key for GENERAL_KNOWLEDGE if not Ollama
                if (genKnowledgeProvider !== 'OLLAMA') {
                    const genKnowledgeApiKey = document.getElementById('taskModel_GENERAL_KNOWLEDGE_apiKey')?.value?.trim();
                    if (!genKnowledgeApiKey) {
                        this.showError('Please enter an API key for the General Knowledge model');
                        return false;
                    }
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
                // Agent config validation using the agent-config-form component
                console.log('=== STEP 4 VALIDATION START ===');

                const configForm = document.getElementById('wizardAgentConfig');
                console.log('Step 4 validation - configForm:', configForm);

                if (!configForm) {
                    console.error('Validation failed: agent-config-form not found');
                    this.showError('Configuration form not found');
                    return false;
                }

                // Use the component's built-in validation
                const isValid = configForm.validate();
                console.log('Step 4 validation - isValid:', isValid);

                if (!isValid) {
                    // The component will show its own error message
                    return false;
                }

                console.log('Step 4 validation passed');
                return true;

            default:
                return true;
        }
    }

    saveCurrentStepData() {
        switch(this.currentStep) {
            case 2:
                // Task models are saved in real-time via updateTaskModel* functions
                console.log('Saved step 2 data:', {
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
                // Save team name
                this.formData.teamName = document.getElementById('teamName')?.value?.trim() || '';

                // Get data from the agent-config-form component
                const configForm = document.getElementById('wizardAgentConfig');
                if (configForm) {
                    const agentData = configForm.getData();

                    this.formData.agentName = agentData.name;
                    this.formData.agentRole = agentData.role;
                    this.formData.agentPurpose = agentData.purpose;
                    this.formData.agentSpecialization = agentData.specialization;
                    this.formData.agentPersonality = agentData.personality;
                    this.formData.communicationStyle = agentData.style;
                    this.formData.emojiPreference = agentData.emojiPreference;
                    this.formData.guidelines = agentData.guidelines;
                }

                console.log('Saved step 4 data:', {
                    teamName: this.formData.teamName,
                    agentName: this.formData.agentName,
                    agentRole: this.formData.agentRole,
                    agentPurpose: this.formData.agentPurpose,
                    agentSpecialization: this.formData.agentSpecialization,
                    agentPersonality: this.formData.agentPersonality,
                    communicationStyle: this.formData.communicationStyle,
                    emojiPreference: this.formData.emojiPreference,
                    guidelines: this.formData.guidelines
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
        if (this.currentStep === 4) {
            // Populate agent config form with saved data
            this.populateAgentConfigForm();

            // Add event listener for team name preview
            const teamNameInput = document.getElementById('teamName');
            if (teamNameInput) {
                teamNameInput.addEventListener('input', (e) => {
                    const preview = document.getElementById('teamNamePreview');
                    if (preview) {
                        preview.textContent = e.target.value.trim() || 'Default';
                    }
                });
            }
        }
    }

    async completeSetup() {
        // Save the current step data before submitting
        this.saveCurrentStepData();

        // Show loading state
        const completeBtn = document.getElementById('completeBtn');
        if (completeBtn) {
            completeBtn.disabled = true;
            completeBtn.innerHTML = '⏳ Setting up...';
        }

        console.log('Completing setup with formData:', this.formData);

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
