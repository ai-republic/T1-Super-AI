class CreateAgentForm extends HTMLElement {
    constructor() {
        super();
    }

    connectedCallback() {
        this.render();
    }

    render() {
        this.innerHTML = `
            <div class="create-agent-form">
                <form id="createAgentForm">
                    <div class="form-section">
                        <div class="form-group">
                            <label for="newAgentName">Agent Name *</label>
                            <input type="text" id="newAgentName" class="form-control" required
                                placeholder="Enter a unique name for the agent">
                        </div>

                        <div class="form-group">
                            <label for="newAgentRole">Role</label>
                            <input type="text" id="newAgentRole" class="form-control"
                                placeholder="e.g., Software Developer, Data Analyst">
                        </div>

                        <div class="form-group">
                            <label for="newAgentPurpose">Purpose</label>
                            <textarea id="newAgentPurpose" class="form-control" rows="3"
                                placeholder="What is this agent designed to do?"></textarea>
                        </div>

                        <div class="form-group">
                            <label for="newAgentSpecialization">Specialization</label>
                            <textarea id="newAgentSpecialization" class="form-control" rows="2"
                                placeholder="What are this agent's areas of expertise?"></textarea>
                        </div>

                        <div class="form-group">
                            <label for="newAgentStyle">Communication Style</label>
                            <input type="text" id="newAgentStyle" class="form-control"
                                placeholder="e.g., professional, casual, technical">
                        </div>

                        <div class="form-group">
                            <label for="newAgentPersonality">Personality</label>
                            <input type="text" id="newAgentPersonality" class="form-control"
                                placeholder="e.g., helpful, concise, detailed">
                        </div>
                    </div>

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
        const form = this.querySelector('#createAgentForm');
        form?.addEventListener('submit', (e) => {
            e.preventDefault();
            this.createAgent();
        });
    }

    async createAgent() {
        const name = this.querySelector('#newAgentName').value.trim();

        if (!name) {
            showToast('Agent name is required', 'error');
            return;
        }

        const agentData = {
            name: name,
            role: this.querySelector('#newAgentRole').value.trim(),
            purpose: this.querySelector('#newAgentPurpose').value.trim(),
            specialization: this.querySelector('#newAgentSpecialization').value.trim(),
            style: this.querySelector('#newAgentStyle').value.trim(),
            personality: this.querySelector('#newAgentPersonality').value.trim()
        };

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
                showToast(`Agent "${name}" created successfully`);
                closeCreateAgentModal();

                // Clear form
                this.querySelector('#createAgentForm').reset();

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
