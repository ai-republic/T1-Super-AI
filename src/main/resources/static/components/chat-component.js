class ChatComponent extends HTMLElement {
    constructor() {
        super();
        this.messages = [];
        this.currentAgent = null;
        this.activeStreams = new Set(); // Track multiple concurrent streams
        this.ws = null;
        this.attachedFiles = []; // Track attached files
        this.panelId = 'chat-panel-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9); // Unique panel ID
    }

    connectedCallback() {
        this.render();
        this.setupEventListeners();
        this.setupWebSocket();
        // Don't load history here - it will be loaded when agent-changed event fires
        // after the agent selector loads the current agent
    }

    setupWebSocket() {
        // Create WebSocket connection for real-time collaboration messages
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/chat`;

        try {
            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                console.log('✅ WebSocket connected');
            };

            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    this.handleWebSocketMessage(data);
                } catch (error) {
                    console.error('Error parsing WebSocket message:', error);
                }
            };

            this.ws.onerror = (error) => {
                console.warn('⚠️ WebSocket error (non-critical):', error.message || 'Connection failed');
            };

            this.ws.onclose = (event) => {
                if (event.wasClean) {
                    console.log('WebSocket disconnected cleanly');
                } else {
                    console.warn('⚠️ WebSocket disconnected unexpectedly (non-critical)');
                }
                // Reconnect after 5 seconds (increased from 3)
                setTimeout(() => {
                    console.log('Attempting to reconnect WebSocket...');
                    this.setupWebSocket();
                }, 5000);
            };
        } catch (error) {
            console.warn('⚠️ Could not establish WebSocket connection (non-critical):', error.message);
            // WebSocket is optional, don't retry if initial connection fails
        }
    }

    handleWebSocketMessage(data) {
        switch (data.type) {
            case 'tool_call':
                this.addCollaborationMessage({
                    type: 'tool_call',
                    fromAgent: data.fromAgent,
                    toolName: data.toolName,
                    timestamp: data.timestamp
                });
                // Add waiting indicator
                this.showWaitingIndicator(data.toolName);
                break;
            case 'tool_result':
                // Remove waiting indicator
                this.hideWaitingIndicator();
                this.addCollaborationMessage({
                    type: 'tool_result',
                    fromAgent: data.fromAgent,
                    toolName: data.toolName,
                    toolResult: data.toolResult,
                    success: data.success,
                    responseTimeMs: data.responseTimeMs,
                    timestamp: data.timestamp
                });
                break;
            case 'agent_communication':
                this.addCollaborationMessage({
                    type: 'agent_communication',
                    fromAgent: data.fromAgent,
                    toAgent: data.toAgent,
                    message: data.message,
                    timestamp: data.timestamp
                });
                // Add waiting indicator for agent response
                this.showWaitingIndicator(`${data.toAgent} processing...`);
                break;
            case 'agent_response':
                // Remove waiting indicator
                this.hideWaitingIndicator();
                this.addCollaborationMessage({
                    type: 'agent_response',
                    fromAgent: data.fromAgent,
                    toAgent: data.toAgent,
                    response: data.response,
                    responseTimeMs: data.responseTimeMs,
                    timestamp: data.timestamp
                });
                break;
        }
    }

    showWaitingIndicator(text) {
        // Remove any existing waiting indicator first
        this.hideWaitingIndicator();

        const messagesContainer = this.querySelector('#chatMessages');
        const waitingHTML = `
            <div class="message message-waiting" id="waitingIndicator">
                <div class="message-avatar">⏳</div>
                <div class="message-bubble">
                    <div class="message-content waiting-content">
                        <div class="waiting-spinner"></div>
                        <span class="waiting-text">${text || 'Waiting for response...'}</span>
                    </div>
                </div>
            </div>
        `;
        messagesContainer.insertAdjacentHTML('beforeend', waitingHTML);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    hideWaitingIndicator() {
        const indicator = this.querySelector('#waitingIndicator');
        if (indicator) {
            indicator.remove();
        }
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
                    <div id="filePreviewContainer" class="file-preview-container" style="display: none;"></div>
                    <form class="chat-input-form" id="chatForm">
                        <input
                            type="file"
                            id="fileInput"
                            class="file-input"
                            multiple
                            accept="image/*,.pdf,.txt"
                            style="display: none;"
                        />
                        <button type="button" class="btn btn-secondary btn-attach" id="attachBtn" title="Attach files (images, PDF, text)">
                            <span class="icon">📎</span>
                        </button>
                        <textarea
                            id="messageInput"
                            class="chat-input"
                            placeholder="Type your message here..."
                            rows="1"
                        ></textarea>
                        <button type="submit" class="btn btn-primary btn-send" id="sendBtn" title="Send message">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                <line x1="12" y1="19" x2="12" y2="5"></line>
                                <polyline points="5 12 12 5 19 12"></polyline>
                            </svg>
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
        const attachBtn = this.querySelector('#attachBtn');
        const fileInput = this.querySelector('#fileInput');

        // File attachment handling
        attachBtn.addEventListener('click', () => {
            fileInput.click();
        });

        fileInput.addEventListener('change', (e) => {
            this.handleFileSelection(e.target.files);
        });

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

        // Handle form submission - NOW SUPPORTS PARALLEL PROCESSING
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const message = input.value.trim();
            if (message || this.attachedFiles.length > 0) {
                // Clear input immediately so user can type next message
                input.value = '';
                input.style.height = 'auto';

                // Send message with attachments without blocking (parallel processing)
                this.sendMessage(message, this.attachedFiles);

                // Clear attached files
                this.attachedFiles = [];
                fileInput.value = '';
                this.updateFilePreview();
            }
        });

        // Listen for agent change events
        window.addEventListener('agent-changed', (e) => {
            this.currentAgent = e.detail;
            this.loadHistory();
        });
    }

    handleFileSelection(files) {
        if (!files || files.length === 0) return;

        // Convert FileList to Array and add to attachedFiles
        Array.from(files).forEach(file => {
            // Check file size (max 10MB)
            if (file.size > 10 * 1024 * 1024) {
                this.showError(`File ${file.name} is too large (max 10MB)`);
                return;
            }

            // Check file type
            const validTypes = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/webp', 'application/pdf', 'text/plain'];
            if (!validTypes.includes(file.type)) {
                this.showError(`File type ${file.type} is not supported`);
                return;
            }

            this.attachedFiles.push(file);
        });

        this.updateFilePreview();
    }

    updateFilePreview() {
        const container = this.querySelector('#filePreviewContainer');
        if (this.attachedFiles.length === 0) {
            container.style.display = 'none';
            container.innerHTML = '';
            return;
        }

        container.style.display = 'flex';
        container.innerHTML = this.attachedFiles.map((file, index) => `
            <div class="file-preview-item">
                <div class="file-preview-icon">${this.getFileIcon(file.type)}</div>
                <div class="file-preview-name">${file.name}</div>
                <button type="button" class="file-preview-remove" onclick="chatComponent.removeFile(${index})" title="Remove file">
                    ×
                </button>
            </div>
        `).join('');
    }

    getFileIcon(mimeType) {
        if (mimeType.startsWith('image/')) return '🖼️';
        if (mimeType === 'application/pdf') return '📄';
        if (mimeType === 'text/plain') return '📝';
        return '📎';
    }

    removeFile(index) {
        this.attachedFiles.splice(index, 1);
        this.updateFilePreview();
    }

    showError(message) {
        // Create a temporary error toast
        const toast = document.createElement('div');
        toast.className = 'error-toast';
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 3000);
    }

    async loadHistory() {
        // Don't reload history during active streaming to avoid conflicts
        if (this.activeStreams.size > 0) {
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

    async sendMessage(content, files = []) {
        const input = this.querySelector('#messageInput');
        const sendBtn = this.querySelector('#sendBtn');

        // Generate unique stream ID for tracking
        const streamId = 'stream-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
        this.activeStreams.add(streamId);

        // CRITICAL: Capture the agent at the time of sending to validate the response
        const requestAgentName = this.currentAgent;

        // Update UI to show activity
        this.updateInputState();

        // Add user message to UI with file attachments
        const userMessage = {
            role: 'user',
            content: content,
            timestamp: new Date().toISOString()
        };

        if (files.length > 0) {
            userMessage.attachments = files.map(f => ({
                filename: f.name,
                mimeType: f.type,
                fileSize: f.size
            }));
        }

        this.addMessage(userMessage);

        // Create assistant message placeholder
        const assistantMessageId = 'msg-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);

        // Add helpful message for file uploads (vision models can be slow)
        const initialContent = files.length > 0 ?
            '_Analyzing image with vision model... This may take a few minutes..._' : '';

        this.addMessage({
            id: assistantMessageId,
            role: 'assistant',
            content: initialContent,
            timestamp: new Date().toISOString(),
            streaming: true,
            agentName: requestAgentName || 'Assistant',
            requestAgentName: requestAgentName  // Track which agent this request was for
        });

        try {
            let response;

            // If files are attached, use multipart form data endpoint
            if (files.length > 0) {
                const formData = new FormData();
                formData.append('message', content);
                // CRITICAL: Send the request agent name and panel ID (captured at send time)
                if (requestAgentName) {
                    formData.append('agentName', requestAgentName);
                }
                if (this.panelId) {
                    formData.append('panelId', this.panelId);
                }
                files.forEach(file => {
                    formData.append('files', file);
                });

                // For file uploads, use non-streaming endpoint
                console.log('Uploading files to /api/v1/chat/with-files');

                // Create abort controller for timeout (5 minutes for vision models that take longer)
                const abortController = new AbortController();
                const timeoutId = setTimeout(() => abortController.abort(), 300000);

                try {
                    response = await fetch('/api/v1/chat/with-files', {
                        method: 'POST',
                        headers: getAuthHeaders(),
                        body: formData,
                        signal: abortController.signal
                    });
                    clearTimeout(timeoutId);
                } catch (error) {
                    clearTimeout(timeoutId);
                    if (error.name === 'AbortError') {
                        throw new Error('Request timed out after 5 minutes. Vision models can take a while - please wait or try a faster model like GPT-4V or Claude 3.5.');
                    }
                    throw error;
                }

                console.log('Fetch completed, status:', response.status, 'ok:', response.ok);

                if (!response.ok) {
                    const errorText = await response.text();
                    console.error('File upload failed:', response.status, errorText);
                    throw new Error(`Upload failed (${response.status}): ${errorText}`);
                }

                console.log('Parsing JSON response...');
                const result = await response.json();
                console.log('File upload response:', result);
                console.log('Response structure:', {
                    success: result.success,
                    hasData: !!result.data,
                    hasResponse: !!(result.data && result.data.response),
                    responseLength: result.data && result.data.response ? result.data.response.length : 0
                });

                // Check if the response has the expected structure
                if (!result.success || !result.data || !result.data.response) {
                    console.error('Unexpected response structure:', result);
                    throw new Error('Invalid response from server: ' + (result.message || 'Unknown error'));
                }

                // VALIDATION: Check if response matches the expected agent and panel
                if (result.data.panelId && result.data.panelId !== this.panelId) {
                    console.warn(`⚠️ Panel ID mismatch! Expected: ${this.panelId}, Received: ${result.data.panelId} - Ignoring response`);
                    // This response is for a different panel, ignore it
                    this.activeStreams.delete(streamId);
                    this.updateInputState();
                    return;
                }

                let responseContent = result.data.response;
                if (result.data.agentName && result.data.agentName !== requestAgentName) {
                    console.warn(`⚠️ Agent mismatch detected! Expected: ${requestAgentName}, Received: ${result.data.agentName}`);
                    responseContent += `\n\n_⚠️ Note: This response was generated by agent "${result.data.agentName}" but was requested from "${requestAgentName}"._`;
                }

                // Update assistant message with full response (no streaming for file uploads)
                console.log('Updating message with response:', responseContent.substring(0, 100));
                this.updateMessageContent(assistantMessageId, responseContent, false);

                // Remove stream tracking
                this.activeStreams.delete(streamId);
                this.updateInputState();
                return;
            }

            // Build URL with message, agentName, and panelId parameters for text-only messages
            // CRITICAL: Use the request agent name and panel ID (captured at send time)
            const streamUrl = `/api/v1/chat/stream?message=${encodeURIComponent(content)}${requestAgentName ? `&agentName=${encodeURIComponent(requestAgentName)}` : ''}${this.panelId ? `&panelId=${encodeURIComponent(this.panelId)}` : ''}`;

            // Use fetch with ReadableStream for SSE with authentication headers
            response = await fetch(
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

                            // VALIDATION: Check if response matches the expected agent and panel
                            if (completeData.panelId && completeData.panelId !== this.panelId) {
                                console.warn(`⚠️ Panel ID mismatch! Expected: ${this.panelId}, Received: ${completeData.panelId} - Ignoring response`);
                                // This response is for a different panel, ignore it
                                return;
                            }

                            if (completeData.agentName && completeData.agentName !== requestAgentName) {
                                console.warn(`⚠️ Agent mismatch detected! Expected: ${requestAgentName}, Received: ${completeData.agentName}`);
                                // Add a warning to the message
                                this.appendToMessage(assistantMessageId,
                                    `\n\n_⚠️ Note: This response was generated by agent "${completeData.agentName}" but was requested from "${requestAgentName}"._`);
                            }

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

        } catch (error) {
            console.error('Failed to send message:', error);

            // Show error in the message
            const errorMessage = '\n\n**Error:** ' + error.message;
            this.appendToMessage(assistantMessageId, errorMessage);

            // CRITICAL: Always finalize the message to remove streaming indicator
            this.finalizeMessage(assistantMessageId, { error: true });

            // Force immediate UI update to show error state
            requestAnimationFrame(() => {
                const messageElement = this.querySelector(`[data-message-id="${assistantMessageId}"]`);
                if (messageElement) {
                    messageElement.classList.add('error-message');

                    // Ensure streaming indicator is removed
                    const streamingIndicator = messageElement.querySelector('.streaming-indicator');
                    if (streamingIndicator) {
                        streamingIndicator.remove();
                    }
                }
            });
        } finally {
            // CRITICAL: Always remove stream from active set and update UI
            this.activeStreams.delete(streamId);
            this.updateInputState();

            // Focus input if no other streams are active
            if (this.activeStreams.size === 0) {
                input.focus();
            }

            // Force final render to ensure clean state
            requestAnimationFrame(() => {
                this.renderMessages();
            });
        }
    }

    updateInputState() {
        const sendBtn = this.querySelector('#sendBtn');
        const input = this.querySelector('#messageInput');

        if (!sendBtn || !input) return;

        // Update button icon to show active streams
        if (this.activeStreams.size > 0) {
            sendBtn.innerHTML = `
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <polyline points="12 6 12 12 16 14"></polyline>
                </svg>
            `;
            sendBtn.classList.add('processing');
            sendBtn.title = `Processing (${this.activeStreams.size} active)`;
        } else {
            sendBtn.innerHTML = `
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="12" y1="19" x2="12" y2="5"></line>
                    <polyline points="5 12 12 5 19 12"></polyline>
                </svg>
            `;
            sendBtn.classList.remove('processing');
            sendBtn.title = 'Send message';
        }
    }

    addMessage(message) {
        this.messages.push(message);
        this.appendMessageToDOM(message);
    }

    addCollaborationMessage(collabMsg) {
        const messagesContainer = this.querySelector('#chatMessages');

        // Remove welcome message if it exists
        const welcomeMsg = messagesContainer.querySelector('.welcome-message');
        if (welcomeMsg) {
            welcomeMsg.remove();
        }

        const timestamp = new Date(collabMsg.timestamp).toLocaleTimeString();
        let messageHTML = '';

        switch (collabMsg.type) {
            case 'tool_call':
                messageHTML = `
                    <div class="message message-collaboration message-tool-call" data-timestamp="${collabMsg.timestamp}">
                        <div class="message-avatar">🔧</div>
                        <div class="message-bubble">
                            <div class="message-header">
                                <span class="message-role">[${collabMsg.fromAgent}] Tool Call</span>
                                <span class="message-time">${timestamp}</span>
                            </div>
                            <div class="message-content">
                                Calling tool: <strong>${collabMsg.toolName}</strong>
                            </div>
                        </div>
                    </div>
                `;
                break;

            case 'tool_result':
                const statusIcon = collabMsg.success ? '✓' : '✗';
                const statusClass = collabMsg.success ? 'success' : 'error';
                messageHTML = `
                    <div class="message message-collaboration message-tool-result ${statusClass}" data-timestamp="${collabMsg.timestamp}">
                        <div class="message-avatar">${statusIcon}</div>
                        <div class="message-bubble">
                            <div class="message-header">
                                <span class="message-role">Result: ${collabMsg.toolName}</span>
                                <span class="message-time">${collabMsg.responseTimeMs}ms</span>
                            </div>
                            <div class="message-content">
                                ${this.truncate(collabMsg.toolResult, 200)}
                            </div>
                        </div>
                    </div>
                `;
                break;

            case 'agent_communication':
                messageHTML = `
                    <div class="message message-collaboration message-agent-comm" data-timestamp="${collabMsg.timestamp}">
                        <div class="message-avatar">🔄</div>
                        <div class="message-bubble">
                            <div class="message-header">
                                <span class="message-role">${collabMsg.fromAgent} → ${collabMsg.toAgent}</span>
                                <span class="message-time">${timestamp}</span>
                            </div>
                            <div class="message-content">
                                ${this.renderMarkdown(collabMsg.message)}
                            </div>
                        </div>
                    </div>
                `;
                break;

            case 'agent_response':
                messageHTML = `
                    <div class="message message-collaboration message-agent-response" data-timestamp="${collabMsg.timestamp}">
                        <div class="message-avatar">📥</div>
                        <div class="message-bubble">
                            <div class="message-header">
                                <span class="message-role">${collabMsg.fromAgent} → ${collabMsg.toAgent}</span>
                                <span class="message-time">${collabMsg.responseTimeMs}ms</span>
                            </div>
                            <div class="message-content">
                                ${this.renderMarkdown(this.truncate(collabMsg.response, 200))}
                            </div>
                        </div>
                    </div>
                `;
                break;
        }

        if (messageHTML) {
            // Insert in chronological order based on timestamp
            const newTimestamp = new Date(collabMsg.timestamp).getTime();
            const allMessages = Array.from(messagesContainer.querySelectorAll('.message:not(.message-waiting)'));

            let insertPosition = null;

            // Find the correct position based on timestamp (iterate forward for ascending order)
            for (let i = 0; i < allMessages.length; i++) {
                const existingMsg = allMessages[i];
                const existingTimestamp = this.parseMessageTimestamp(existingMsg);

                if (existingTimestamp && newTimestamp < existingTimestamp) {
                    // New message is older, insert before this message
                    insertPosition = existingMsg;
                    break;
                }
            }

            if (insertPosition) {
                // Insert before the found position (this message is newer than our message)
                insertPosition.insertAdjacentHTML('beforebegin', messageHTML);
            } else {
                // Message is newest, append at the end (before waiting indicators)
                const firstWaiting = messagesContainer.querySelector('.message-waiting');
                if (firstWaiting) {
                    firstWaiting.insertAdjacentHTML('beforebegin', messageHTML);
                } else {
                    messagesContainer.insertAdjacentHTML('beforeend', messageHTML);
                }
            }

            // Force reflow
            void messagesContainer.offsetHeight;

            // Highlight code blocks if any - find the newly added message
            const newMessage = Array.from(messagesContainer.querySelectorAll('.message')).find(
                msg => msg.getAttribute('data-timestamp') === collabMsg.timestamp
            );
            if (newMessage) {
                this.highlightCode(newMessage);
            }

            // Scroll to bottom
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
    }

    truncate(text, maxLength) {
        if (!text) return '';
        if (text.length <= maxLength) return text;
        return text.substring(0, maxLength) + '...';
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

        // Build attachments HTML if present
        let attachmentsHTML = '';
        if (msg.attachments && msg.attachments.length > 0) {
            attachmentsHTML = `
                <div class="message-attachments">
                    ${msg.attachments.map(att => {
                        // Check if attachment is an image with base64 data
                        if (att.mimeType && att.mimeType.startsWith('image/') && att.contentBase64) {
                            // Display actual image for assistant responses with generated images
                            return `
                                <div class="attachment-image">
                                    <img src="data:${att.mimeType};base64,${att.contentBase64}"
                                         alt="${att.filename || 'Generated image'}"
                                         loading="lazy"
                                         onclick="window.open(this.src, '_blank')"
                                         style="max-width: 100%; height: auto; cursor: pointer; border-radius: 8px; margin-top: 8px;" />
                                    ${att.filename ? `<div class="attachment-filename" style="margin-top: 4px; font-size: 0.85em; opacity: 0.7;">${att.filename}</div>` : ''}
                                </div>
                            `;
                        } else {
                            // Display file icon for other attachments
                            return `
                                <div class="attachment-item">
                                    <span class="attachment-icon">${this.getFileIcon(att.mimeType)}</span>
                                    <span class="attachment-name">${att.filename}</span>
                                    <span class="attachment-size">${this.formatFileSize(att.fileSize)}</span>
                                </div>
                            `;
                        }
                    }).join('')}
                </div>
            `;
        }

        const messageHTML = `
            <div class="message ${isUser ? 'message-user' : 'message-assistant'} ${colorClass}" data-message-id="${messageId}" data-agent="${isUser ? '' : agentName}" data-timestamp="${msg.timestamp}">
                <div class="message-avatar">
                    ${isUser ? '👤' : '🤖'}
                </div>
                <div class="message-bubble">
                    <div class="message-header">
                        <span class="message-role">${isUser ? 'You' : agentName}</span>
                        <span class="message-time">${timestamp}</span>
                    </div>
                    ${attachmentsHTML}
                    <div class="message-content">
                        ${this.renderMarkdown(msg.content)}
                    </div>
                    ${msg.streaming ? '<div class="streaming-indicator"><span></span><span></span><span></span></div>' : ''}
                </div>
            </div>
        `;

        // Insert message in chronological order based on timestamp
        const newTimestamp = new Date(msg.timestamp).getTime();
        const allMessages = Array.from(messagesContainer.querySelectorAll('.message:not(.message-waiting)'));

        let insertPosition = null;

        // Find the correct position based on timestamp (iterate forward for ascending order)
        for (let i = 0; i < allMessages.length; i++) {
            const existingMsg = allMessages[i];
            const existingTimestamp = this.parseMessageTimestamp(existingMsg);

            if (existingTimestamp && newTimestamp < existingTimestamp) {
                // New message is older, insert before this message
                insertPosition = existingMsg;
                break;
            }
        }

        if (insertPosition) {
            // Insert before the found position (this message is newer than our message)
            insertPosition.insertAdjacentHTML('beforebegin', messageHTML);
        } else {
            // Message is newest, append at the end (before waiting indicators)
            const firstWaiting = messagesContainer.querySelector('.message-waiting');
            if (firstWaiting) {
                firstWaiting.insertAdjacentHTML('beforebegin', messageHTML);
            } else {
                messagesContainer.insertAdjacentHTML('beforeend', messageHTML);
            }
        }

        // Force reflow to ensure rendering
        void messagesContainer.offsetHeight;

        // Highlight code blocks - find the newly added message
        const newMessage = messagesContainer.querySelector(`[data-message-id="${messageId}"]`);
        if (newMessage) {
            this.highlightCode(newMessage);
        }

        // Scroll to bottom
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    parseMessageTimestamp(messageElement) {
        // Try to get timestamp from data attribute if available
        const timestamp = messageElement.getAttribute('data-timestamp');
        if (timestamp) {
            return new Date(timestamp).getTime();
        }

        // Fallback: try to parse from the time display
        const timeElement = messageElement.querySelector('.message-time');
        if (timeElement) {
            const timeText = timeElement.textContent;
            // This is a time string like "2:30:45 PM", we need the full date
            // For now, assume today's date
            const today = new Date();
            const datePart = today.toDateString();
            try {
                return new Date(datePart + ' ' + timeText).getTime();
            } catch (e) {
                return null;
            }
        }

        return null;
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
        if (data.modelUsed) {
            message.modelUsed = data.modelUsed;
        }

        // Add attachments if present (e.g., generated images)
        if (data.attachments && data.attachments.length > 0) {
            message.attachments = data.attachments;
            console.log('📎 Added', data.attachments.length, 'attachment(s) to message');
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
            if (!message) return;

            // Update content
            const contentDiv = messageElement.querySelector('.message-content');
            if (contentDiv) {
                contentDiv.innerHTML = this.renderMarkdown(message.content);
                void contentDiv.offsetHeight;
                this.highlightCode(contentDiv);
            }

            // Update or add attachments if present
            if (message.attachments && message.attachments.length > 0) {
                // Check if attachments container already exists
                let attachmentsContainer = messageElement.querySelector('.message-attachments');

                if (!attachmentsContainer) {
                    // Create attachments container before content
                    const messageBubble = messageElement.querySelector('.message-bubble');
                    const messageHeader = messageBubble.querySelector('.message-header');

                    attachmentsContainer = document.createElement('div');
                    attachmentsContainer.className = 'message-attachments';

                    // Insert after header, before content
                    if (messageHeader && messageHeader.nextSibling) {
                        messageBubble.insertBefore(attachmentsContainer, messageHeader.nextSibling);
                    }
                }

                // Render attachments
                if (attachmentsContainer) {
                    attachmentsContainer.innerHTML = message.attachments.map(att => {
                        // Check if attachment is an image with base64 data
                        if (att.mimeType && att.mimeType.startsWith('image/') && att.contentBase64) {
                            // Display actual image
                            return `
                                <div class="attachment-image">
                                    <img src="data:${att.mimeType};base64,${att.contentBase64}"
                                         alt="${att.filename || 'Generated image'}"
                                         loading="lazy"
                                         onclick="window.open(this.src, '_blank')"
                                         style="max-width: 100%; height: auto; cursor: pointer; border-radius: 8px; margin-top: 8px;" />
                                    ${att.filename ? `<div class="attachment-filename" style="margin-top: 4px; font-size: 0.85em; opacity: 0.7;">${att.filename}</div>` : ''}
                                </div>
                            `;
                        } else {
                            // Display file icon for other attachments
                            return `
                                <div class="attachment-item">
                                    <span class="attachment-icon">${this.getFileIcon(att.mimeType)}</span>
                                    <span class="attachment-name">${att.filename}</span>
                                    <span class="attachment-size">${this.formatFileSize(att.fileSize)}</span>
                                </div>
                            `;
                        }
                    }).join('');
                }
            }

            // Scroll to bottom
            const messagesContainer = this.querySelector('#chatMessages');
            requestAnimationFrame(() => {
                messagesContainer.scrollTop = messagesContainer.scrollHeight;
            });
        }
    }

    updateMessageContent(messageId, content, streaming = false) {
        const message = this.messages.find(m => m.id === messageId);
        if (message) {
            message.content = content;
            message.streaming = streaming;

            // If not streaming, finalize the message to remove streaming indicator
            if (!streaming) {
                this.finalizeMessage(messageId, {});
            } else {
                this.updateMessage(messageId);
            }
        }
    }

    formatFileSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
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

        // Save collaboration messages and waiting indicators before clearing
        const collaborationMessages = Array.from(messagesContainer.querySelectorAll('.message-collaboration'));
        const waitingIndicators = Array.from(messagesContainer.querySelectorAll('.message-waiting'));

        // Extract collaboration message data with timestamps for sorting
        const collabData = collaborationMessages.map(el => ({
            html: el.outerHTML,
            timestamp: el.getAttribute('data-timestamp')
        }));

        const messagesHTML = this.messages.map(msg => {
            const timestamp = new Date(msg.timestamp).toLocaleTimeString();
            const isUser = msg.role === 'user';
            const messageId = msg.id || 'msg-' + timestamp;

            // Use the agent name from the message
            const agentName = isUser ? 'You' : (msg.agentName || 'Assistant');
            const colorClass = isUser ? '' : this.getAgentColorClass(msg.agentName || 'Assistant');

            let metadata = '';
            if (msg.tokensUsed || msg.processingTime || msg.modelUsed) {
                metadata = `
                    <div class="message-metadata">
                        ${msg.modelUsed ? `<span class="model-badge">🤖 ${msg.modelUsed}</span>` : ''}
                        ${msg.tokensUsed ? `<span>Tokens: ${msg.tokensUsed}</span>` : ''}
                        ${msg.processingTime ? `<span>Time: ${msg.processingTime}ms</span>` : ''}
                    </div>
                `;
            }

            // Build attachments HTML if present
            let attachmentsHTML = '';
            if (msg.attachments && msg.attachments.length > 0) {
                attachmentsHTML = `
                    <div class="message-attachments">
                        ${msg.attachments.map(att => {
                            // Check if attachment is an image with base64 data
                            if (att.mimeType && att.mimeType.startsWith('image/') && att.contentBase64) {
                                // Display actual image for assistant responses with generated images
                                return `
                                    <div class="attachment-image">
                                        <img src="data:${att.mimeType};base64,${att.contentBase64}"
                                             alt="${att.filename || 'Generated image'}"
                                             loading="lazy"
                                             onclick="window.open(this.src, '_blank')"
                                             style="max-width: 100%; height: auto; cursor: pointer; border-radius: 8px; margin-top: 8px;" />
                                        ${att.filename ? `<div class="attachment-filename" style="margin-top: 4px; font-size: 0.85em; opacity: 0.7;">${att.filename}</div>` : ''}
                                    </div>
                                `;
                            } else {
                                // Display file icon for other attachments
                                return `
                                    <div class="attachment-item">
                                        <span class="attachment-icon">${this.getFileIcon(att.mimeType)}</span>
                                        <span class="attachment-name">${att.filename}</span>
                                        <span class="attachment-size">${this.formatFileSize(att.fileSize)}</span>
                                    </div>
                                `;
                            }
                        }).join('')}
                    </div>
                `;
            }

            return {
                html: `
                    <div class="message ${isUser ? 'message-user' : 'message-assistant'} ${colorClass}" data-message-id="${messageId}" data-agent="${isUser ? '' : agentName}" data-timestamp="${msg.timestamp}">
                        <div class="message-avatar">
                            ${isUser ? '👤' : '🤖'}
                        </div>
                        <div class="message-bubble">
                            <div class="message-header">
                                <span class="message-role">${isUser ? 'You' : agentName}</span>
                                <span class="message-time">${timestamp}</span>
                            </div>
                            ${attachmentsHTML}
                            <div class="message-content">
                                ${this.renderMarkdown(msg.content)}
                            </div>
                            ${metadata}
                            ${msg.streaming ? '<div class="streaming-indicator"><span></span><span></span><span></span></div>' : ''}
                        </div>
                    </div>
                `,
                timestamp: msg.timestamp
            };
        });

        // Merge conversation and collaboration messages, sorted by timestamp (ascending - oldest first)
        const allMessages = [...messagesHTML, ...collabData]
            .filter(m => m.timestamp) // Filter out any without timestamps
            .sort((a, b) => {
                const timeA = new Date(a.timestamp).getTime();
                const timeB = new Date(b.timestamp).getTime();
                // Ascending: oldest messages first (top of chat), newest last (bottom of chat)
                return timeA - timeB;
            });

        // Render all messages in chronological order
        messagesContainer.innerHTML = allMessages.map(m => m.html).join('');

        // Restore waiting indicators at the end (they don't have timestamps)
        if (waitingIndicators.length > 0) {
            waitingIndicators.forEach(el => {
                messagesContainer.insertAdjacentHTML('beforeend', el.outerHTML);
            });
        }

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
