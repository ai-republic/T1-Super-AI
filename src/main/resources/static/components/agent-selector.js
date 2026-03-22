// Global event listener setup - runs IMMEDIATELY when this module loads
// This ensures we catch the team-changed event even if agent-selector isn't upgraded yet
let agentSelectorInstance = null;

window.addEventListener('team-changed', (event) => {
    // If the component instance exists, update it directly
    if (agentSelectorInstance) {
        // Only reload agents if the team actually changed
        if (agentSelectorInstance.currentTeam !== event.detail) {
            agentSelectorInstance.currentTeam = event.detail;
            agentSelectorInstance.currentAgent = null; // Clear agent only on actual team change
            agentSelectorInstance.loadAgents();
        }
    } else {
        // Component not ready yet - store the team for later
        window._pendingTeamForAgentSelector = event.detail;
    }
});

class AgentSelector extends HTMLElement {
    constructor() {
        super();
        this.agents = [];
        this.currentAgent = null;
        this.currentTeam = null;

        // Register this instance globally so the event listener can access it
        agentSelectorInstance = this;
    }

    async connectedCallback() {
        this.render();
        this._isLoading = false; // Track if we're currently loading

        // Wait a moment to see if team-selector dispatches an event
        // This helps avoid race conditions on initial page load
        await new Promise(resolve => setTimeout(resolve, 100));

        // Check if a team was already set before this component was ready
        if (window._pendingTeamForAgentSelector) {
            this.currentTeam = window._pendingTeamForAgentSelector;
            delete window._pendingTeamForAgentSelector;
            await this.loadAgents();
        } else if (!this.currentTeam) {
            // Only load if we don't have a team yet (event might have already set it)
            await this.loadCurrentTeamAndAgents();
        }

        // Refresh agents every 5 seconds (only if we have a team set)
        setInterval(() => {
            if (this.currentTeam && !this._isLoading) {
                this.loadAgents();
            }
        }, 5000);
    }

    async loadCurrentTeamAndAgents() {
        try {
            const response = await fetch('/api/v1/teams/current', {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();
                this.currentTeam = apiResponse.data || 'Default';
                await this.loadAgents();
            }
        } catch (error) {
            console.error('Failed to load current team:', error);
        }
    }

    render() {
        this.innerHTML = `
            <div class="agent-list" id="agentList">
                <div class="loading">Loading agents...</div>
            </div>
        `;
    }

    // Removed loadCurrentTeam() - we now rely on team-selector to tell us the team
    // This eliminates the race condition and ensures consistency

    async loadAgents() {
        // Don't load agents if we don't know the current team yet
        if (!this.currentTeam) {
            return;
        }

        // Prevent duplicate loads
        if (this._isLoading) {
            return;
        }

        this._isLoading = true;

        try {
            const response = await fetch('/api/v1/agents', {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();
                // API returns wrapped response: { success: true, data: [...] }
                const allAgents = apiResponse.data || [];

                // Filter agents to only show those from the current team
                this.agents = allAgents.filter(agent =>
                    agent.teamName === this.currentTeam
                );

                await this.loadCurrentAgent();
                this.renderAgents();
            }
        } catch (error) {
            console.error('Failed to load agents:', error);
        } finally {
            this._isLoading = false;
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
                // No current agent selected - select first agent from the list if available
                if (this.agents.length > 0 && this.currentAgent === null) {
                    const firstAgent = this.agents[0].name;
                    // Don't call selectAgent() to avoid API call, just set it locally and dispatch event
                    this.currentAgent = firstAgent;
                    window.dispatchEvent(new CustomEvent('agent-changed', {
                        detail: this.currentAgent
                    }));
                } else if (this.currentAgent !== null) {
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
            const statusIcon = (agent.status && agent.status.toLowerCase() === 'active') ? '🟢' : '🔴';
            const colorClass = this.getAgentColorClass(agent.name);

            // Get provider and model info
            const provider = agent.provider || '';
            const model = agent.model || '';
            // Provider might be an enum string like "OPENAI" or null
            const providerModelText = (provider && model) ? `${provider} / ${model}` : '';

            return `
                <div class="agent-card ${isActive ? 'agent-active' : ''} ${colorClass}"
                     data-agent="${agent.name}"
                     onclick="agentSelector.selectAgent('${agent.name}')">
                    <div class="agent-card-content">
                        <div class="agent-info">
                            <div class="agent-name">${agent.name}</div>
                            <div class="agent-role">${agent.role || 'No role specified'}</div>
                            ${providerModelText ? `<div class="agent-provider-model">${providerModelText}</div>` : ''}
                        </div>
                        <div class="agent-controls">
                            <div class="agent-status-indicator">${statusIcon}</div>
                            <button class="btn-icon-small" onclick="event.stopPropagation(); agentSelector.cloneAgent('${agent.name}')" title="Clone to Team">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                                </svg>
                            </button>
                            <button class="btn-icon-small" onclick="event.stopPropagation(); agentSelector.editAgent('${agent.name}')" title="Edit">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                                </svg>
                            </button>
                            <button class="btn-icon-small" onclick="event.stopPropagation(); agentSelector.deleteAgent('${agent.name}')" title="Delete">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <polyline points="3 6 5 6 21 6"></polyline>
                                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                    <line x1="10" y1="11" x2="10" y2="17"></line>
                                    <line x1="14" y1="11" x2="14" y2="17"></line>
                                </svg>
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    }

    updateAgentSelection(newAgent, oldAgent) {
        // Fast DOM update - only toggle classes instead of re-rendering
        const listContainer = this.querySelector('#agentList');

        // Remove active class from old agent
        if (oldAgent) {
            const oldCard = listContainer.querySelector(`[data-agent="${oldAgent}"]`);
            if (oldCard) {
                oldCard.classList.remove('agent-active');
            }
        }

        // Add active class to new agent
        if (newAgent) {
            const newCard = listContainer.querySelector(`[data-agent="${newAgent}"]`);
            if (newCard) {
                newCard.classList.add('agent-active');
            }
        }
    }

    async selectAgent(agentName) {
        // Store the previous agent in case we need to rollback
        const previousAgent = this.currentAgent;

        // Optimistically update UI immediately with fast DOM manipulation
        this.currentAgent = agentName;
        this.updateAgentSelection(agentName, previousAgent);

        // Dispatch event for other components
        window.dispatchEvent(new CustomEvent('agent-changed', {
            detail: agentName
        }));

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
                console.log('Agent switched to:', agentName);
                showToast(`Switched to agent: ${agentName}`);
            } else {
                // Rollback on failure
                this.currentAgent = previousAgent;
                this.updateAgentSelection(previousAgent, agentName);
                window.dispatchEvent(new CustomEvent('agent-changed', {
                    detail: previousAgent
                }));
                showToast('Failed to switch agent', 'error');
            }
        } catch (error) {
            console.error('Failed to select agent:', error);
            // Rollback on error
            this.currentAgent = previousAgent;
            this.updateAgentSelection(previousAgent, agentName);
            window.dispatchEvent(new CustomEvent('agent-changed', {
                detail: previousAgent
            }));
            showToast('Failed to switch agent', 'error');
        }
    }

    async cloneAgent(agentName) {
        // Show clone agent modal
        showCloneAgentModal(agentName, this.currentTeam);
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
