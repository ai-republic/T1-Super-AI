class CreateAgentForm extends HTMLElement {
    constructor() {
        super();
        this.configForm = null;
    }

    connectedCallback() {
        this.render();
    }

    render() {
        this.innerHTML = `
            <div class="create-agent-form">
                <form id="createAgentForm">
                    <agent-config-form mode="create"></agent-config-form>

                    <div class="form-actions">
                        <button type="button" class="btn btn-secondary" onclick="closeCreateAgentModal()">Cancel</button>
                        <button type="submit" class="btn btn-primary">Create Agent</button>
                    </div>
                </form>
            </div>
        `;

        this.setupEventListeners();
    }

    setupEventListeners() {
        this.configForm = this.querySelector('agent-config-form');

        const form = this.querySelector('#createAgentForm');
        form?.addEventListener('submit', (e) => {
            e.preventDefault();
            this.createAgent();
        });

        // Listen for validation errors from the config form
        this.configForm?.addEventListener('validation-error', (e) => {
            showToast(e.detail.message, 'error');
        });
    }

    async createAgent() {
        if (!this.configForm) {
            this.configForm = this.querySelector('agent-config-form');
        }

        // Validate form
        if (!this.configForm.validate()) {
            return;
        }

        const agentData = this.configForm.getData();

        try {
            const response = await fetch('/api/v1/agents', {
                method: 'POST',
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(agentData)
            });

            if (response.ok) {
                showToast(`Agent "${agentData.name}" created successfully`);
                closeCreateAgentModal();

                // Clear form
                this.configForm.reset();

                // Refresh agent list
                if (window.agentSelector) {
                    window.agentSelector.loadAgents();
                }
            } else {
                const errorText = await response.text();
                showToast(`Failed to create agent: ${errorText}`, 'error');
            }
        } catch (error) {
            console.error('Failed to create agent:', error);
            showToast('Failed to create agent', 'error');
        }
    }
}

customElements.define('create-agent-form', CreateAgentForm);
