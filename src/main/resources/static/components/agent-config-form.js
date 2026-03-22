/**
 * Reusable Agent Configuration Form Component
 *
 * This component provides a standardized form for configuring agent properties.
 * It can be used in three contexts:
 * 1. Creating new agents (create-agent-form)
 * 2. Editing existing agents (character-editor)
 * 3. Setup wizard step 4 (hatching)
 *
 * Usage:
 *   <agent-config-form mode="create|edit" read-only-name="true|false"></agent-config-form>
 *
 * Properties:
 *   - mode: "create" or "edit" (default: "create")
 *   - readOnlyName: Boolean, whether agent name should be read-only (default: false)
 *
 * Methods:
 *   - getData(): Returns object with all form values
 *   - setData(data): Populates form with provided data
 *   - validate(): Returns true if form is valid, shows errors otherwise
 *   - reset(): Clears all form fields
 */
class AgentConfigForm extends HTMLElement {
    constructor() {
        super();
        this.mode = 'create';
        this.readOnlyName = false;
        this.defaultGuidelines = `- Always align with the user's preferences and work focus (see USER.md)
- Do not ask too many questions unless you are unsure. Solve the task yourself. Use your tools and skills. Or create the necessary plugin or tool or skill. Be creative and inventive!
- Ask if you need credentials to access resources unless they are already defined in your agent folder 'credentials.json' file.`;
    }

    static get observedAttributes() {
        return ['mode', 'read-only-name'];
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (name === 'mode') {
            this.mode = newValue || 'create';
        } else if (name === 'read-only-name') {
            this.readOnlyName = newValue === 'true';
        }

        // Re-render if already connected
        if (this.isConnected) {
            this.render();
        }
    }

    connectedCallback() {
        this.mode = this.getAttribute('mode') || 'create';
        this.readOnlyName = this.getAttribute('read-only-name') === 'true';
        this.render();

        // Pre-fill guidelines in create mode
        if (this.mode === 'create') {
            const guidelinesField = this.querySelector('#agentGuidelines');
            if (guidelinesField && !guidelinesField.value) {
                guidelinesField.value = this.defaultGuidelines;
            }
        }
    }

    render() {
        const isEdit = this.mode === 'edit';
        const nameReadOnly = this.readOnlyName ? 'readonly' : '';
        const nameHint = isEdit ?
            'Changing the name will rename the agent folder and update all references' :
            'Choose a memorable name for your agent';

        this.innerHTML = `
            <div class="agent-config-form">
                <div class="form-section">
                    <h3>Basic Information</h3>

                    <div class="form-group">
                        <label for="agentName" class="form-label">
                            Agent Name <span class="required">*</span>
                        </label>
                        <input
                            type="text"
                            id="agentName"
                            class="form-control form-input"
                            placeholder="e.g., CodeHelper, DataWizard, WritingBuddy"
                            required
                            ${nameReadOnly}
                        >
                        <small class="form-hint">${nameHint}</small>
                    </div>

                    <div class="form-group">
                        <label for="agentRole" class="form-label">
                            Agent Role <span class="required">*</span>
                        </label>
                        <input
                            type="text"
                            id="agentRole"
                            class="form-control form-input"
                            placeholder="e.g., Software Development Assistant, Code Reviewer"
                            required
                        >
                        <small class="form-hint">What is this agent's primary role?</small>
                    </div>

                    <div class="form-group">
                        <label for="agentPurpose" class="form-label">Purpose</label>
                        <textarea
                            id="agentPurpose"
                            class="form-control form-input form-textarea"
                            rows="3"
                            placeholder="e.g., Help with code reviews, debug issues, and suggest improvements"
                        ></textarea>
                        <small class="form-hint">What will this agent help you with?</small>
                    </div>

                    <div class="form-group">
                        <label for="agentSpecialization" class="form-label">Specialization</label>
                        <textarea
                            id="agentSpecialization"
                            class="form-control form-input form-textarea"
                            rows="2"
                            placeholder="e.g., Java Spring Boot, React, Python ML"
                        ></textarea>
                        <small class="form-hint">Any specific technical areas of expertise?</small>
                    </div>
                </div>

                ${isEdit ? this.renderProviderModelSection() : ''}

                <div class="form-section">
                    <h3>Personality & Communication</h3>

                    <div class="form-group">
                        <label for="agentPersonality" class="form-label">
                            Personality Traits
                        </label>
                        <textarea
                            id="agentPersonality"
                            class="form-control form-input form-textarea"
                            rows="2"
                            placeholder="e.g., Professional and helpful, Friendly and encouraging"
                        ></textarea>
                        <small class="form-hint">Describe personality characteristics</small>
                    </div>

                    <div class="form-group">
                        <label for="agentStyle" class="form-label">
                            Communication Style
                        </label>
                        <select id="agentStyle" class="form-control form-input">
                            <option value="Clear and concise">Clear and concise</option>
                            <option value="Detailed and thorough">Detailed and thorough</option>
                            <option value="Casual and friendly">Casual and friendly</option>
                            <option value="Technical and precise">Technical and precise</option>
                        </select>
                        <small class="form-hint">How should this agent communicate?</small>
                    </div>

                    <div class="form-group">
                        <label for="agentEmoji" class="form-label">
                            Emoji Usage
                        </label>
                        <select id="agentEmoji" class="form-control form-input">
                            <option value="NONE">None - Plain text only</option>
                            <option value="MINIMAL">Minimal - Rare usage</option>
                            <option value="MODERATE">Moderate - Balanced</option>
                            <option value="ENTHUSIASTIC">Enthusiastic - Frequent</option>
                        </select>
                        <small class="form-hint">How often should the agent use emojis?</small>
                    </div>
                </div>

                <div class="form-section">
                    <h3>Guidelines & Constraints</h3>

                    <div class="form-group">
                        <label for="agentGuidelines" class="form-label">
                            Additional Guidelines
                        </label>
                        <textarea
                            id="agentGuidelines"
                            class="form-control form-input form-textarea"
                            rows="5"
                            placeholder="Enter guidelines for your agent..."
                        ></textarea>
                        <small class="form-hint">Provide specific guidelines or rules for how this agent should behave</small>
                    </div>
                </div>
            </div>
        `;
    }

    renderProviderModelSection() {
        return `
            <div class="form-section">
                <h3>Provider & Model (Optional)</h3>
                <p class="form-hint">Override default provider/model for this agent</p>

                <div class="form-row">
                    <div class="form-group">
                        <label for="agentProvider" class="form-label">Provider</label>
                        <select id="agentProvider" class="form-control form-input">
                            <option value="">Use Default</option>
                            <option value="OPENAI">OpenAI</option>
                            <option value="ANTHROPIC">Anthropic</option>
                            <option value="OLLAMA">Ollama</option>
                            <option value="GOOGLE">Google</option>
                            <option value="AZURE_OPENAI">Azure OpenAI</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label for="agentModel" class="form-label">Model</label>
                        <input
                            type="text"
                            id="agentModel"
                            class="form-control form-input"
                            placeholder="e.g., gpt-4, claude-3-opus"
                        >
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Get all form data
     * @returns {Object} Form data
     */
    getData() {
        const data = {
            name: this.querySelector('#agentName')?.value?.trim() || '',
            role: this.querySelector('#agentRole')?.value?.trim() || '',
            purpose: this.querySelector('#agentPurpose')?.value?.trim() || '',
            specialization: this.querySelector('#agentSpecialization')?.value?.trim() || '',
            personality: this.querySelector('#agentPersonality')?.value?.trim() || '',
            style: this.querySelector('#agentStyle')?.value || 'Clear and concise',
            emojiPreference: this.querySelector('#agentEmoji')?.value || 'MODERATE',
            guidelines: this.querySelector('#agentGuidelines')?.value?.trim() || ''
        };

        // Include provider/model if in edit mode
        if (this.mode === 'edit') {
            const provider = this.querySelector('#agentProvider')?.value || '';
            const model = this.querySelector('#agentModel')?.value?.trim() || '';

            data.provider = provider || null;
            data.model = model || null;
        }

        return data;
    }

    /**
     * Set form data
     * @param {Object} data - Data to populate form with
     */
    setData(data) {
        if (!data) return;

        const fields = {
            agentName: data.name || '',
            agentRole: data.role || data.agentRole || '',
            agentPurpose: data.purpose || data.agentPurpose || '',
            agentSpecialization: data.specialization || data.agentSpecialization || '',
            agentPersonality: data.personality || data.agentPersonality || '',
            agentStyle: data.style || data.communicationStyle || 'Clear and concise',
            agentEmoji: data.emojiPreference || 'MODERATE',
            agentGuidelines: data.guidelines || ''
        };

        // Set provider/model if in edit mode
        if (this.mode === 'edit') {
            fields.agentProvider = data.provider || '';
            fields.agentModel = data.model || '';
        }

        Object.entries(fields).forEach(([id, value]) => {
            const element = this.querySelector(`#${id}`);
            if (element) {
                element.value = value;
            }
        });
    }

    /**
     * Validate form data
     * @returns {boolean} True if valid
     */
    validate() {
        const data = this.getData();

        if (!data.name) {
            this.showError('Agent name is required');
            this.querySelector('#agentName')?.focus();
            return false;
        }

        // Validate agent name format (alphanumeric, spaces, underscores, and hyphens)
        if (!/^[a-zA-Z0-9_\- ]+$/.test(data.name)) {
            this.showError('Agent name can only contain letters, numbers, spaces, underscores, and hyphens');
            this.querySelector('#agentName')?.focus();
            return false;
        }

        if (!data.role) {
            this.showError('Agent role is required');
            this.querySelector('#agentRole')?.focus();
            return false;
        }

        this.clearError();
        return true;
    }

    /**
     * Reset form to empty state
     */
    reset() {
        const form = this.querySelector('.agent-config-form');
        if (form) {
            const inputs = form.querySelectorAll('input, textarea, select');
            inputs.forEach(input => {
                if (input.type === 'checkbox') {
                    input.checked = false;
                } else {
                    input.value = '';
                }
            });
        }

        // Set defaults
        const styleSelect = this.querySelector('#agentStyle');
        if (styleSelect) styleSelect.value = 'Clear and concise';

        const emojiSelect = this.querySelector('#agentEmoji');
        if (emojiSelect) emojiSelect.value = 'MODERATE';

        this.clearError();
    }

    /**
     * Show error message
     * @param {string} message - Error message
     */
    showError(message) {
        // Dispatch event so parent can handle it
        this.dispatchEvent(new CustomEvent('validation-error', {
            detail: { message },
            bubbles: true
        }));
    }

    /**
     * Clear error message
     */
    clearError() {
        this.dispatchEvent(new CustomEvent('validation-clear', {
            bubbles: true
        }));
    }
}

customElements.define('agent-config-form', AgentConfigForm);
