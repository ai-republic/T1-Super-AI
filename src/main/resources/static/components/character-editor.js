class CharacterEditor extends HTMLElement {
    constructor() {
        super();
        this.agentDetails = null;
        this.configForm = null;
    }

    connectedCallback() {
        this.render();
    }

    render() {
        this.innerHTML = `
            <div class="character-editor">
                <form id="characterForm">
                    <agent-config-form mode="edit" read-only-name="true"></agent-config-form>

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
        this.configForm = this.querySelector('agent-config-form');

        const form = this.querySelector('#characterForm');
        form?.addEventListener('submit', (e) => {
            e.preventDefault();
            this.saveCharacter();
        });

        // Listen for validation errors from the config form
        this.configForm?.addEventListener('validation-error', (e) => {
            showToast(e.detail.message, 'error');
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

        if (!this.configForm) {
            this.configForm = this.querySelector('agent-config-form');
        }

        if (this.configForm) {
            this.configForm.setData(this.agentDetails);
        }
    }

    async saveCharacter() {
        if (!this.configForm) {
            this.configForm = this.querySelector('agent-config-form');
        }

        // Validate form
        if (!this.configForm.validate()) {
            return;
        }

        const agentData = this.configForm.getData();
        const agentName = agentData.name;

        // Prepare update data (name is excluded from updates)
        const updateData = {
            role: agentData.role,
            purpose: agentData.purpose,
            specialization: agentData.specialization,
            provider: agentData.provider || null,
            model: agentData.model || null,
            style: agentData.style,
            personality: agentData.personality,
            emojiPreference: agentData.emojiPreference,
            guidelines: agentData.guidelines
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
