# T1 Super AI

<div align="center">
  <img src="doc/logo.png" alt="T1 Super AI Logo" width="200"/>

  ### *Advanced Multi-Agent AI Platform with Self-Evolution and Inter-Agent Communication*

  [![Java](https://img.shields.io/badge/Java-25-orange?style=flat&logo=openjdk)](https://openjdk.org/)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
  [![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M2-blue?style=flat)](https://spring.io/projects/spring-ai)
  [![License](https://img.shields.io/badge/License-Creative%20Commons%20Attribution--ShareAlike%204.0%20International%20Public%20License-yellow?style=flat)](LICENSE)
</div>

---

## 🎭 Overview

**T1 Super AI** is an advanced AI agent platform built with Spring Boot 4 and Java 25, designed to create, manage, and orchestrate multiple autonomous AI agents with distinct personalities and capabilities. The platform enables agents to:

- 🧠 **Operate autonomously** with independent decision-making
- 🤝 **Communicate and collaborate** with other agents
- 🛠️ **Create their own tools** dynamically at runtime
- 🔄 **Self-evolve** through learning and experience
- 🎯 **Execute complex tasks** via REST API or interactive CLI

This platform represents a comprehensive solution for building multi-agent AI systems where agents can learn, adapt, and work together to solve complex problems.

---

## 🌟 Key Features

### 🤖 Multi-Agent Orchestration
- Create agents with unique personalities, roles, and specialized capabilities
- Agents communicate, collaborate, and coordinate on complex tasks
- Each agent maintains isolated memory and learning context
- Support for OpenAI, Anthropic Claude, and Ollama models
- Dynamic agent creation and configuration through API or CLI

### 🎨 Agent Personalization & Character
Each agent can be customized with:
- **Personality Traits**: Define communication style, problem-solving approach, and behavioral patterns
- **Role Specialization**: Assign specific domains of expertise (e.g., data analysis, creative writing, code review)
- **Custom Prompts**: Configure system prompts that shape agent behavior and responses
- **Capability Sets**: Define which tools and functions each agent can access
- **Model Selection**: Choose the underlying AI model best suited for the agent's role

### 🧠 Self-Evolution & Learning
Agents continuously improve through:
- **Experience-Based Learning**: Agents learn from successful and failed task executions
- **Pattern Recognition**: Identify recurring problems and develop optimized solutions
- **Tool Discovery**: Agents evaluate their capabilities and identify gaps, creating new tools as needed
- **Knowledge Synthesis**: Combine information from multiple interactions to build comprehensive understanding
- **Adaptive Behavior**: Adjust response strategies based on user feedback and task outcomes
- **Memory Consolidation**: Important learnings are persisted to vector memory for long-term retention

### 🤝 Inter-Agent Communication & Collaboration
- **Direct Messaging**: Agents can send structured messages to specific agents
- **Broadcast Communication**: Share information with all agents in the system
- **Task Delegation**: Agents can assign subtasks to other agents based on their specializations
- **Knowledge Sharing**: Agents exchange learned patterns and successful strategies
- **Collaborative Problem Solving**: Multiple agents work together on complex, multi-faceted problems
- **Consensus Building**: Agents can vote or discuss to reach optimal solutions

### 🧰 Autonomous Tool Creation
Agents autonomously extend their capabilities by:
- **Runtime Tool Registration**: Create and register new tools without system restart
- **Tool Discovery**: Identify needed functionality and implement appropriate solutions
- **Parameter Definition**: Self-define tool schemas and validation rules
- **Tool Sharing**: Created tools can be shared with other agents
- **Version Management**: Tools evolve through iterations and improvements

### 💾 Vector Memory & Embeddings
- **Long-term Memory**: Persistent storage using vector databases for semantic retrieval
- **Contextual Recall**: Retrieve relevant memories based on semantic similarity
- **Memory Isolation**: Each agent maintains separate memory spaces
- **Cross-Agent Memory Sharing**: Selectively share memories between collaborating agents
- **SimpleVectorStore**: Built-in vector store for quick setup
- **Redis Support**: Planned support for distributed vector storage

### 🌐 Dual Launch Modes
- **Web Application Mode (Default)**: Browser-based UI with REST API, WebSocket support, and multi-user access
- **CLI Mode (Standalone)**: Rich terminal interface with split-pane design for single-user interaction without web server

### 🔒 Security & Authentication
- **API Key Authentication**: Secure access control for REST endpoints
- **WebSocket Security**: Encrypted real-time communication channels
- **Agent Isolation**: Security boundaries between agent contexts

### 📚 OpenAPI Documentation
Complete API documentation with Swagger UI for interactive exploration and testing.

---

## 🚀 Quick Start

### Prerequisites

Before getting started, ensure you have:

- **Java 25** with preview features enabled
- **Maven 3.9+** for build management
- **API Keys** for your chosen AI provider(s):
  - OpenAI API key (for GPT models)
  - Anthropic API key (for Claude models)
  - Or a locally running Ollama instance (for open-source models)

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/t1-super-ai.git
cd t1-super-ai

# Build the project
mvn clean package -DskipTests
```

### Launch Modes

T1 Super AI can run in two modes:

#### 1. Web Application Mode (Default)
Browser-based UI with REST API support:
```bash
# Run the web application (default)
java -jar target/t1-super-ai-1.0.0-SNAPSHOT.jar

# Or use the launch script
./run-webapp.sh      # Linux/Mac
run-webapp.bat       # Windows
```
Access the web UI at: **http://localhost:8080**

#### 2. CLI Mode (Standalone)
Terminal-based interface without web server:
```bash
# Run in CLI mode
java -jar target/t1-super-ai-1.0.0-SNAPSHOT.jar --spring.profiles.active=cli

# Or use the launch script
./run-cli.sh         # Linux/Mac
run-cli.bat          # Windows
```

See **[LAUNCH_MODES.md](LAUNCH_MODES.md)** for detailed comparison and usage instructions.

### Initial Setup

On first run, T1 Super AI will automatically initialize a workspace directory at `~/.t1-super-ai/` and guide you through configuration setup. The system will create:

```
~/.t1-super-ai/
├── config.json              # Main configuration file
├── CHARACTER.md             # Default agent character/personality
├── USAGE.md                 # Usage instructions
├── README.md                # Workspace overview
├── QUICK_REFERENCE.md       # Quick command reference
├── CONFIGURATION_GUIDE.md   # Configuration details
├── agents/                  # Individual agent configurations
├── tools/                   # Custom tool definitions
├── plugins/                 # Plugin extensions
├── mcp-servers/             # MCP server configurations
└── skills/                  # Custom skills
```

The `config.json` file will be created with your LLM provider settings:

```json
{
  "defaultProvider": "OPENAI",
  "llmConfigs": {
    "OPENAI": {
      "apiKey": "your-openai-api-key",
      "baseUrl": "https://api.openai.com/v1",
      "model": "gpt-4",
      "additionalParams": {}
    },
    "ANTHROPIC": {
      "apiKey": "your-anthropic-api-key",
      "baseUrl": "https://api.anthropic.com",
      "model": "claude-3-5-sonnet-20241022",
      "additionalParams": {}
    },
    "OLLAMA": {
      "apiKey": null,
      "baseUrl": "http://localhost:11434",
      "model": "llama3.2",
      "additionalParams": {}
    }
  },
  "taskModels": {},
  "mcpServers": [],
  "systemSettings": {
    "enableFileSystem": true,
    "enableWebAccess": true,
    "enableBashExecution": true,
    "workingDirectory": "/your/working/directory",
    "maxTokens": 4096,
    "temperature": 0.7
  }
}
```

You can configure only the providers you plan to use. The wizard will prompt you for API keys during initial setup.

---

## 🎮 Usage

### CLI Mode

Launch the interactive CLI:

```bash
java -jar target/t1-super-ai-1.0.0-SNAPSHOT.jar
```

On first run, you'll be guided through initial configuration. The system will create the workspace directory and help you set up your LLM providers.

#### Agent Creation Process

Creating a new agent involves an interactive wizard that personalizes your agent:

```bash
/agent create <agent-name>
```

The agent creation wizard will ask you to define:

1. **Agent Role** - What role will this agent play? (e.g., Code Reviewer, Data Analyst, DevOps Engineer)
2. **Agent Purpose** - What is this agent's primary purpose? (e.g., Review code for security issues, Analyze data patterns)
3. **Personality Traits** - What personality should this agent have? (e.g., Professional and detail-oriented, Helpful and encouraging)
4. **Communication Style** - How should the agent communicate? (e.g., Concise and technical, Detailed with examples)
5. **Special Abilities** - Any special skills or knowledge areas? (e.g., Expert in Python and Java, Specializes in statistical analysis)
6. **Model Preferences** - Which LLM model works best for this role?

After creation, the agent will have its own directory structure:

```
~/.t1-super-ai/agents/<agent-name>/
├── config.json      # Agent-specific configuration
├── CHARACTER.md     # Agent personality and role definition
└── USAGE.md         # Agent-specific usage guide
```

#### Available Commands

Once you're in the CLI, you can use:

- `/agent create <agent-name>` - Create a new AI agent through interactive wizard
- `/agent list` - View all agents
- `/agent use <agent-name>` - Switch to a specific agent
- `/agent remove <agent-name>` - Remove an agent
- `/config` - Run configuration wizard
- `/provider [name]` - Switch LLM provider (openai, anthropic, ollama)
- `/model [name]` - Change or view current model
- `/help` - Display all available commands
- Simply type your message to chat with the currently active agent

### REST API Mode

The API runs on `http://localhost:8080` by default.

#### Create an Agent

```bash
curl -X POST http://localhost:8080/api/agents \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Einstein",
    "personality": "Brilliant physicist with a sense of humor",
    "model": "gpt-4",
    "capabilities": ["reasoning", "mathematics", "physics"]
  }'
```

#### Chat with an Agent

```bash
curl -X POST http://localhost:8080/api/agents/Einstein/chat \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Explain quantum entanglement like I'm five"
  }'
```

### Swagger UI

Visit `http://localhost:8080/swagger-ui.html` for interactive API documentation and testing.

---

## 📁 Workspace Structure

T1 Super AI uses a workspace directory at `~/.t1-super-ai/` to manage all configuration, agents, and extensions. This design allows for:
- Clean separation from the application code
- Easy backup and version control of your agents
- Hot-reloading of configurations without restart
- Shared resources across multiple agent instances

### Workspace Directory Layout

```
~/.t1-super-ai/
│
├── config.json                    # Main system configuration
│   ├── LLM provider settings (OpenAI, Anthropic, Ollama)
│   ├── Default models and parameters
│   ├── System permissions and settings
│   └── MCP server configurations
│
├── CHARACTER.md                   # Default agent character template
├── USAGE.md                       # System usage instructions
├── README.md                      # Workspace documentation
├── QUICK_REFERENCE.md             # Command quick reference
├── CONFIGURATION_GUIDE.md         # Configuration documentation
│
├── agents/                        # Agent-specific configurations
│   ├── <agent-name-1>/
│   │   ├── config.json           # Agent configuration
│   │   │   ├── Model selection
│   │   │   ├── Temperature, max tokens
│   │   │   ├── System prompt
│   │   │   └── Enabled tools
│   │   ├── CHARACTER.md          # Agent personality definition
│   │   └── USAGE.md              # Agent-specific usage guide
│   │
│   └── <agent-name-2>/
│       ├── config.json
│       ├── CHARACTER.md
│       └── USAGE.md
│
├── tools/                         # Custom tool definitions
│   ├── <tool-name>.json          # Tool configuration
│   └── <tool-name>.js/.py        # Tool implementation (optional)
│
├── plugins/                       # Plugin extensions
│   ├── <plugin-name>/
│   │   ├── plugin.json           # Plugin metadata
│   │   └── implementation files
│   └── ...
│
├── mcp-servers/                   # MCP (Model Context Protocol) servers
│   ├── <server-name>/
│   │   ├── config.json           # Server configuration
│   │   └── server files
│   └── ...
│
└── skills/                        # Custom skills
    ├── <skill-name>.json         # Skill definition
    └── ...
```

### Agent Configuration Structure

Each agent has its own `config.json` in `~/.t1-super-ai/agents/<agent-name>/`:

```json
{
  "name": "DataScientist",
  "provider": "OPENAI",
  "model": "gpt-4",
  "systemPrompt": "You are an expert data scientist...",
  "temperature": 0.3,
  "maxTokens": 4096,
  "enabledTools": [
    "bash",
    "read_file",
    "write_file",
    "web_fetch",
    "send_message_to_agent"
  ],
  "customSettings": {
    "responseStyle": "technical",
    "verbosity": "detailed"
  }
}
```

### CHARACTER.md Format

Each agent's `CHARACTER.md` defines its personality and capabilities:

```markdown
# Agent: DataScientist

## Role
Expert Data Scientist specializing in statistical analysis and machine learning

## Purpose
Analyze datasets, build predictive models, and provide data-driven insights

## Personality Traits
- Analytical and methodical
- Detail-oriented with strong statistical reasoning
- Patient in explaining complex concepts

## Communication Style
- Prefers data visualization and concrete examples
- Uses statistical terminology appropriately
- Provides step-by-step explanations

## Special Abilities
- Expert in Python (pandas, scikit-learn, matplotlib)
- Statistical modeling and hypothesis testing
- Data cleaning and feature engineering
- Model evaluation and optimization

## Preferred Tools
- Python for data analysis
- Jupyter notebooks for exploration
- Statistical libraries and frameworks
```

### Hot-Reloading

The system watches for changes in the workspace directory and automatically reloads:
- Agent configurations when `config.json` files change
- Tool definitions when added/modified in `tools/`
- Plugin configurations when updated
- MCP server settings

This allows you to update agent personalities, add new tools, or modify configurations without restarting the application.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                  REST API Layer                     │
│         (Spring Boot 4 + Spring Security)           │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│              Agent Orchestrator                     │
│      (Multi-agent coordination & routing)           │
└─────────────────┬───────────────────────────────────┘
                  │
       ┌──────────┼──────────┐
       │          │          │
┌──────▼───┐ ┌───▼────┐ ┌───▼────┐
│ Agent 1  │ │Agent 2 │ │Agent N │
│ (OpenAI) │ │(Claude)│ │(Ollama)│
└──────┬───┘ └───┬────┘ └───┬────┘
       │         │          │
       └─────────┼──────────┘
                 │
┌────────────────▼────────────────────────────────────┐
│           Vector Memory Store                       │
│    (Long-term memory + Semantic search)             │
└─────────────────────────────────────────────────────┘
```

---

## 🧪 Advanced Features

### Agent Personality Configuration

Create agents with distinct personalities and specializations:

```java
AgentConfig config = AgentConfig.builder()
    .name("DataScientist")
    .personality("Analytical, detail-oriented, and methodical")
    .role("Data Analysis Specialist")
    .model("gpt-4")
    .systemPrompt("You are an expert data scientist specializing in statistical analysis...")
    .capabilities(List.of("data_analysis", "statistics", "visualization"))
    .temperature(0.3)  // Lower temperature for more focused responses
    .build();
```

### Self-Evolution Mechanism

Agents evolve their capabilities over time:

```java
// Agent identifies a capability gap
agent.analyzeTaskFailure(task);
// Output: "Missing tool for parsing CSV files"

// Agent creates the needed tool
agent.evolve("CreateCSVParser", toolDefinition);

// Tool is now available for future tasks
agent.execute(similarTask);  // Succeeds with new tool
```

**Evolution Triggers:**
- Task failures due to missing capabilities
- Recognition of repetitive manual operations
- Identification of inefficiencies in current workflows
- User feedback indicating desired improvements

### Inter-Agent Communication Patterns

**Direct Request-Response:**
```java
// Agent A requests specialized help
Message request = Message.builder()
    .from("GeneralistAgent")
    .to("DataScientist")
    .content("Analyze this dataset for trends")
    .context(dataContext)
    .build();

Response response = agentB.receive(request);
```

**Collaborative Problem Solving:**
```java
// Multi-agent collaboration on complex task
Task complexTask = new Task("Build predictive model");

// Orchestrator delegates subtasks
orchestrator.delegate("DataScientist", "Perform feature engineering");
orchestrator.delegate("MLEngineer", "Train model");
orchestrator.delegate("CodeReviewer", "Review implementation");

// Agents share progress and results
agents.forEach(agent -> agent.shareProgress());
```

**Knowledge Broadcasting:**
```java
// Agent discovers useful information and broadcasts
agent.broadcast("Discovered: API rate limit is 100 req/min");

// Other agents update their knowledge base
allAgents.forEach(a -> a.updateKnowledge(broadcast));
```

### Autonomous Tool Creation

Agents create tools based on identified needs:

```java
// Agent encounters a new requirement
agent.processRequest("Fetch weather data for analysis");

// Agent identifies missing capability and creates tool
ToolDefinition weatherTool = agent.designTool(
    "FetchWeather",
    "Retrieves current weather data for specified location",
    parameters -> {
        // Auto-generated implementation
        return weatherAPI.getCurrentWeather(parameters.get("location"));
    }
);

// Register tool for immediate use
agent.registerTool(weatherTool);

// Share with other agents who might benefit
agent.shareToolWith("DataScientist", weatherTool);
```

### Vector Memory & Learning

**Experience Storage:**
```java
// Agent stores successful interaction pattern
agent.remember(Memory.builder()
    .content("User prefers detailed technical explanations")
    .category("communication_preference")
    .importance(0.9)
    .build());

// Store learned problem-solving strategy
agent.remember(Memory.builder()
    .content("For performance optimization, profile first, then optimize")
    .category("best_practice")
    .metadata(Map.of("domain", "performance", "success_rate", 0.95))
    .build());
```

**Contextual Recall:**
```java
// Retrieve relevant memories for current context
List<Memory> relevantExperiences = agent.recall(
    "How should I explain technical concepts?",
    similarityThreshold = 0.8,
    maxResults = 5
);

// Apply learned patterns to current task
agent.applyLearnings(relevantExperiences, currentTask);
```

**Cross-Agent Learning:**
```java
// Agent shares successful strategy with team
agent.shareMemory("OptimizationStrategy", targetAgents);

// Other agents incorporate the learning
targetAgents.forEach(a -> a.integrateSharedKnowledge(memory));
```

### Adaptive Behavior

Agents adjust their behavior based on feedback and outcomes:

```java
// Agent receives feedback
agent.processFeedback(Feedback.builder()
    .taskId("task-123")
    .rating(4.5)
    .comment("Good analysis but too verbose")
    .build());

// Agent updates internal parameters
agent.adapt(AdaptationStrategy.builder()
    .parameter("response_length")
    .adjustment(-0.2)  // Reduce verbosity
    .build());

// Future responses incorporate the adaptation
agent.adjustTemperature(0.7);  // More focused
agent.setMaxTokens(500);       // More concise
```

---

## 🤖 Agent Creation Deep Dive

Creating new agents in T1 Super AI involves an interactive wizard that creates a fully personalized agent based on your specifications.

### Agent Personalization

Each agent is given a character, purpose, and personality from the moment of creation. Agents aren't just configured—they emerge with their own unique identity and capabilities.

### The Agent Creation Wizard Flow

1. **Initiate Agent Creation**
   ```bash
   /agent create MyNewAgent
   ```

2. **Define Agent Role**
   - Prompted: "What role will this agent play?"
   - Examples: Code Reviewer, Data Analyst, DevOps Engineer, Creative Writer
   - This determines the agent's primary identity

3. **Specify Purpose**
   - Prompted: "What is this agent's primary purpose?"
   - Examples: Review code for security issues, Analyze business metrics, Automate deployments
   - Defines the agent's main objective

4. **Set Personality Traits**
   - Prompted: "What personality should this agent have?"
   - Examples: Professional and detail-oriented, Friendly and encouraging, Concise and direct
   - Shapes how the agent behaves and responds

5. **Choose Communication Style**
   - Prompted: "How should the agent communicate?"
   - Examples: Technical with code examples, Business-friendly language, Academic and formal
   - Determines the agent's tone and language

6. **Assign Special Abilities**
   - Prompted: "Any special skills or knowledge areas?"
   - Examples: Expert in Python and Java, AWS infrastructure specialist, Statistical modeling expert
   - Defines domain expertise

7. **Select Model**
   - Choose which LLM model best suits this agent's role
   - Options include GPT-4, Claude, or local Ollama models
   - Different models excel at different tasks

### What Gets Created

After agent creation completes, you'll have:

```
~/.t1-super-ai/agents/<agent-name>/
├── config.json      # Generated from wizard responses
├── CHARACTER.md     # Detailed personality profile
└── USAGE.md         # How to use this specific agent
```

### Post-Creation Customization

After creating an agent, you can further customize it by:

**Editing `config.json`:**
- Adjust temperature for more/less creative responses
- Change the model if performance isn't optimal
- Enable/disable specific tools
- Modify token limits

**Editing `CHARACTER.md`:**
- Refine personality traits
- Add more detailed knowledge areas
- Update communication preferences
- Document learned patterns

**Testing and Iteration:**
```bash
/agent use MyNewAgent
# Chat with the agent
# Observe behavior
# Edit ~/.t1-super-ai/agents/MyNewAgent/config.json or CHARACTER.md as needed
# Run /reload to apply changes
```

### Example: Creating a Security Auditor Agent

```
Command: /agent create SecurityAuditor

Step 1 - Role: Security Auditor
Step 2 - Purpose: Identify vulnerabilities and security best practices in code
Step 3 - Personality: Thorough, cautious, and security-focused
Step 4 - Communication: Direct and actionable with severity ratings
Step 5 - Special Abilities: OWASP Top 10, penetration testing, secure coding
Step 6 - Model: gpt-4 (for complex reasoning)

Result: Agent created at ~/.t1-super-ai/agents/SecurityAuditor/
```

The agent will now embody these characteristics in all interactions, automatically applying security-focused analysis to conversations and tasks.

---

## 📖 Documentation

We've got documentation for days! Check out these guides:

- **[API Documentation](API-DOCUMENTATION.md)** - Complete REST API reference
- **[API Authentication Guide](API-AUTHENTICATION-GUIDE.md)** - Security setup
- **[Agent Creation Examples](CREATE-AGENT-EXAMPLES.md)** - Sample agent configurations
- **[Inter-Agent Communication](INTER-AGENT-COMMUNICATION.md)** - Agent-to-agent messaging
- **[Vector Memory Guide](VECTOR_MEMORY_EMBEDDINGS.md)** - Memory storage and embeddings
- **[Swagger Quick Start](SWAGGER-QUICK-START.md)** - API documentation UI
- **[OpenAPI Implementation](OPENAPI-SWAGGER-IMPLEMENTATION.md)** - Swagger setup details
- **[Troubleshooting](EMBEDDINGS_TROUBLESHOOTING.md)** - When things go wrong

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 25 | Modern language features and preview APIs |
| Spring Boot | 4.0.3 | Application framework and dependency injection |
| Spring AI | 2.0.0-M2 | AI model integration and abstractions |
| Maven | 3.9+ | Build and dependency management |
| JLine | 3.27.1 | Advanced CLI capabilities |
| Jackson | Latest | JSON serialization and deserialization |
| Lombok | 1.18.40 | Code generation and boilerplate reduction |
| SpringDoc | 2.7.0 | OpenAPI documentation generation |

---

## ⚡ CLI Commands Reference

### Core Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/help` | Display all available commands with descriptions | `/help` |
| `/clear` | Clear the terminal screen and redraw banner | `/clear` |
| `/exit` or `/quit` | Exit the application | `/exit` |
| `/reload` | Reload CHARACTER.md and USAGE.md context from disk | `/reload` |

### Configuration Commands

| Command | Arguments | Description | Example |
|---------|-----------|-------------|---------|
| `/config` | None | Run interactive configuration wizard to set up LLM providers, API keys, and system settings | `/config` |
| `/provider` | `[name]` | View current provider or switch to a different LLM provider (openai, anthropic, ollama) | `/provider openai` |
| `/model` | `[name]` or `[number]` | View current model, show model selection menu, or switch to a specific model by name or menu number | `/model gpt-4o` or `/model 2` |
| `/auto-model` | `[on\|off]` | Enable/disable automatic model selection based on prompt classification. Shows current status if no argument | `/auto-model on` |
| `/classify` | `<prompt>` | Test prompt classification to see which task type and model would be selected (debugging tool) | `/classify Write a Python function` |

### Agent Management Commands

| Command | Arguments | Description | Example |
|---------|-----------|-------------|---------|
| `/agent create` | `<name>` | Create a new agent (forks current session) through interactive wizard. Prompts for role, purpose, personality, communication style, abilities, and model | `/agent create DataAnalyst` |
| `/agent list` | None | List all active agents with their status, configuration, creation time, and conversation count | `/agent list` |
| `/agent use` | `<name>` | Switch to a different agent for conversations | `/agent use DataAnalyst` |
| `/agent remove` | `<name>` | Delete an agent after confirmation. Cannot remove the master agent. | `/agent remove DataAnalyst` |

### Tool & Extension Commands

| Command | Arguments | Description | Example |
|---------|-----------|-------------|---------|
| `/mcp list` | None | List all connected MCP (Model Context Protocol) servers | `/mcp list` |
| `/mcp tools` | `<server-name>` | List all tools available from a specific MCP server. Use `local` to see core and plugin tools | `/mcp tools local` |
| `/plugin list` | None | List all loaded plugins with version, type, description, and tool count | `/plugin list` |
| `/plugin tools` | None | List all tools provided by loaded plugins | `/plugin tools` |
| `/plugin reload` | None | Reload all plugins from disk (hot-reload) | `/plugin reload` |
| `/skill list` | None | List all available skills | `/skill list` |
| `/skill create` | None | Launch skill creation wizard (coming soon) | `/skill create` |

### Chat & Messages

| Input | Description |
|-------|-------------|
| `<message>` | Any text without a leading `/` is sent as a chat message to the currently active agent |
| Multi-line input | The CLI supports typing multiple lines. Just keep typing - the agent won't process until you submit |

---

## 📘 Detailed Command Descriptions

### Configuration Management

#### `/config` - Configuration Wizard
Launches an interactive wizard that guides you through:
- Setting up LLM providers (OpenAI, Anthropic, Ollama)
- Entering and validating API keys
- Selecting default models for each provider
- Configuring task-specific models for different workloads
- Setting system permissions (file system, web access, bash execution)
- Configuring working directory and default parameters

All settings are saved to `~/.t1-super-ai/config.json`.

#### `/provider [name]` - Provider Management
Switch between different LLM providers on the fly:
- `openai` - Uses OpenAI's GPT models (requires API key)
- `anthropic` - Uses Anthropic's Claude models (requires API key)
- `ollama` - Uses locally running Ollama models (no API key needed)

Without arguments, displays the current provider. Changes affect new conversations immediately.

#### `/model [name|number]` - Model Selection
Manage which specific model to use:
- **No arguments**: Shows interactive menu with all available models
- **With name**: Directly switch to a model (e.g., `/model gpt-4-turbo`)
- **With number**: Select from the menu (e.g., `/model 2`)

The menu displays:
- Default provider model
- Task-specific models (if configured)
- Option to enter custom model name

Models are provider-specific. Switching models doesn't change the provider.

#### `/auto-model [on|off]` - Automatic Model Selection
Enable intelligent model routing based on task type:

When **enabled**:
- System analyzes your prompt using LLM classification
- Automatically selects the best task-specific model
- Routes coding tasks to code-optimized models, reasoning tasks to reasoning models, etc.

When **disabled**:
- All prompts use the default provider model

Without arguments, shows current status and configured task models.

#### `/classify <prompt>` - Prompt Classification Testing
Debug tool for automatic model selection:
- Sends your prompt to the classifier LLM
- Shows detailed analysis of detected task type
- Displays which model would be selected
- Useful for tuning task-specific model configurations

Example output:
```
Task Type: CODE_GENERATION
Confidence: High
Selected Model: GPT-4 (OpenAI)
Reasoning: Prompt contains code writing keywords...
```

### Agent Management

#### `/agent create <name>` - Agent Creation Wizard
Launches the interactive wizard to create a personalized agent. This command creates a fork of the current session with its own configuration. The wizard asks:

1. **Agent Role**: What role will this agent play? (e.g., Code Reviewer, Data Analyst)
2. **Agent Purpose**: What is the agent's primary purpose?
3. **Personality Traits**: What personality should the agent have?
4. **Communication Style**: How should the agent communicate?
5. **Special Abilities**: Special skills or knowledge areas?
6. **Model Preferences**: Which LLM model works best for this role?

Creates a new directory structure:
```
~/.t1-super-ai/agents/<name>/
├── config.json      # Agent configuration
├── CHARACTER.md     # Personality definition
└── USAGE.md         # Usage guide
```

#### `/agent list` - View All Agents
Displays comprehensive information about all agents:
- Status indicators (🟢 active, 🟡 idle, 🔴 stopped)
- Current agent marked with `*`
- Agent role and context
- Provider and model
- Creation timestamp
- Last active timestamp
- Conversation count

#### `/agent use <name>` - Switch Agents
Switch the active agent for conversations. Each agent has:
- Its own conversation history
- Its own personality and system prompt
- Its own model configuration
- Its own memory context

Switching is instant and doesn't lose any conversation history.

#### `/agent remove <name>` - Delete Agent
Permanently delete an agent after confirmation:
- Prompts for `y/n` confirmation
- Cannot remove the master agent
- If currently using the agent being removed, automatically switches to master
- Deletes the agent's configuration files from disk

### Tool & Extension Management

#### `/mcp list` - List MCP Servers
Shows all connected MCP (Model Context Protocol) servers. MCP servers provide additional tools and capabilities that agents can use.

#### `/mcp tools <server-name>` - List MCP Server Tools
View all tools available from a specific MCP server:
- Use `local` to see built-in core tools and plugin tools
- Use the server name to see external MCP server tools

Each tool shows its name and description.

#### `/plugin list` - View Loaded Plugins
Displays all loaded plugins with:
- Plugin name and version
- Plugin type (tool provider, skill provider, etc.)
- Description
- Number of tools the plugin provides

Plugins extend the platform's capabilities without modifying core code.

#### `/plugin tools` - List Plugin Tools
Shows all tools provided by loaded plugins. Each tool displays:
- Tool name
- Tool description
- Source plugin

#### `/plugin reload` - Hot-Reload Plugins
Reloads all plugins from the `~/.t1-super-ai/plugins/` directory:
- Picks up new plugins
- Reloads modified plugins
- Removes deleted plugins
- No application restart required

#### `/skill list` - View Available Skills
Lists all available skills. Skills are higher-level capabilities composed of multiple tools and logic patterns.

#### `/skill create` - Create New Skill
Launches the skill creation wizard (feature in development). For now, create skills manually in `~/.t1-super-ai/skills/`.

### Utility Commands

#### `/reload` - Reload Context
Reloads the CHARACTER.md and USAGE.md files from disk:
- Picks up changes to agent personality
- Updates system instructions
- Refreshes context without restart
- Applies to current conversation immediately

#### `/clear` - Clear Screen
Clears the terminal and redraws the application banner. Useful for decluttering the view.

#### `/help` - Show Help
Displays a quick reference of all available commands with brief descriptions.

---

### REST API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/agents` | GET | List all agents |
| `/api/v1/agents` | POST | Create a new agent |
| `/api/v1/agents/{name}` | GET | Get agent details |
| `/api/v1/agents/{name}` | PUT | Update agent configuration |
| `/api/v1/agents/{name}` | DELETE | Delete an agent |
| `/api/v1/agents/{name}/chat` | POST | Send message to agent |
| `/api/v1/agents/{name}/message` | POST | Send inter-agent message |
| `/api/v1/configuration` | GET | Get system configuration |
| `/api/v1/tools` | GET | List available tools |
| `/api/v1/plugins` | GET | List installed plugins |
| `/api/v1/mcp-servers` | GET | List MCP servers |

All API endpoints require the `X-API-Key` header for authentication.

### Configuration Files Quick Reference

| File | Purpose | Hot-Reload |
|------|---------|------------|
| `~/.t1-super-ai/config.json` | System-wide configuration | ✅ Yes |
| `~/.t1-super-ai/agents/<name>/config.json` | Agent-specific settings | ✅ Yes |
| `~/.t1-super-ai/agents/<name>/CHARACTER.md` | Agent personality | ✅ Yes |
| `~/.t1-super-ai/tools/<name>.json` | Tool definitions | ✅ Yes |
| `~/.t1-super-ai/plugins/<name>/plugin.json` | Plugin metadata | ✅ Yes |
| `src/main/resources/application.properties` | Spring Boot configuration | ❌ No |

---

## 🤔 FAQ

### General Questions

**Q: Is this production-ready?**
A: The platform is currently in active development. While core features are functional, it's recommended for experimental and development use cases. Production deployment should be carefully evaluated based on your specific requirements.

**Q: Do I need all three AI providers?**
A: No. You can use any combination of OpenAI, Anthropic, or Ollama. Each agent can be configured to use a specific provider based on your needs and available API keys.

**Q: Why Java 25?**
A: Java 25 provides modern language features including enhanced pattern matching, record patterns, and preview APIs that improve code clarity and developer productivity. The platform leverages these features for cleaner implementation of complex agent behaviors.

**Q: Where is my data stored?**
A: All configuration and agent data is stored in `~/.t1-super-ai/` in your home directory. This includes agent configurations, conversation history (if vector store is enabled), and custom tools. The application itself only contains code—no user data.

### Workspace & Configuration

**Q: What happens on first run?**
A: The system automatically creates the `~/.t1-super-ai/` workspace directory, initializes necessary subdirectories (agents, tools, plugins, mcp-servers, skills), and creates instructional markdown files. You may be guided through initial configuration if needed.

**Q: Can I back up my agents?**
A: Yes! Simply back up the `~/.t1-super-ai/` directory. You can also version control individual agent directories to track changes over time.

**Q: Can I edit agent configurations manually?**
A: Absolutely. All configurations are stored as JSON and Markdown files that you can edit directly. The system supports hot-reloading, so changes take effect without restart.

**Q: Where do I put my API keys?**
A: API keys go in `~/.t1-super-ai/config.json` under the `llmConfigs` section for each provider. Never commit this file to version control.

### Agent Creation & Personalization

**Q: How do I create a new agent?**
A: Use the `/agent create <name>` command to launch an interactive wizard that guides you through defining the agent's role, purpose, personality, communication style, special abilities, and model preference.

**Q: Can I change an agent's personality after creation?**
A: Yes. Edit the agent's `CHARACTER.md` file and `config.json` in `~/.t1-super-ai/agents/<name>/` to adjust personality traits, system prompts, or behavior parameters. Run `/reload` to apply changes immediately.

**Q: How is agent personality different from model selection?**
A: Model selection determines the underlying AI engine (GPT-4, Claude, etc.), while personality configuration shapes how that model behaves through system prompts, temperature settings, and capability restrictions. Think of the model as the brain and personality as the training and character.

**Q: Can multiple agents use the same model?**
A: Yes. Multiple agents can use the same underlying model (e.g., GPT-4) but have completely different personalities, roles, and behaviors based on their individual configurations.

**Q: How many agents can I create?**
A: There's no hard limit. Each agent is a lightweight configuration in the workspace directory. Practical limits depend on your system resources and API rate limits.

### Learning & Evolution

**Q: How do agents learn and evolve?**
A: Agents learn through multiple mechanisms: storing successful interaction patterns in vector memory, analyzing task outcomes, receiving user feedback, and identifying capability gaps. They evolve by creating new tools, adjusting behavior parameters, and sharing knowledge with other agents.

**Q: Can agents really write their own tools?**
A: Yes. Agents can identify missing capabilities during task execution and dynamically create, register, and use new tools at runtime. This enables autonomous capability expansion without manual intervention.

**Q: Do agents remember previous conversations?**
A: If vector store is enabled (`spring.ai.vectorstore.enabled=true` in `application.properties`), agents store conversation history in vector memory for semantic retrieval. Otherwise, each session is independent.

### Inter-Agent Communication

**Q: Do agents share memories with each other?**
A: Agents maintain isolated memory spaces by default, but can selectively share specific memories or knowledge with other agents through explicit sharing mechanisms. This allows for controlled knowledge transfer while maintaining agent independence.

**Q: How does inter-agent communication work?**
A: Agents communicate through a message-passing system that supports direct messaging, broadcasts, and structured collaboration patterns. Messages can include context, data, and task delegation information, enabling coordinated problem-solving.

**Q: Can agents work together on tasks?**
A: Yes. Agents can delegate subtasks to other agents based on specializations, collaborate on complex problems, and share knowledge. The orchestrator coordinates multi-agent workflows.

---

## 🐛 Known Issues

- Vector embeddings with local transformers can cause file locking issues on Windows (disabled by default)
- Redis vector store support is planned but not yet implemented
- Agent self-evolution is currently limited to tool creation; broader capability expansion is under development

---

## 🤝 Contributing

Contributions are welcome! Whether it's:

- 🐛 Bug fixes
- ✨ New features
- 📝 Documentation improvements
- 🎨 UI enhancements
- 🧪 Test coverage

Please read our contributing guidelines before submitting a PR.

---

## 📜 License

This project is licensed under the Creative Commons - Attribution-NonCommercial-ShareAlike 4.0 International - License - because we believe in sharing the AI revolution.

---

## 🙏 Acknowledgments

- The Spring team for Spring Boot 4 and Spring AI framework
- OpenAI, Anthropic, and the Ollama project for their AI models
- The open-source community for supporting libraries and tools

---

## 📞 Support

For issues and questions:

1. Review the comprehensive documentation in the repository
2. Search existing GitHub issues for similar problems
3. Open a new issue with detailed information about your environment and the problem
4. Include logs, configuration details, and steps to reproduce

---

<div align="center">

**Building the Future of Multi-Agent AI Systems**

*Enabling autonomous agents that learn, collaborate, and evolve*

</div>
