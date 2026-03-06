class ChatComponent extends HTMLElement {
    constructor() {
        super();
        this.messages = [];
        this.currentAgent = null;
        this.isStreaming = false;
    }

    connectedCallback() {
        this.render();
        this.setupEventListeners();
        // Don't load history here - it will be loaded when agent-changed event fires
        // after the agent selector loads the current agent
    }

    render() {
        this.innerHTML = `
            <div class="chat-container">
                <div class="chat-messages" id="chatMessages">
                    <div class="welcome-message">
                        <div class="welcome-icon">👋</div>
                        <h2>Welcome to T1 Super AI</h2>
                        <p>Select an agent or create a new one to start chatting</p>
                    </div>
                </div>
                <div class="chat-input-container">
                    <form class="chat-input-form" id="chatForm">
                        <textarea
                            id="messageInput"
                            class="chat-input"
                            placeholder="Type your message here..."
                            rows="1"
                        ></textarea>
                        <button type="submit" class="btn btn-primary btn-send" id="sendBtn">
                            <span class="icon">📤</span>
                            Send
                        </button>
                    </form>
                    <div class="chat-actions">
                        <button class="btn btn-secondary btn-sm" onclick="chatComponent.clearHistory()">
                            <span class="icon">🗑️</span>
                            Clear History
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="chatComponent.reloadContext()">
                            <span class="icon">🔄</span>
                            Reload Context
                        </button>
                    </div>
                </div>
            </div>
        `;
    }

    setupEventListeners() {
        const form = this.querySelector('#chatForm');
        const input = this.querySelector('#messageInput');
        const sendBtn = this.querySelector('#sendBtn');

        // Auto-resize textarea
        input.addEventListener('input', () => {
            input.style.height = 'auto';
            input.style.height = Math.min(input.scrollHeight, 200) + 'px';
        });

        // Handle Enter key (Shift+Enter for new line)
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                // Use requestSubmit() instead of dispatching event (modern standard)
                if (form.requestSubmit) {
                    form.requestSubmit();
                } else {
                    // Fallback for older browsers
                    form.dispatchEvent(new Event('submit', { cancelable: true }));
                }
            }
        });

        // Handle form submission
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const message = input.value.trim();
            if (message && !this.isStreaming) {
                await this.sendMessage(message);
                input.value = '';
                input.style.height = 'auto';
            }
        });

        // Listen for agent change events
        window.addEventListener('agent-changed', (e) => {
            this.currentAgent = e.detail;
            this.loadHistory();
        });
    }

    async loadHistory() {
        // Don't reload history during active streaming to avoid conflicts
        if (this.isStreaming) {
            return;
        }

        try {
            const response = await fetch('/api/v1/chat/history', {
                headers: getAuthHeaders()
            });

            if (response.ok) {
                const apiResponse = await response.json();

                // API returns wrapped response: { success: true, data: [...] }
                this.messages = apiResponse.data || [];

                // Clean up any messages that might be marked as streaming
                this.messages.forEach(msg => {
                    if (msg.streaming) {
                        msg.streaming = false;
                    }
                });

                this.renderMessages();
            }
        } catch (error) {
            console.error('Failed to load history:', error);
        }
    }

    async sendMessage(content) {
        // Disable input and set streaming flag FIRST
        this.isStreaming = true;
        const input = this.querySelector('#messageInput');
        const sendBtn = this.querySelector('#sendBtn');
        input.disabled = true;
        sendBtn.disabled = true;

        // Add user message to UI (will use appendMessageToDOM since isStreaming=true)
        this.addMessage({
            role: 'user',
            content: content,
            timestamp: new Date().toISOString()
        });

        // Create assistant message placeholder
        const assistantMessageId = 'msg-' + Date.now();
        this.addMessage({
            id: assistantMessageId,
            role: 'assistant',
            content: '',
            timestamp: new Date().toISOString(),
            streaming: true,
            agentName: this.currentAgent || 'Assistant'
        });

        try {
            // Build URL with message and agentName parameters
            const streamUrl = `/api/v1/chat/stream?message=${encodeURIComponent(content)}${this.currentAgent ? `&agentName=${encodeURIComponent(this.currentAgent)}` : ''}`;

            // Use fetch with ReadableStream for SSE with authentication headers
            const response = await fetch(
                streamUrl,
                {
                    method: 'GET',
                    headers: {
                        ...getAuthHeaders(),
                        'Accept': 'text/event-stream'
                    }
                }
            );

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let currentEvent = '';

            let messageFinalized = false;

            while (true) {
                const { done, value } = await reader.read();

                if (done) {
                    break;
                }

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n\n');
                buffer = lines.pop(); // Keep incomplete message in buffer

                for (const message of lines) {
                    if (!message.trim()) {
                        continue; // Skip empty messages
                    }

                    const eventLines = message.split('\n');
                    let eventType = '';
                    let eventData = '';

                    for (const line of eventLines) {
                        // Handle both "event: " and "event:" formats (with and without space after colon)
                        if (line.startsWith('event:')) {
                            let value = line.substring(6); // Skip "event:"
                            if (value.startsWith(' ')) {
                                value = value.substring(1); // Skip optional space
                            }
                            eventType = value.trim();
                        } else if (line.startsWith('data:')) {
                            const afterColon = line.substring(5); // Skip "data:"

                            // Only skip the space if there's more content after it
                            if (afterColon.length > 1 && afterColon.startsWith(' ')) {
                                eventData = afterColon.substring(1);
                            } else if (afterColon === ' ') {
                                // Special case: data is exactly one space
                                eventData = ' ';
                            } else {
                                eventData = afterColon;
                            }
                        }
                    }

                    // Handle different event types
                    if (eventType === 'chunk') {
                        this.appendToMessage(assistantMessageId, eventData);
                    } else if (eventType === 'complete') {
                        messageFinalized = true;
                        try {
                            const completeData = JSON.parse(eventData);
                            this.finalizeMessage(assistantMessageId, completeData);
                        } catch (e) {
                            console.error('Error parsing complete data:', e);
                            this.finalizeMessage(assistantMessageId, {});
                        }
                    } else if (eventType === '' && message.trim()) {
                        console.warn('Received SSE message with empty event type');
                    }
                }
            }

            // Ensure message is finalized even if complete event wasn't received
            if (!messageFinalized) {
                console.warn('Stream ended without complete event');
                this.finalizeMessage(assistantMessageId, {});
            }

            // Re-enable input and finalize UI
            this.isStreaming = false;
            input.disabled = false;
            sendBtn.disabled = false;
            input.focus();

            // Force final render to ensure clean state
            this.renderMessages();
            requestAnimationFrame(() => {
                this.renderMessages();
            });

        } catch (error) {
            console.error('Failed to send message:', error);
            this.appendToMessage(assistantMessageId, '\n\n*Failed to send message: ' + error.message + '*');
            this.finalizeMessage(assistantMessageId, {});

            this.isStreaming = false;
            input.disabled = false;
            sendBtn.disabled = false;

            requestAnimationFrame(() => {
                this.renderMessages();
            });
        }
    }

    addMessage(message) {
        this.messages.push(message);

        // Only render if not streaming to avoid flickering
        if (!this.isStreaming) {
            this.renderMessages();
        } else {
            // During streaming, just append the message
            this.appendMessageToDOM(message);
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

    appendMessageToDOM(msg) {
        const messagesContainer = this.querySelector('#chatMessages');

        // Remove welcome message if it exists
        const welcomeMsg = messagesContainer.querySelector('.welcome-message');
        if (welcomeMsg) {
            welcomeMsg.remove();
        }

        const timestamp = new Date(msg.timestamp).toLocaleTimeString();
        const isUser = msg.role === 'user';
        const messageId = msg.id || 'msg-' + timestamp;

        // Use the agent name from the message
        const agentName = isUser ? 'You' : (msg.agentName || 'Assistant');
        const colorClass = isUser ? '' : this.getAgentColorClass(msg.agentName || 'Assistant');

        const messageHTML = `
            <div class="message ${isUser ? 'message-user' : 'message-assistant'} ${colorClass}" data-message-id="${messageId}" data-agent="${isUser ? '' : agentName}">
                <div class="message-avatar">
                    ${isUser ? '👤' : '🤖'}
                </div>
                <div class="message-bubble">
                    <div class="message-header">
                        <span class="message-role">${isUser ? 'You' : agentName}</span>
                        <span class="message-time">${timestamp}</span>
                    </div>
                    <div class="message-content">
                        ${this.renderMarkdown(msg.content)}
                    </div>
                    ${msg.streaming ? '<div class="streaming-indicator"><span></span><span></span><span></span></div>' : ''}
                </div>
            </div>
        `;

        messagesContainer.insertAdjacentHTML('beforeend', messageHTML);

        // Force reflow to ensure rendering
        void messagesContainer.offsetHeight;

        // Highlight code blocks
        const newMessage = messagesContainer.lastElementChild;
        this.highlightCode(newMessage);

        // Scroll to bottom
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    appendToMessage(messageId, chunk) {
        const message = this.messages.find(m => m.id === messageId);
        if (message) {
            message.content += chunk;
            this.updateMessage(messageId);
        }
    }

    finalizeMessage(messageId, data) {
        const message = this.messages.find(m => m.id === messageId);

        if (!message) {
            console.error('Message not found:', messageId);
            return;
        }

        message.streaming = false;

        if (data.tokensUsed) {
            message.tokensUsed = data.tokensUsed;
        }
        if (data.processingTime) {
            message.processingTime = data.processingTime;
        }

        // Update the message content one last time
        this.updateMessage(messageId);

        // Remove the streaming indicator from the DOM
        const messageElement = this.querySelector(`[data-message-id="${messageId}"]`);
        if (messageElement) {
            const streamingIndicator = messageElement.querySelector('.streaming-indicator');
            if (streamingIndicator) {
                streamingIndicator.remove();
            }

            // Force visual update by toggling a class
            messageElement.classList.add('finalized');

            // Ensure the message is visible
            const contentDiv = messageElement.querySelector('.message-content');
            if (contentDiv) {
                void contentDiv.offsetHeight;
            }
        }
    }

    updateMessage(messageId) {
        const messageElement = this.querySelector(`[data-message-id="${messageId}"]`);
        if (messageElement) {
            const message = this.messages.find(m => m.id === messageId);
            const contentDiv = messageElement.querySelector('.message-content');
            if (contentDiv && message) {
                contentDiv.innerHTML = this.renderMarkdown(message.content);
                void contentDiv.offsetHeight;
                this.highlightCode(contentDiv);

                // Scroll to bottom
                const messagesContainer = this.querySelector('#chatMessages');
                requestAnimationFrame(() => {
                    messagesContainer.scrollTop = messagesContainer.scrollHeight;
                });
            }
        }
    }

    renderMessages() {
        const messagesContainer = this.querySelector('#chatMessages');

        if (!messagesContainer) {
            return;
        }

        if (this.messages.length === 0) {
            messagesContainer.innerHTML = `
                <div class="welcome-message">
                    <div class="welcome-icon">👋</div>
                    <h2>Welcome to T1 Super AI</h2>
                    <p>Start a conversation with your agent</p>
                </div>
            `;
            return;
        }

        const messagesHTML = this.messages.map(msg => {
            const timestamp = new Date(msg.timestamp).toLocaleTimeString();
            const isUser = msg.role === 'user';
            const messageId = msg.id || 'msg-' + timestamp;

            // Use the agent name from the message
            const agentName = isUser ? 'You' : (msg.agentName || 'Assistant');
            const colorClass = isUser ? '' : this.getAgentColorClass(msg.agentName || 'Assistant');

            let metadata = '';
            if (msg.tokensUsed || msg.processingTime) {
                metadata = `
                    <div class="message-metadata">
                        ${msg.tokensUsed ? `<span>Tokens: ${msg.tokensUsed}</span>` : ''}
                        ${msg.processingTime ? `<span>Time: ${msg.processingTime}ms</span>` : ''}
                    </div>
                `;
            }

            return `
                <div class="message ${isUser ? 'message-user' : 'message-assistant'} ${colorClass}" data-message-id="${messageId}" data-agent="${isUser ? '' : agentName}">
                    <div class="message-avatar">
                        ${isUser ? '👤' : '🤖'}
                    </div>
                    <div class="message-bubble">
                        <div class="message-header">
                            <span class="message-role">${isUser ? 'You' : agentName}</span>
                            <span class="message-time">${timestamp}</span>
                        </div>
                        <div class="message-content">
                            ${this.renderMarkdown(msg.content)}
                        </div>
                        ${metadata}
                        ${msg.streaming ? '<div class="streaming-indicator"><span></span><span></span><span></span></div>' : ''}
                    </div>
                </div>
            `;
        }).join('');

        messagesContainer.innerHTML = messagesHTML;
        void messagesContainer.offsetHeight;

        // Use requestAnimationFrame to ensure DOM is ready
        requestAnimationFrame(() => {
            this.highlightCode(messagesContainer);
            requestAnimationFrame(() => {
                messagesContainer.scrollTop = messagesContainer.scrollHeight;
            });
        });
    }

    renderMarkdown(content) {
        if (!content) return '';

        // Preprocess content to fix common markdown formatting issues
        let processedContent = content;

        // Fix 1: Ensure list items after colons are on new lines
        // "things like: - item" → "things like:\n- item"
        processedContent = processedContent.replace(/:\s+-\s+/g, ':\n- ');

        // Fix 2: Detect inline list items (multiple dashes in same sentence)
        // If we see "text - item1 - item2", convert to proper list
        const lines = processedContent.split('\n');
        const fixedLines = [];

        for (let line of lines) {
            // Check if line has multiple " - " patterns (likely inline list)
            const dashCount = (line.match(/\s-\s/g) || []).length;

            if (dashCount >= 2) {
                // Split by " - " and make each a list item
                const parts = line.split(/\s+-\s+/);
                const prefix = parts[0].trim();

                // Add prefix if it doesn't end with colon
                if (prefix && !prefix.endsWith(':')) {
                    fixedLines.push(prefix + ':');
                } else {
                    fixedLines.push(prefix);
                }

                // Add each part as a list item
                for (let i = 1; i < parts.length; i++) {
                    fixedLines.push('- ' + parts[i].trim());
                }
            } else {
                // Keep line as-is
                fixedLines.push(line);
            }
        }

        processedContent = fixedLines.join('\n');

        // Use marked.js to render markdown
        if (typeof marked !== 'undefined') {
            // Configure marked for better rendering
            marked.setOptions({
                breaks: true,        // Convert \n to <br>
                gfm: true,          // GitHub Flavored Markdown
                headerIds: false,   // Don't add IDs to headers
                mangle: false,      // Don't escape email addresses
                sanitize: false,    // Allow HTML (we trust our content)
                smartLists: true,   // Better list parsing
                smartypants: true   // Use smart quotes and dashes
            });

            return marked.parse(processedContent);
        }

        // Fallback: simple line breaks
        return processedContent.replace(/\n/g, '<br>');
    }

    highlightCode(container) {
        if (typeof hljs !== 'undefined') {
            container.querySelectorAll('pre code').forEach((block) => {
                hljs.highlightElement(block);
            });
        }
    }

    async clearHistory() {
        if (!confirm('Are you sure you want to clear the chat history?')) {
            return;
        }

        try {
            const response = await fetch('/api/v1/chat/history', {
                method: 'DELETE',
                headers: getAuthHeaders()
            });

            if (response.ok) {
                this.messages = [];
                this.renderMessages();
                showToast('Chat history cleared');
            } else {
                showToast('Failed to clear history', 'error');
            }
        } catch (error) {
            console.error('Failed to clear history:', error);
            showToast('Failed to clear history', 'error');
        }
    }

    async reloadContext() {
        try {
            const response = await fetch('/api/v1/chat/reload-context', {
                method: 'POST',
                headers: getAuthHeaders()
            });

            if (response.ok) {
                showToast('Context reloaded successfully');
            } else {
                showToast('Failed to reload context', 'error');
            }
        } catch (error) {
            console.error('Failed to reload context:', error);
            showToast('Failed to reload context', 'error');
        }
    }
}

customElements.define('chat-component', ChatComponent);

// Export for global access
window.chatComponent = null;
document.addEventListener('DOMContentLoaded', () => {
    window.chatComponent = document.querySelector('chat-component');
});
