/**
 * Team Selector Component
 *
 * Displays a dropdown for selecting the active team workspace.
 * When a team is selected, it switches the workspace and reloads agents for that team.
 */
class TeamSelector extends HTMLElement {
    constructor() {
        super();
        this.teams = [];
        this.currentTeam = null;
    }

    connectedCallback() {
        this.render();
        this.loadTeams();
    }

    render() {
        this.innerHTML = `
            <div class="team-selector-container">
                <label for="teamSelect" class="team-selector-label">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                        <circle cx="9" cy="7" r="4"></circle>
                        <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                        <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                    </svg>
                    Team:
                </label>
                <select id="teamSelect" class="team-select" onchange="window.teamSelector.selectTeam(this.value)">
                    <option value="">Loading teams...</option>
                </select>
                <button class="btn-icon-small" onclick="window.teamSelector.editTeamName()" title="Rename Team">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                    </svg>
                </button>
                <button class="btn-icon-small" onclick="window.teamSelector.showAddTeamModal()" title="Add Team">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="12" y1="5" x2="12" y2="19"></line>
                        <line x1="5" y1="12" x2="19" y2="12"></line>
                    </svg>
                </button>
            </div>
        `;

        // Store global reference
        window.teamSelector = this;
    }

    async loadTeams() {
        try {
            const response = await fetch('/api/v1/teams', {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();
                this.teams = apiResponse.data || [];

                await this.loadCurrentTeam();

                this.renderTeams();

                // Dispatch team-changed event on initial load so agent selector loads agents
                if (this.currentTeam) {
                    const event = new CustomEvent('team-changed', {
                        detail: this.currentTeam
                    });
                    window.dispatchEvent(event);
                }
            }
        } catch (error) {
            console.error('Failed to load teams:', error);
            this.showError();
        }
    }

    async loadCurrentTeam() {
        try {
            const response = await fetch('/api/v1/teams/current', {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();
                this.currentTeam = apiResponse.data || 'Default';
            } else {
                this.currentTeam = 'Default';
            }
        } catch (error) {
            console.error('Failed to load current team:', error);
            this.currentTeam = 'Default';
        }
    }

    renderTeams() {
        const select = this.querySelector('#teamSelect');

        if (!select) return;

        if (this.teams.length === 0) {
            select.innerHTML = '<option value="Default">Default</option>';
            this.currentTeam = 'Default';
            return;
        }

        select.innerHTML = this.teams.map(team => {
            const selected = team === this.currentTeam ? 'selected' : '';
            return `<option value="${team}" ${selected}>${team}</option>`;
        }).join('');
    }

    showError() {
        const select = this.querySelector('#teamSelect');
        if (select) {
            select.innerHTML = '<option value="Default">Default (Error loading teams)</option>';
        }
    }

    async selectTeam(teamName) {
        if (!teamName || teamName === this.currentTeam) {
            return;
        }

        try {
            const response = await fetch('/api/v1/teams/switch', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...getAuthHeaders()
                },
                body: JSON.stringify({ teamName })
            });

            if (response.ok) {
                this.currentTeam = teamName;

                // Dispatch team-changed event so agent selector reloads
                window.dispatchEvent(new CustomEvent('team-changed', {
                    detail: teamName
                }));

                console.log('Team switched to:', teamName);
            } else {
                console.error('Failed to switch team');
                // Revert selection
                this.renderTeams();
            }
        } catch (error) {
            console.error('Failed to switch team:', error);
            // Revert selection
            this.renderTeams();
        }
    }

    showAddTeamModal() {
        const modal = document.getElementById('addTeamModal');
        if (modal) {
            modal.classList.add('active');
            // Clear the input field
            const input = document.getElementById('newTeamNameInput');
            if (input) {
                input.value = '';
                // Focus the input after a short delay to ensure modal is visible
                setTimeout(() => input.focus(), 100);
            }
        }
    }

    async editTeamName() {
        const oldTeamName = this.currentTeam;
        if (!oldTeamName) {
            showToast('No team selected', 'error');
            return;
        }

        const newTeamName = prompt(`Rename team "${oldTeamName}" to:`, oldTeamName);

        if (!newTeamName || newTeamName.trim() === '' || newTeamName === oldTeamName) {
            return;
        }

        try {
            const response = await fetch('/api/v1/teams/rename', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...getAuthHeaders()
                },
                body: JSON.stringify({
                    oldTeamName: oldTeamName,
                    newTeamName: newTeamName.trim()
                })
            });

            if (response.ok) {
                const result = await response.json();
                showToast(result.message || `Team renamed to "${newTeamName}"`, 'success');

                // Update current team
                this.currentTeam = newTeamName;

                // Reload teams list
                await this.loadTeams();

                // Dispatch team-changed event
                window.dispatchEvent(new CustomEvent('team-changed', {
                    detail: newTeamName
                }));
            } else {
                const error = await response.json();
                showToast(error.message || 'Failed to rename team', 'error');
            }
        } catch (error) {
            console.error('Failed to rename team:', error);
            showToast('Failed to rename team', 'error');
        }
    }
}

// Register the custom element
customElements.define('team-selector', TeamSelector);
