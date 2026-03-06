class CharacterEditor extends HTMLElement {
    constructor() {
        super();
        this.agentDetails = null;
    }

    connectedCallback() {
        this.render();
    }

    render() {
        this.innerHTML = `
            <div class="character-editor">
                <form id="characterForm">
                    <div class="form-section">
                        <h3>Basic Information</h3>
                        <div class="form-group">
                            <label for="agentName">Agent Name *</label>
                            <input type="text" id="agentName" class="form-control" required readonly>
                        </div>

                        <div class="form-group">
                            <label for="agentRole">Role</label>
                            <input type="text" id="agentRole" class="form-control" placeholder="e.g., Software Developer, Data Analyst">
                        </div>

                        <div class="form-group">
                            <label for="agentPurpose">Purpose</label>
                            <textarea id="agentPurpose" class="form-control" rows="3"
                                placeholder="What is this agent designed to do?"></textarea>
                        </div>

                        <div class="form-group">
                            <label for="agentSpecialization">Specialization</label>
                            <textarea id="agentSpecialization" class="form-control" rows="3"
                                placeholder="What are this agent's areas of expertise?"></textarea>
                        </div>
                    </div>

                    <div class="form-section">
                        <h3>Provider & Model</h3>
                        <div class="form-row">
                            <div class="form-group">
                                <label for="agentProvider">Provider</label>
                                <select id="agentProvider" class="form-control">
                                    <option value="">Use Default</option>
                                    <option value="OPENAI">OpenAI</option>
                                    <option value="ANTHROPIC">Anthropic</option>
                                    <option value="OLLAMA">Ollama</option>
                                    <option value="GOOGLE">Google</option>
                                    <option value="AZURE_OPENAI">Azure OpenAI</option>
                                </select>
                            </div>

                            <div class="form-group">
                                <label for="agentModel">Model</label>
                                <input type="text" id="agentModel" class="form-control"
                                    placeholder="e.g., gpt-4, claude-3-opus">
                            </div>
                        </div>
                    </div>

                    <div class="form-section">
                        <h3>Personality</h3>
                        <div class="form-group">
                            <label for="agentStyle">Communication Style</label>
                            <textarea id="agentStyle" class="form-control" rows="3"
                                placeholder="How should this agent communicate? (e.g., formal, casual, technical)"></textarea>
                        </div>

                        <div class="form-group">
                            <label for="agentPersonality">Personality Traits</label>
                            <textarea id="agentPersonality" class="form-control" rows="3"
                                placeholder="Describe personality characteristics"></textarea>
                        </div>

                        <div class="form-group">
                            <label for="agentEmoji">Emoji Preference</label>
                            <select id="agentEmoji" class="form-control">
                                <option value="NONE">None</option>
                                <option value="MINIMAL">Minimal</option>
                                <option value="MODERATE">Moderate</option>
                                <option value="ENTHUSIASTIC">Enthusiastic</option>
                            </select>
                            <small class="form-help">How often should the agent use emojis?</small>
                        </div>
                    </div>

                    <div class="form-section">
                        <h3>Constraints & Guidelines</h3>
                        <div class="form-group">
                            <label for="agentGuidelines">Additional Guidelines</label>
                            <textarea id="agentGuidelines" class="form-control" rows="4"
                                placeholder="Any other behavioral guidelines, instructions or rules this agent should follow"></textarea>
                        </div>
                    </div>

                    <div class="form-actions">
                        <button type="button" class="btn btn-secondary" onclick="closeCharacterModal()">Cancel</button>
                        <button type="submit" class="btn btn-primary">Save Character</button>
                    </div>
                </form>
            </div>
        `;

        this.setupEventListeners();
    }

    setupEventListeners() {
        const form = this.querySelector('#characterForm');
        form?.addEventListener('submit', (e) => {
            e.preventDefault();
            this.saveCharacter();
        });
    }

    async loadAgent(agentDetails) {
        this.agentDetails = agentDetails;
        this.populateForm();
    }

    async loadCurrentAgent() {
        try {
            const response = await fetch('/api/v1/agents/current', {
                headers: getAuthHeaders()
            });

            if (response.ok && response.status !== 204) {
                const apiResponse = await response.json();
                // API returns wrapped response: { success: true, data: {...} }
                const agentName = apiResponse.data.name;

                const detailsResponse = await fetch(`/api/v1/agents/${agentName}`, {
                    headers: getAuthHeaders()
                });

                if (detailsResponse.ok) {
                    const detailsApiResponse = await detailsResponse.json();
                    // API returns wrapped response: { success: true, data: {...} }
                    this.agentDetails = detailsApiResponse.data;
                    this.populateForm();
                }
            } else if (response.status === 204) {
                // No current agent selected
                showToast('No agent selected. Please select or create an agent first.', 'warning');
            }
        } catch (error) {
            console.error('Failed to load current agent:', error);
            showToast('Failed to load agent details', 'error');
        }
    }

    populateForm() {
        if (!this.agentDetails) return;

        const fields = {
            agentName: this.agentDetails.name || '',
            agentRole: this.agentDetails.role || '',
            agentPurpose: this.agentDetails.purpose || '',
            agentSpecialization: this.agentDetails.specialization || '',
            agentProvider: this.agentDetails.provider || '',
            agentModel: this.agentDetails.model || '',
            agentStyle: this.agentDetails.style || '',
            agentPersonality: this.agentDetails.personality || '',
            agentEmoji: this.agentDetails.emojiPreference || 'NONE',
            agentGuidelines: this.agentDetails.guidelines || ''
        };

        Object.entries(fields).forEach(([id, value]) => {
            const element = this.querySelector(`#${id}`);
            if (element) {
                element.value = value;
            }
        });
    }

    async saveCharacter() {
        const agentName = this.querySelector('#agentName').value;

        if (!agentName) {
            showToast('Agent name is required', 'error');
            return;
        }

        const updateData = {
            role: this.querySelector('#agentRole').value,
            purpose: this.querySelector('#agentPurpose').value,
            specialization: this.querySelector('#agentSpecialization').value,
            provider: this.querySelector('#agentProvider').value || null,
            model: this.querySelector('#agentModel').value || null,
            style: this.querySelector('#agentStyle').value,
            personality: this.querySelector('#agentPersonality').value,
            emojiPreference: this.querySelector('#agentEmoji').value,
            guidelines: this.querySelector('#agentGuidelines').value
        };

        try {
            const response = await fetch(`/api/v1/agents/${agentName}`, {
                method: 'PUT',
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(updateData)
            });

            if (response.ok) {
                showToast('Character profile updated successfully');
                closeCharacterModal();

                // Refresh agent list
                if (window.agentSelector) {
                    window.agentSelector.loadAgents();
                }
            } else {
                const errorText = await response.text();
                showToast(`Failed to update character: ${errorText}`, 'error');
            }
        } catch (error) {
            console.error('Failed to save character:', error);
            showToast('Failed to save character profile', 'error');
        }
    }
}

customElements.define('character-editor', CharacterEditor);

// Export for global access
window.characterEditor = null;
document.addEventListener('DOMContentLoaded', () => {
    window.characterEditor = document.querySelector('character-editor');
});
