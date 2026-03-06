class AgentSelector extends HTMLElement {
    constructor() {
        super();
        this.agents = [];
        this.currentAgent = null;
    }

    connectedCallback() {
        this.render();
        this.loadAgents();
        // Refresh agents every 5 seconds
        setInterval(() => this.loadAgents(), 5000);
    }

    render() {
        this.innerHTML = `
            <div class="agent-list" id="agentList">
                <div class="loading">Loading agents...</div>
            </div>
        `;
    }

    async loadAgents() {
        try {
            const response = await fetch('/api/v1/agents', {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();
                // API returns wrapped response: { success: true, data: [...] }
                this.agents = apiResponse.data || [];
                await this.loadCurrentAgent();
                this.renderAgents();
            }
        } catch (error) {
            console.error('Failed to load agents:', error);
        }
    }

    async loadCurrentAgent() {
        try {
            const response = await fetch('/api/v1/agents/current', {
                headers: getAuthHeaders()
            });

            if (response.ok && response.status !== 204) {
                const apiResponse = await response.json();
                // API returns wrapped response: { success: true, data: {...} }
                const newAgent = apiResponse.data.name;

                // Only dispatch event if agent actually changed
                if (newAgent !== this.currentAgent) {
                    this.currentAgent = newAgent;
                    // Dispatch agent-changed event so chat component loads history
                    window.dispatchEvent(new CustomEvent('agent-changed', {
                        detail: this.currentAgent
                    }));
                }
            } else if (response.status === 204) {
                // No current agent selected - this is normal on first load
                if (this.currentAgent !== null) {
                    this.currentAgent = null;
                }
            }
        } catch (error) {
            console.error('Failed to load current agent:', error);
        }
    }

    getAgentColorClass(agentName) {
        if (!agentName || agentName === 'Assistant') {
            return '';
        }

        // Simple hash function to convert agent name to a number
        let hash = 0;
        for (let i = 0; i < agentName.length; i++) {
            hash = ((hash << 5) - hash) + agentName.charCodeAt(i);
            hash = hash & hash; // Convert to 32-bit integer
        }

        // Map to color index (0-9)
        const colorIndex = Math.abs(hash) % 10;
        return `agent-color-${colorIndex}`;
    }

    renderAgents() {
        const listContainer = this.querySelector('#agentList');

        if (this.agents.length === 0) {
            listContainer.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">🤖</div>
                    <p>No agents found</p>
                    <button class="btn btn-primary btn-sm" onclick="showCreateAgentModal()">
                        Create Agent
                    </button>
                </div>
            `;
            return;
        }

        listContainer.innerHTML = this.agents.map(agent => {
            const isActive = agent.name === this.currentAgent || agent.isCurrentAgent;
            const statusIcon = agent.status === 'ACTIVE' ? '🟢' : '🔴';
            const colorClass = this.getAgentColorClass(agent.name);

            return `
                <div class="agent-card ${isActive ? 'agent-active' : ''} ${colorClass}"
                     data-agent="${agent.name}"
                     onclick="agentSelector.selectAgent('${agent.name}')">
                    <div class="agent-card-header">
                        <div class="agent-name">${agent.name}</div>
                        <div class="agent-status">${statusIcon}</div>
                    </div>
                    <div class="agent-role">${agent.role || 'No role specified'}</div>
                    <div class="agent-stats">
                        <span class="stat-item">
                            <span class="icon">💬</span>
                            ${agent.conversationCount || 0}
                        </span>
                    </div>
                    <div class="agent-actions">
                        <button class="btn-icon" onclick="event.stopPropagation(); agentSelector.editAgent('${agent.name}')" title="Edit">
                            ✏️
                        </button>
                        <button class="btn-icon" onclick="event.stopPropagation(); agentSelector.deleteAgent('${agent.name}')" title="Delete">
                            🗑️
                        </button>
                    </div>
                </div>
            `;
        }).join('');
    }

    async selectAgent(agentName) {
        try {
            const response = await fetch('/api/v1/agents/current', {
                method: 'PUT',
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ agentName })
            });

            if (response.ok) {
                this.currentAgent = agentName;
                this.renderAgents();

                // Dispatch event for other components
                window.dispatchEvent(new CustomEvent('agent-changed', {
                    detail: agentName
                }));

                showToast(`Switched to agent: ${agentName}`);
            } else {
                showToast('Failed to switch agent', 'error');
            }
        } catch (error) {
            console.error('Failed to select agent:', error);
            showToast('Failed to switch agent', 'error');
        }
    }

    async editAgent(agentName) {
        // Load agent details and show character editor
        try {
            const response = await fetch(`/api/v1/agents/${agentName}`, {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();
                // API returns wrapped response: { success: true, data: {...} }
                window.characterEditor?.loadAgent(apiResponse.data);
                showCharacterEditor();
            }
        } catch (error) {
            console.error('Failed to load agent:', error);
            showToast('Failed to load agent details', 'error');
        }
    }

    async deleteAgent(agentName) {
        if (!confirm(`Are you sure you want to delete agent "${agentName}"?`)) {
            return;
        }

        try {
            const response = await fetch(`/api/v1/agents/${agentName}`, {
                method: 'DELETE',
                headers: getAuthHeaders()
            });

            if (response.ok) {
                showToast(`Agent "${agentName}" deleted`);
                await this.loadAgents();
            } else {
                showToast('Failed to delete agent', 'error');
            }
        } catch (error) {
            console.error('Failed to delete agent:', error);
            showToast('Failed to delete agent', 'error');
        }
    }
}

customElements.define('agent-selector', AgentSelector);

// Export for global access
window.agentSelector = null;
document.addEventListener('DOMContentLoaded', () => {
    window.agentSelector = document.querySelector('agent-selector');
});
