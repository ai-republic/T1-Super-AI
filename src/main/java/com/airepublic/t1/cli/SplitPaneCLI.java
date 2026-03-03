package com.airepublic.t1.cli;

import static org.fusesource.jansi.Ansi.ansi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fusesource.jansi.Ansi;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.springframework.stereotype.Component;

import com.airepublic.t1.agent.Agent;
import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.agent.AgentSessionController;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.mcp.MCPClient;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.IndividualAgentConfig;
import com.airepublic.t1.plugins.PluginManager;
import com.airepublic.t1.service.HatchingService;

import lombok.extern.slf4j.Slf4j;

/**
 * Split-pane CLI with fixed input at bottom
 * Output scrolls in top area, input stays at bottom
 */
@Slf4j
@Component
public class SplitPaneCLI implements CLI {

    private final AgentConfigurationManager configManager;
    private final AgentConfigService agentConfigService;
    private final AgentOrchestrator orchestrator;
    private final PluginManager pluginManager;
    private final MCPClient mcpClient;
    private final HatchingService hatchingService;
    private final AgentManager agentManager;
    private final CommandHandler commandHandler;
    private final AgentSessionController sessionController;

    private Terminal terminal;
    private int terminalHeight;
    private int terminalWidth;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicBoolean inWizardMode = new AtomicBoolean(false);
    private String currentAgentName = "master";
    private StringBuilder inputBuffer = new StringBuilder();
    private int cursorPosition = 0; // Cursor position within input buffer

    // Hatch mode state
    private boolean inHatchMode = false;
    private int hatchStep = 0;
    private final List<String> hatchConversation = new ArrayList<>();

    private final ExecutorService processingExecutor = Executors.newCachedThreadPool();
    private Thread spinnerThread;
    private Thread resizeListenerThread;
    private final String[] spinnerFrames = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private volatile int spinnerLine = -1;

    // Available commands for tab completion
    private final String[] availableCommands = {
            "/help", "/clear", "/exit", "/quit", "/config", "/provider", "/model",
            "/auto-model", "/classify", "/mcp", "/plugin", "/skill", "/agent", "/reload"
    };

    public SplitPaneCLI(
            final AgentConfigurationManager configManager,
            final AgentConfigService agentConfigService,
            final AgentOrchestrator orchestrator,
            final PluginManager pluginManager,
            final MCPClient mcpClient,
            final HatchingService hatchingService,
            final AgentManager agentManager,
            final CommandHandler commandHandler,
            final AgentSessionController sessionController) {
        this.configManager = configManager;
        this.agentConfigService = agentConfigService;
        this.orchestrator = orchestrator;
        this.pluginManager = pluginManager;
        this.mcpClient = mcpClient;
        this.hatchingService = hatchingService;
        this.agentManager = agentManager;
        this.commandHandler = commandHandler;
        this.sessionController = sessionController;
    }

    public void start(final boolean needsHatching) {
        try {
            // Register this CLI with the command handler
            commandHandler.setCLI(this);

            // Initialize terminal in raw mode
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .jansi(true)
                    .build();

            terminal.enterRawMode();
            updateTerminalDimensions();

            // Clear screen
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();

            // Set up scrolling region (top area, leave bottom 3 lines for input)
            setScrollingRegion(0, terminalHeight - 4);

            // Start terminal resize listener
            startResizeListener();

            // Initialize with colored header
            printOutput("\033[36m╔═══════════════════════════════════════════════════════════╗\033[0m");
            printOutput("\033[36m║\033[0m              \033[1;33m████████╗  ██╗  \033[0m                             \033[36m║\033[0m");
            printOutput("\033[36m║\033[0m              \033[1;33m   ██║    ███║   \033[1;36mSuper AI\033[0m  \033[33mv1.0.0\033[0m            \033[36m║\033[0m");
            printOutput("\033[36m║\033[0m              \033[1;33m   ██║     ██║\033[0m                               \033[36m║\033[0m");
            printOutput("\033[36m╚═══════════════════════════════════════════════════════════╝\033[0m");
            printOutput("");

            // Set the output callback for AgentSessionController (after terminal is ready)
            sessionController.setOutputCallback(this::printOutput);

            agentManager.initializeMasterAgent(orchestrator);
            printOutput("\033[32m✓ Master agent initialized\033[0m");

            // Load saved agent configurations from disk
            agentManager.loadSavedAgents(orchestrator);

            // Check if we need to run HATCH process first
            if (needsHatching && hatchingService.needsHatching()) {
                startHatchMode();
            } else {
                printOutput("\033[32m✓ Ready! You can type while the agent is processing.\033[0m");
                printOutput("");
            }

            // Draw initial input area
            redrawInputArea();

            // Start input loop
            processInputLoop();

            shutdown();

        } catch (final Exception e) {
            log.error("Error starting CLI", e);
            throw new RuntimeException("Failed to start CLI", e);
        }
    }

    /**
     * Set scrolling region
     */
    private void setScrollingRegion(final int top, final int bottom) throws IOException {
        // ANSI escape: CSI top ; bottom r
        terminal.writer().print(String.format("\033[%d;%dr", top + 1, bottom + 1));
        terminal.writer().flush();
    }

    /**
     * Update terminal dimensions
     */
    private void updateTerminalDimensions() {
        terminalHeight = terminal.getHeight();
        terminalWidth = terminal.getWidth();
    }

    /**
     * Start terminal resize listener thread
     */
    private void startResizeListener() {
        resizeListenerThread = new Thread(() -> {
            int lastHeight = terminalHeight;
            int lastWidth = terminalWidth;

            while (running.get()) {
                try {
                    // Use smaller sleep intervals to respond faster to shutdown
                    Thread.sleep(100); // Check every 100ms (faster shutdown response)

                    final int currentHeight = terminal.getHeight();
                    final int currentWidth = terminal.getWidth();

                    if (currentHeight != lastHeight || currentWidth != lastWidth) {
                        lastHeight = currentHeight;
                        lastWidth = currentWidth;
                        handleTerminalResize(currentHeight, currentWidth);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.debug("Resize listener interrupted, shutting down");
                    break;
                } catch (final Exception e) {
                    log.error("Error in resize listener", e);
                }
            }
            log.debug("Resize listener thread terminated");
        });
        resizeListenerThread.setDaemon(true);
        resizeListenerThread.setName("terminal-resize-listener");
        resizeListenerThread.start();
    }

    /**
     * Handle terminal resize event
     */
    private void handleTerminalResize(final int newHeight, final int newWidth) {
        synchronized (terminal) {
            terminalHeight = newHeight;
            terminalWidth = newWidth;

            try {
                // Update scrolling region to fit new size
                setScrollingRegion(0, terminalHeight - 4);

                // Redraw input area with new dimensions
                redrawInputArea();

                log.debug("Terminal resized to {}x{}", newWidth, newHeight);
            } catch (final Exception e) {
                log.error("Error handling terminal resize", e);
            }
        }
    }

    /**
     * Process input loop
     */
    private void processInputLoop() throws IOException {
        final var reader = terminal.reader();

        while (running.get()) {
            // Skip reading if in wizard mode
            if (inWizardMode.get()) {
                try {
                    Thread.sleep(50); // Shorter sleep for faster shutdown response
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // Exit on interrupt
                }
                continue;
            }

            // Use non-blocking read with timeout to check running flag more frequently
            final int c = reader.read(100); // 100ms timeout

            if (c == -1) {
                break;
            }

            if (c == 3) { // Ctrl+C
                if (confirmExit()) {
                    break;
                }
                continue;
            }

            if (c == 4) { // Ctrl+D
                break;
            }

            if (c == 13 || c == 10) { // Enter
                final String input = inputBuffer.toString().trim();
                if (!input.isEmpty()) {
                    // Move to output area and print with cyan color and person emoji
                    moveToOutputArea();
                    printOutput("👤 \033[36mYou:\033[0m " + input);
                    printOutput("");

                    // Process input
                    processInputAsync(input);

                    // Clear input buffer and reset cursor position
                    inputBuffer = new StringBuilder();
                    cursorPosition = 0;
                }
                redrawInputArea();
                continue;
            }

            if (c == 127 || c == 8) { // Backspace
                if (cursorPosition > 0) {
                    inputBuffer.deleteCharAt(cursorPosition - 1);
                    cursorPosition--;
                    redrawInputArea();
                }
                continue;
            }

            if (c == 9) { // Tab
                handleTabCompletion();
                continue;
            }

            if (c == 27) { // Escape sequence (arrow keys, etc.)
                final int next1 = reader.read(100); // Read with timeout
                if (next1 == -1) {
                    // Just ESC key pressed, ignore
                    continue;
                }

                if (next1 == 91) { // CSI sequence (ESC [)
                    // Try to peek at the next character without blocking too long
                    final int next2 = reader.read(50);
                    if (next2 == -1) {
                        continue;
                    }

                    log.debug("Escape sequence: ESC [ {} (decimal: {})", (char)next2, next2);

                    // Standard arrow keys: ESC[A, ESC[B, ESC[C, ESC[D
                    // These are single character sequences after ESC[
                    if (next2 == 65) { // Up arrow (ESC[A)
                        log.debug("Up arrow detected");
                        // Could implement history navigation here
                        continue;
                    } else if (next2 == 66) { // Down arrow (ESC[B)
                        log.debug("Down arrow detected");
                        // Could implement history navigation here
                        continue;
                    } else if (next2 == 67) { // Right arrow (ESC[C)
                        log.debug("Right arrow detected");
                        handleRightArrow();
                        continue;
                    } else if (next2 == 68) { // Left arrow (ESC[D)
                        log.debug("Left arrow detected");
                        handleLeftArrow();
                        continue;
                    } else if (next2 == 72) { // Home key (ESC[H)
                        handleHome();
                        continue;
                    } else if (next2 == 70) { // End key (ESC[F)
                        handleEnd();
                        continue;
                    } else if (next2 == 49) { // Extended sequences starting with 1
                        final int next3 = reader.read(100);
                        if (next3 == -1) {
                            continue;
                        }

                        log.debug("Extended sequence: ESC [ 1 {} ({})", next3, (char)next3);

                        if (next3 == 126) { // Home (ESC[1~)
                            handleHome();
                            continue;
                        } else if (next3 == 59) { // Modifier sequences (ESC[1;modifier)
                            final int modifier = reader.read(100);
                            final int direction = reader.read(100);
                            if (modifier == -1 || direction == -1) {
                                continue;
                            }

                            log.debug("Modifier sequence: ESC [ 1 ; {} {}", modifier, (char)direction);

                            if (modifier == 53) { // Ctrl modifier (1;5)
                                if (direction == 67) { // Ctrl+Right (ESC[1;5C)
                                    handleCtrlRightArrow();
                                    continue;
                                } else if (direction == 68) { // Ctrl+Left (ESC[1;5D)
                                    handleCtrlLeftArrow();
                                    continue;
                                }
                            } else if (modifier == 50) { // Shift modifier (1;2)
                                // Could handle Shift+Arrow here
                                continue;
                            }
                        }
                    } else if (next2 == 52) { // End key variation (ESC[4~)
                        final int next3 = reader.read(100);
                        if (next3 == 126) {
                            handleEnd();
                            continue;
                        }
                    } else if (next2 == 56) { // End key variation (ESC[8~)
                        final int next3 = reader.read(100);
                        if (next3 == 126) {
                            handleEnd();
                            continue;
                        }
                    } else if (next2 == 51) { // Delete key (ESC[3~)
                        final int next3 = reader.read(100);
                        if (next3 == 126) {
                            handleDelete();
                            continue;
                        }
                    }
                } else if (next1 == 79) { // SS3 sequence (ESC O) - alternative encoding
                    final int next2 = reader.read(50);
                    if (next2 == -1) {
                        continue;
                    }

                    log.debug("SS3 sequence: ESC O {} (decimal: {})", (char)next2, next2);

                    // Arrow keys in SS3 mode (some terminals use this)
                    if (next2 == 65) { // Up arrow (ESC OA)
                        log.debug("Up arrow (SS3) detected");
                        // Could implement history navigation here
                        continue;
                    } else if (next2 == 66) { // Down arrow (ESC OB)
                        log.debug("Down arrow (SS3) detected");
                        // Could implement history navigation here
                        continue;
                    } else if (next2 == 67) { // Right arrow (ESC OC)
                        log.debug("Right arrow (SS3) detected");
                        handleRightArrow();
                        continue;
                    } else if (next2 == 68) { // Left arrow (ESC OD)
                        log.debug("Left arrow (SS3) detected");
                        handleLeftArrow();
                        continue;
                    } else if (next2 == 72) { // Home (ESC OH)
                        log.debug("Home (SS3) detected");
                        handleHome();
                        continue;
                    } else if (next2 == 70) { // End (ESC OF)
                        log.debug("End (SS3) detected");
                        handleEnd();
                        continue;
                    }
                }
                // Unknown escape sequence, ignore
                log.debug("Unknown escape sequence starting with ESC {}", next1);
                continue;
            }

            // Regular character
            if (c >= 32 && c < 127) {
                inputBuffer.insert(cursorPosition, (char) c);
                cursorPosition++;
                redrawInputArea();
            }
        }
    }

    /**
     * Redraw input area at bottom
     */
    private void redrawInputArea() {
        try {
            final var config = configManager.getConfiguration();
            final String provider = config.getDefaultProvider().toString().toLowerCase();

            // Get model name
            String model = "unknown";
            final var llmConfig = config.getLlmConfigs().get(config.getDefaultProvider());
            if (llmConfig != null && llmConfig.getModel() != null) {
                model = llmConfig.getModel();
                // Shorten model name if too long (e.g., "gpt-4-turbo" instead of full name)
                if (model.length() > 20) {
                    model = model.substring(0, 17) + "...";
                }
            }

            final String prompt = String.format("[%s/%s] %s", provider, model, currentAgentName);

            // Save cursor position
            terminal.writer().print("\0337");

            // Move to input area (bottom 3 lines)
            terminal.writer().print(String.format("\033[%d;1H", terminalHeight - 2));

            // Clear input area
            terminal.writer().print("\033[K");
            terminal.writer().print("─".repeat(Math.min(terminalWidth, 200))); // Limit to reasonable width
            terminal.writer().print("\033[K\n");

            // Print prompt and input
            terminal.writer().print(prompt + " > " + inputBuffer.toString());
            terminal.writer().print("\033[K\n");

            // Bottom separator
            terminal.writer().print("─".repeat(Math.min(terminalWidth, 200))); // Limit to reasonable width
            terminal.writer().print("\033[K");

            // Position cursor at current position in input
            final int cursorCol = prompt.length() + 3 + cursorPosition;
            terminal.writer().print(String.format("\033[%d;%dH", terminalHeight - 1, cursorCol + 1));

            terminal.writer().flush();

        } catch (final Exception e) {
            log.error("Error redrawing input area", e);
        }
    }

    /**
     * Move cursor to output area
     */
    private void moveToOutputArea() {
        // Move to output area (above the fixed input)
        terminal.writer().print(String.format("\033[%d;1H", terminalHeight - 3));
        terminal.writer().flush();
    }

    /**
     * Print output in scrolling region
     */
    private void printOutput(final String message) {
        synchronized (terminal) {
            // Save cursor position
            terminal.writer().print("\0337");

            // Move to scrolling region
            moveToOutputArea();

            // Print message
            terminal.writer().println(message);

            // Restore cursor
            terminal.writer().print("\0338");

            terminal.writer().flush();

            // Redraw input area to ensure it stays fixed
            redrawInputArea();
        }
    }

    /**
     * Handle left arrow key - move cursor left
     */
    private void handleLeftArrow() {
        if (cursorPosition > 0) {
            cursorPosition--;
            redrawInputArea();
        }
    }

    /**
     * Handle right arrow key - move cursor right
     */
    private void handleRightArrow() {
        if (cursorPosition < inputBuffer.length()) {
            cursorPosition++;
            redrawInputArea();
        }
    }

    /**
     * Handle Ctrl+Left arrow - move cursor to previous word
     */
    private void handleCtrlLeftArrow() {
        if (cursorPosition == 0) {
            return;
        }

        // Skip whitespace
        while (cursorPosition > 0 && Character.isWhitespace(inputBuffer.charAt(cursorPosition - 1))) {
            cursorPosition--;
        }

        // Move to start of word
        while (cursorPosition > 0 && !Character.isWhitespace(inputBuffer.charAt(cursorPosition - 1))) {
            cursorPosition--;
        }

        redrawInputArea();
    }

    /**
     * Handle Ctrl+Right arrow - move cursor to next word
     */
    private void handleCtrlRightArrow() {
        if (cursorPosition >= inputBuffer.length()) {
            return;
        }

        // Skip current word
        while (cursorPosition < inputBuffer.length() && !Character.isWhitespace(inputBuffer.charAt(cursorPosition))) {
            cursorPosition++;
        }

        // Skip whitespace
        while (cursorPosition < inputBuffer.length() && Character.isWhitespace(inputBuffer.charAt(cursorPosition))) {
            cursorPosition++;
        }

        redrawInputArea();
    }

    /**
     * Handle Home key - move cursor to beginning of line
     */
    private void handleHome() {
        cursorPosition = 0;
        redrawInputArea();
    }

    /**
     * Handle End key - move cursor to end of line
     */
    private void handleEnd() {
        cursorPosition = inputBuffer.length();
        redrawInputArea();
    }

    /**
     * Handle Delete key - delete character at cursor position
     */
    private void handleDelete() {
        if (cursorPosition < inputBuffer.length()) {
            inputBuffer.deleteCharAt(cursorPosition);
            redrawInputArea();
        }
    }

    /**
     * Process input asynchronously
     */
    private void processInputAsync(final String input) {
        processingExecutor.submit(() -> {
            try {
                processInput(input);
            } catch (final Exception e) {
                printOutput("✗ Error: " + e.getMessage());
                log.error("Error processing input", e);
            }
        });
    }

    /**
     * Process input
     */
    private void processInput(final String input) {
        // Check if we're in HATCH mode
        if (inHatchMode) {
            processHatchInput(input);
            return;
        }

        // Check for slash commands
        if (input.startsWith("/")) {
            handleSlashCommand(input);
            return;
        }

        // Process with LLM
        processing.set(true);

        // Print "🤖 Assistant: " with spinner in orange
        printOutput("");
        printOutputNoNewline("🤖 \033[38;5;208mAssistant:\033[0m ⠋");
        startSpinner();

        try {
            final String response = orchestrator.processMessage(input);
            stopSpinner();

            // Get model info if available
            final String modelInfo = orchestrator.getCurrentModelInfo();
            final String assistantLabel = modelInfo != null
                    ? "🤖 \033[38;5;208mAssistant\033[0m " + modelInfo + ":"
                            : "🤖 \033[38;5;208mAssistant:\033[0m";

            // Clear the "Assistant: ⠋" line and reprint with actual response
            clearLastLine();
            printOutput(assistantLabel + " " + response);
            printOutput("");
        } catch (final Exception e) {
            stopSpinner();
            clearLastLine();
            printOutput("✗ Error: " + e.getMessage());
        } finally {
            processing.set(false);
        }
    }

    /**
     * Start spinner animation
     */
    private void startSpinner() {
        // We need to track the current cursor line in the scrolling region
        // The spinner is at the end of "🤖 Assistant: ⠋"
        // Position is: column 15 (after "🤖 Assistant: ")
        spinnerLine = -1; // Will be set dynamically
        spinnerThread = new Thread(() -> {
            int frameIndex = 0;
            while (processing.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    updateSpinner(spinnerFrames[frameIndex]);
                    frameIndex = (frameIndex + 1) % spinnerFrames.length;
                    Thread.sleep(100);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    /**
     * Update spinner character
     */
    private void updateSpinner(final String frame) {
        synchronized (terminal) {
            // Save cursor
            terminal.writer().print("\0337");

            // The "🤖 Assistant: ⠋" line is 2 lines up from current cursor
            // Format: "🤖 Assistant: ⠋"
            // Position calculation: emoji (🤖) + space + "Assistant: " + spinner position
            // Robot emoji typically renders as 2 characters wide
            // Column position: 2 (emoji) + 1 (space) + 11 ("Assistant: ") = 14

            terminal.writer().print("\033[F\033[F"); // Move up 2 lines
            terminal.writer().print("\033[15G"); // Move to column 15 (after "🤖 Assistant: ")
            terminal.writer().print(frame);

            // Restore cursor
            terminal.writer().print("\0338");
            terminal.writer().flush();
        }
    }

    /**
     * Stop spinner animation
     */
    private void stopSpinner() {
        if (spinnerThread != null) {
            spinnerThread.interrupt();
            try {
                spinnerThread.join(500);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Clear last line in output
     */
    private void clearLastLine() {
        synchronized (terminal) {
            // Save cursor
            terminal.writer().print("\0337");

            // Move up one line and clear it
            terminal.writer().print("\033[F\033[K");

            // Restore cursor
            terminal.writer().print("\0338");
            terminal.writer().flush();
        }
    }

    /**
     * Print output without newline
     */
    private void printOutputNoNewline(final String message) {
        synchronized (terminal) {
            // Save cursor position
            terminal.writer().print("\0337");

            // Move to scrolling region
            moveToOutputArea();

            // Print message without newline
            terminal.writer().print(message);

            // Restore cursor
            terminal.writer().print("\0338");

            terminal.writer().flush();

            // Redraw input area
            redrawInputArea();
        }
    }

    /**
     * Handle slash commands
     */
    private void handleSlashCommand(final String input) {
        final String command = input.toLowerCase();

        // Handle /clear specially since it needs terminal operations
        if (command.equals("/clear")) {
            try {
                terminal.puts(InfoCmp.Capability.clear_screen);
                setScrollingRegion(0, terminalHeight - 4);
                redrawInputArea();
            } catch (final IOException e) {
                log.error("Error clearing screen", e);
            }
            return;
        }

        // Handle /exit specially since it needs to set running flag
        if (command.equals("/exit") || command.equals("/quit")) {
            printOutput("Exiting...");
            running.set(false);
            return;
        }

        // Redirect System.out to capture CommandHandler output
        final java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream oldOut = System.out;

        try {
            System.setOut(new java.io.PrintStream(outputStream));
            commandHandler.handleCommand(input);
            System.out.flush();

            // Print captured output line by line
            final String output = outputStream.toString();
            if (!output.isEmpty()) {
                for (final String line : output.split("\n")) {
                    printOutput(line);
                }
            }
        } catch (final Exception e) {
            printOutput("✗ Error executing command: " + e.getMessage());
            log.error("Error executing command", e);
        } finally {
            System.setOut(oldOut);
        }
    }

    /**
     * Handle tab completion
     */
    private void handleTabCompletion() {
        final String current = inputBuffer.toString();

        // Only complete if starts with /
        if (!current.startsWith("/")) {
            return;
        }

        // Find matching commands
        final List<String> matches = new ArrayList<>();
        for (final String cmd : availableCommands) {
            if (cmd.startsWith(current)) {
                matches.add(cmd);
            }
        }

        if (matches.isEmpty()) {
            // No matches, beep
            terminal.writer().print("\007");
            terminal.writer().flush();
        } else if (matches.size() == 1) {
            // Exactly one match, complete it
            inputBuffer = new StringBuilder(matches.get(0));
            redrawInputArea();
        } else {
            // Multiple matches, show them
            printOutput("");
            printOutput("Possible completions:");
            for (final String match : matches) {
                printOutput("  " + match);
            }
            printOutput("");
            redrawInputArea();
        }
    }

    /**
     * Confirm exit
     */
    private boolean confirmExit() {
        printOutput("Press Ctrl+C again to exit, or any other key to continue");
        try {
            final int c = terminal.reader().read(1000); // 1 second timeout
            return c == 3; // Ctrl+C again
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Shutdown
     */
    private void shutdown() {
        final long startTime = System.currentTimeMillis();
        printOutput("Shutting down...");
        running.set(false);

        // Interrupt resize listener thread immediately
        log.info("Shutting down resize listener...");
        if (resizeListenerThread != null && resizeListenerThread.isAlive()) {
            resizeListenerThread.interrupt();
            try {
                resizeListenerThread.join(200); // Wait max 200ms
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Resize listener shutdown took {}ms", System.currentTimeMillis() - startTime);

        // Shutdown processing executor with shorter timeout
        final long execStart = System.currentTimeMillis();
        log.info("Shutting down processing executor...");
        processingExecutor.shutdownNow(); // Use shutdownNow immediately for faster exit
        try {
            processingExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Processing executor shutdown took {}ms", System.currentTimeMillis() - execStart);

        // Shutdown other services with timing
        final long agentStart = System.currentTimeMillis();
        log.info("Shutting down agent manager...");
        try {
            agentManager.shutdownAll();
        } catch (final Exception e) {
            log.error("Error shutting down agents", e);
        }
        log.info("Agent manager shutdown took {}ms", System.currentTimeMillis() - agentStart);

        final long mcpStart = System.currentTimeMillis();
        log.info("Disconnecting MCP clients...");
        try {
            mcpClient.disconnectAll();
        } catch (final Exception e) {
            log.error("Error disconnecting MCP", e);
        }
        log.info("MCP disconnect took {}ms", System.currentTimeMillis() - mcpStart);

        final long pluginStart = System.currentTimeMillis();
        log.info("Shutting down plugins...");
        try {
            pluginManager.shutdown();
        } catch (final Exception e) {
            log.error("Error shutting down plugins", e);
        }
        log.info("Plugin shutdown took {}ms", System.currentTimeMillis() - pluginStart);

        // Reset terminal
        try {
            // Reset scrolling region
            setScrollingRegion(0, terminalHeight - 1);
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.close();
        } catch (final IOException e) {
            log.error("Error closing terminal", e);
        }

        final long totalTime = System.currentTimeMillis() - startTime;
        log.info("Total shutdown time: {}ms", totalTime);
        System.out.println("Shutdown complete in " + totalTime + "ms");

        // Force exit to avoid Spring Boot shutdown delays
        System.exit(0);
    }

    @Override
    public void stop() {
        running.set(false);
    }

    /**
     * Update current agent name and redraw prompt
     */
    @Override
    public void updatePromptAgent(final String agentName) {
        currentAgentName = agentName;
        redrawInputArea();
    }

    /**
     * Start HATCH mode - LLM-guided setup conversation
     */
    private void startHatchMode() {
        inHatchMode = true;
        hatchStep = 0;

        printOutput("");
        printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
        printOutput("\033[36m         🥚 WELCOME TO AI SUPER AGENT - HATCH PROCESS         \033[0m");
        printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
        printOutput("");

        final String initiationMessage = hatchingService.getHatchInitiationMessage();
        printOutput(initiationMessage);
        printOutput("");
        printOutput("Let's start with some basics about you!");
        printOutput("");
        printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
        printOutput("\033[36m                     STEP 1: ABOUT YOU                         \033[0m");
        printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
        printOutput("");
        printOutput("\033[33mWhat should I call you?\033[0m");
        printOutput("(e.g., 'Alex', 'Dr. Smith', 'Team Lead', or just your name)");
        printOutput("");
    }

    /**
     * Process input during HATCH mode
     */
    private void processHatchInput(final String input) {
        hatchConversation.add("User: " + input);

        switch (hatchStep) {
            case 0: // Name
                printOutput("");
                printOutput("Great! Nice to meet you, " + input + "!");
                printOutput("");
                printOutput("\033[33mWhat are your preferred pronouns?\033[0m (optional)");
                printOutput("(e.g., 'they/them', 'she/her', 'he/him', or leave blank to skip)");
                printOutput("");
                hatchConversation.add("Assistant: Collected name, asking for pronouns");
                hatchStep++;
                break;

            case 1: // Pronouns
                printOutput("");
                printOutput("Thanks! Now, \033[33mwhat do you mainly work on?\033[0m");
                printOutput("(e.g., 'Full-stack web development', 'Data science', 'DevOps', 'Research')");
                printOutput("");
                hatchConversation.add("Assistant: Collected pronouns, asking for work focus");
                hatchStep++;
                break;

            case 2: // Work focus
                printOutput("");
                printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
                printOutput("\033[36m                 STEP 2: NAME YOUR AGENT                       \033[0m");
                printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
                printOutput("");
                printOutput("\033[33mWhat would you like to call me\033[0m (your AI agent)?");
                printOutput("(e.g., 'Atlas', 'CodeWizard', 'DevBuddy', 'Professor', 'Sage')");
                printOutput("");
                hatchConversation.add("Assistant: Collected work focus, asking for agent name");
                hatchStep++;
                break;

            case 3: // Agent name
                printOutput("");
                printOutput("Nice! I'll be '" + input + "' from now on!");
                printOutput("");
                printOutput("\033[33mWhat's my primary purpose?\033[0m What will you mainly use me for?");
                printOutput("(e.g., 'Help with daily coding', 'Research assistant', 'DevOps automation')");
                printOutput("");
                hatchConversation.add("Assistant: Collected agent name, asking for purpose");
                hatchStep++;
                break;

            case 4: // Purpose
                printOutput("");
                printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
                printOutput("\033[36m              STEP 3: COMMUNICATION STYLE                      \033[0m");
                printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
                printOutput("");
                printOutput("\033[33mHow should I communicate with you?\033[0m");
                printOutput("Choose: Professional / Friendly / Concise / Educational / Enthusiastic");
                printOutput("");
                hatchConversation.add("Assistant: Collected purpose, asking for communication style");
                hatchStep++;
                break;

            case 5: // Communication style
                printOutput("");
                printOutput("Perfect! And \033[33mshould I use emojis?\033[0m");
                printOutput("Choose: Freely / Sparingly / No emojis");
                printOutput("");
                hatchConversation.add("Assistant: Collected communication style, asking for emoji preference");
                hatchStep++;
                break;

            case 6: // Emoji preference
                printOutput("");
                printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
                printOutput("\033[36m           ✨ SETUP COMPLETE - CREATING YOUR PROFILE           \033[0m");
                printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
                printOutput("");
                hatchConversation.add("Assistant: Collected emoji preference, completing setup");

                completeHatchProcess();
                break;

            default:
                printOutput("\033[31m✗ Unexpected HATCH state - completing setup\033[0m");
                completeHatchProcess();
                break;
        }
    }

    /**
     * Complete the HATCH process and create CHARACTER.md
     */
    private void completeHatchProcess() {
        printOutput("Processing your responses...");
        printOutput("");

        // Build conversation summary for extraction
        final StringBuilder conversationSummary = new StringBuilder();
        for (final String message : hatchConversation) {
            conversationSummary.append(message).append("\n");
        }

        // Extract hatch data from conversation
        Map<String, String> hatchData = hatchingService.extractHatchData(conversationSummary.toString());

        // If extraction failed, parse manually
        if (hatchData.isEmpty()) {
            hatchData = parseHatchConversationManually();
        }

        // Complete the HATCH process
        hatchingService.completeHatchProcess(hatchData);

        printOutput("\033[32m✅ Your CHARACTER profile has been created!\033[0m");
        printOutput("");
        printOutput("You can review and edit it at: ~/.t1-super-ai/CHARACTER.md");
        printOutput("");
        printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
        printOutput("\033[36m              🎉 READY TO START YOUR SESSION!                  \033[0m");
        printOutput("\033[36m═══════════════════════════════════════════════════════════════\033[0m");
        printOutput("");
        printOutput("Your agent is now configured and ready to help.");
        printOutput("Type /help to see available commands, or just start chatting!");
        printOutput("");

        // Exit HATCH mode
        inHatchMode = false;
        hatchConversation.clear();
    }

    /**
     * Parse HATCH conversation manually if LLM extraction fails
     */
    private Map<String, String> parseHatchConversationManually() {
        final Map<String, String> data = new java.util.HashMap<>();

        try {
            final List<String> userResponses = new ArrayList<>();
            for (final String msg : hatchConversation) {
                if (msg.startsWith("User: ")) {
                    userResponses.add(msg.substring(6));
                }
            }

            if (userResponses.size() >= 6) {
                data.put("user_name", userResponses.get(0));
                data.put("user_pronouns", userResponses.get(1));
                data.put("user_work_focus", userResponses.get(2));
                data.put("agent_name", userResponses.get(3));
                data.put("agent_purpose", userResponses.get(4));
                data.put("communication_style", userResponses.get(5));
                if (userResponses.size() >= 7) {
                    data.put("emoji_preference", userResponses.get(6));
                }
            }

            // Build personality traits
            final String personality = String.format(
                    "%s assistant with a %s communication style",
                    data.getOrDefault("agent_purpose", "helpful"),
                    data.getOrDefault("communication_style", "professional")
                    );
            data.put("personality_traits", personality);

        } catch (final Exception e) {
            log.error("Error parsing HATCH conversation manually", e);
        }

        return data;
    }

    @Override
    public void startAgentCreationWizard(final String agentName) {
        // Set wizard mode flag to prevent main input loop from reading
        inWizardMode.set(true);

        try {
            // Display wizard header
            printOutput("");
            printOutput("╔════════════════════════════════════════════════════╗");
            printOutput("║  🤖 Agent Creation Wizard                          ║");
            printOutput("╚════════════════════════════════════════════════════╝");
            printOutput("");

            // Step 1: Get agent role
            printOutput("📝 Step 1: Define the agent's role");
            printOutput("Example: 'Senior Java Developer', 'DevOps Engineer', 'Code Reviewer'");
            final String role = promptForInput("Enter agent role: ", "General Purpose Agent");

            // Step 2: Get agent context
            printOutput("");
            printOutput("📝 Step 2: Provide context for the agent");
            printOutput("Describe what this agent should focus on or specialize in.");
            printOutput("Example: 'Expert in Spring Boot and microservices architecture'");
            final String context = promptForInput("Enter agent context: ", "A helpful AI assistant");

            // Step 3: Select provider
            printOutput("");
            printOutput("🔧 Step 3: Select LLM Provider");
            final AgentConfiguration config = configManager.getConfiguration();
            final List<LLMProvider> availableProviders = new ArrayList<>();

            int providerIndex = 1;
            for (final LLMProvider provider : LLMProvider.values()) {
                if (config.getLlmConfigs().containsKey(provider)) {
                    printOutput("  " + providerIndex + ". " + getProviderIcon(provider) + " " + provider);
                    availableProviders.add(provider);
                    providerIndex++;
                }
            }

            // Add option to use default
            printOutput("  " + providerIndex + ". ⭐ Use default provider (" + config.getDefaultProvider() + ")");

            final int maxChoice = providerIndex;
            final int providerChoice = promptForNumber("Select provider (1-" + maxChoice + "): ", 1, maxChoice, maxChoice);

            LLMProvider selectedProvider;
            if (providerChoice == maxChoice) {
                selectedProvider = config.getDefaultProvider();
            } else {
                selectedProvider = availableProviders.get(providerChoice - 1);
            }

            // Step 4: Select model
            printOutput("");
            printOutput("🎯 Step 4: Select Model");
            final var llmConfig = config.getLlmConfigs().get(selectedProvider);
            final String defaultModel = llmConfig != null ? llmConfig.getModel() : "";

            printOutput("Default model for " + selectedProvider + ": " + defaultModel);
            final String selectedModel = promptForInput("Enter model name (press Enter for default): ", defaultModel);

            // Step 5: Personality (for CHARACTER.md)
            printOutput("");
            printOutput("🎭 Step 5: Define Personality");
            printOutput("Describe the agent's personality traits.");
            printOutput("Examples: Thorough and detail-oriented, Encouraging and supportive");
            final String personality = promptForInput("Enter personality: ", "Professional and helpful");

            // Step 6: Communication Style (for CHARACTER.md)
            printOutput("");
            printOutput("💬 Step 6: Communication Style");
            printOutput("How should the agent communicate?");
            printOutput("Examples: Technical and precise, Friendly and casual, Formal and detailed");
            final String commStyle = promptForInput("Enter communication style: ", "Clear and concise");

            // Step 7: Specialties (optional, for CHARACTER.md)
            printOutput("");
            printOutput("⭐ Step 7: Specialties (Optional)");
            printOutput("What are this agent's areas of expertise?");
            printOutput("Examples: Java Spring Boot, Python data science, AWS infrastructure");
            final String specialties = promptForInput("Enter specialties (or press Enter to skip): ", "General software development");

            // Step 8: Constraints (optional, for CHARACTER.md)
            printOutput("");
            printOutput("🚧 Step 8: Constraints (Optional)");
            printOutput("Any constraints or boundaries for this agent?");
            printOutput("Examples: Only provide code reviews, Focus on backend only");
            final String constraints = promptForInput("Enter constraints (or press Enter to skip): ", "None");

            // Create agent configuration
            final IndividualAgentConfig agentConfig = new IndividualAgentConfig(
                    agentName, role, context, selectedProvider, selectedModel
                    );

            // Create agent folder
            try {
                agentConfigService.createAgentFolder(agentName);
            } catch (final Exception e) {
                printOutput("⚠️  Warning: Could not create agent folder: " + e.getMessage());
            }

            // Save configuration
            try {
                agentConfigService.saveAgentConfig(agentConfig);
                printOutput("");
                printOutput("✅ Agent configuration saved");
            } catch (final Exception e) {
                printOutput("⚠️  Warning: Could not save agent configuration: " + e.getMessage());
            }

            // Create CHARACTER.md
            try {
                final Map<String, String> hatchData = new HashMap<>();
                hatchData.put("agent_role", role);
                hatchData.put("agent_purpose", context);
                hatchData.put("agent_personality", personality);
                hatchData.put("communication_style", commStyle);
                hatchData.put("specialties", specialties);
                hatchData.put("constraints", constraints);
                agentConfigService.createCharacterMd(agentName, hatchData);
                printOutput("✅ CHARACTER.md created");
            } catch (final Exception e) {
                printOutput("⚠️  Warning: Could not create CHARACTER.md: " + e.getMessage());
            }

            // Create USAGE.md
            try {
                agentConfigService.createUsageMd(agentName, agentConfig);
                printOutput("✅ USAGE.md created");
            } catch (final Exception e) {
                printOutput("⚠️  Warning: Could not create USAGE.md: " + e.getMessage());
            }

            // Create the agent
            printOutput("");
            printOutput("🚀 Creating agent '" + agentName + "'...");
            final Agent newAgent = agentManager.createAgent(agentName, orchestrator, agentConfig);

            printOutput("");
            printOutput("✅ Agent '" + agentName + "' created and started!");
            printOutput("📋 Role: " + role);
            printOutput("📝 Context: " + context);
            printOutput("🔧 Provider: " + selectedProvider);
            printOutput("🎯 Model: " + selectedModel);
            printOutput("");
            printOutput("Session context forked from: " + agentManager.getCurrentAgentName());
            printOutput("The new agent is running in its own thread.");
            printOutput("");
            printOutput("💡 Use '/agent use " + agentName + "' to switch to this agent");
            printOutput("");

        } catch (final Exception e) {
            printOutput("❌ Error creating agent: " + e.getMessage());
            log.error("Error in agent creation wizard", e);
        } finally {
            // Reset wizard mode flag
            inWizardMode.set(false);
            // Clear any remaining input buffer
            inputBuffer = new StringBuilder();
            // Redraw the input area
            redrawInputArea();
        }
    }

    private String promptForInput(final String prompt, final String defaultValue) {
        final StringBuilder input = new StringBuilder();

        try {
            synchronized (terminal) {
                // Move to output area and print prompt in yellow
                moveToOutputArea();
                final String yellowPrompt = ansi().fg(Ansi.Color.YELLOW).a(prompt).reset().toString();
                terminal.writer().print(yellowPrompt);
                terminal.writer().flush();
            }

            final var reader = terminal.reader();
            while (true) {
                final int c = reader.read(); // Blocking read - no timeout to avoid double keystroke
                if (c == -1) {
                    continue;
                }

                if (c == '\r' || c == '\n') {
                    synchronized (terminal) {
                        terminal.writer().println();
                        terminal.writer().flush();
                    }
                    final String result = input.toString().trim();
                    return result.isEmpty() ? defaultValue : result;
                } else if (c == 127 || c == '\b' || c == 8) { // Backspace (127, \b, or 8)
                    if (input.length() > 0) {
                        input.deleteCharAt(input.length() - 1);
                        synchronized (terminal) {
                            terminal.writer().print("\b \b");
                            terminal.writer().flush();
                        }
                    }
                } else if (c >= 32 && c < 127) {
                    input.append((char) c);
                    synchronized (terminal) {
                        terminal.writer().print((char) c);
                        terminal.writer().flush();
                    }
                }
            }
        } catch (final IOException e) {
            log.error("Error reading input", e);
            return defaultValue;
        }
    }

    private int promptForNumber(final String prompt, final int min, final int max, final int defaultValue) {
        while (true) {
            final String input = promptForInput(prompt, String.valueOf(defaultValue));
            try {
                final int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
                printOutput("❌ Please enter a number between " + min + " and " + max);
            } catch (final NumberFormatException e) {
                printOutput("❌ Invalid input. Please enter a number between " + min + " and " + max);
            }
        }
    }

    private String getProviderIcon(final LLMProvider provider) {
        return switch (provider) {
            case OPENAI -> "🟢";
            case ANTHROPIC -> "🟣";
            case OLLAMA -> "🦙";
        };
    }
}
