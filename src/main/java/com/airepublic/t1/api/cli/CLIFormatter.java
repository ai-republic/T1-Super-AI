package com.airepublic.t1.cli;

import org.fusesource.jansi.Ansi;
import org.springframework.stereotype.Component;

import static org.fusesource.jansi.Ansi.ansi;

@Component
public class CLIFormatter {

    public void printBanner() {
        String banner = """

                ==========================================================

                          ████████╗  ██╗        Super AI
                             ██║   ███║
                             ██║    ██║         v1.0.0
                             ╚═╝    ╚═╝

                          Spring Boot 4.0.2 | Spring AI 2.0.0-M2 | Java 25

                ==========================================================
                """;

        System.out.println(ansi().fg(Ansi.Color.CYAN).a(banner).reset());
    }

    public String formatPrompt(String provider) {
        return formatPrompt(provider, "master");
    }

    public String formatPrompt(String provider, String agentName) {
        Ansi prompt = ansi()
                .fg(Ansi.Color.GREEN)
                .a(agentName)
                .reset()
                .a(" [")
                .fg(Ansi.Color.YELLOW)
                .a(provider)
                .reset()
                .a("] > ");

        return prompt.toString();
    }

    public void printInfo(String message) {
        System.out.println(ansi()
                .fg(Ansi.Color.BLUE)
                .a("ℹ ")
                .reset()
                .a(message));
    }

    public void printSuccess(String message) {
        System.out.println(ansi()
                .fg(Ansi.Color.GREEN)
                .a("✓ ")
                .reset()
                .a(message));
    }

    public void printError(String message) {
        System.out.println(ansi()
                .fg(Ansi.Color.RED)
                .a("✗ ")
                .reset()
                .a(message));
    }

    public void printWarning(String message) {
        System.out.println(ansi()
                .fg(Ansi.Color.YELLOW)
                .a("⚠ ")
                .reset()
                .a(message));
    }

    public void printThinking() {
        System.out.print(ansi()
                .fg(Ansi.Color.MAGENTA)
                .a("⚡ Thinking...")
                .reset()
                .a("\r"));
    }

    public void printAgentResponse(String response) {
        System.out.print("\r\033[K"); // Clear the "Thinking..." line
        System.out.println(ansi()
                .fg(Ansi.Color.CYAN)
                .a("Assistant: ")
                .reset()
                .a(response)
                .a("\n"));
    }

    public void printToolCall(String toolName, String result) {
        System.out.println(ansi()
                .fg(Ansi.Color.YELLOW)
                .a("  ⚙ Tool: ")
                .reset()
                .a(toolName)
                .fg(Ansi.Color.YELLOW)
                .a(" → ")
                .reset()
                .a(truncate(result, 100)));
    }

    public void printSystemMessage(String message) {
        System.out.println(ansi()
                .fgBright(Ansi.Color.BLACK)
                .a("System: ")
                .a(message)
                .reset());
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Format provider tag for prompt (e.g., [openai])
     */
    public String formatProviderTag(String provider) {
        return ansi()
                .a("[")
                .fg(Ansi.Color.YELLOW)
                .a(provider)
                .reset()
                .a("]")
                .toString();
    }

    /**
     * Format agent tag for prompt (e.g., master)
     */
    public String formatAgentTag(String agentName) {
        return ansi()
                .a(" ")
                .fg(Ansi.Color.GREEN)
                .a(agentName)
                .reset()
                .toString();
    }

    /**
     * Format status tag for prompt (e.g., [processing])
     */
    public String formatStatusTag(String status) {
        return ansi()
                .a(" [")
                .fg(Ansi.Color.MAGENTA)
                .a(status)
                .reset()
                .a("]")
                .toString();
    }

    /**
     * Print agent-to-agent communication message.
     */
    public void printAgentCommunication(String fromAgent, String toAgent, String message) {
        System.out.println(ansi()
                .fg(Ansi.Color.MAGENTA)
                .a("🔄 [")
                .a(fromAgent)
                .a(" → ")
                .a(toAgent)
                .a("]: ")
                .reset()
                .a(truncate(message, 100)));
    }

    /**
     * Print agent response in collaboration context.
     */
    public void printAgentCollaborationResponse(String fromAgent, String toAgent, String response, long responseTimeMs) {
        System.out.println(ansi()
                .fg(Ansi.Color.MAGENTA)
                .a("📥 [")
                .a(fromAgent)
                .a(" → ")
                .a(toAgent)
                .a("]: ")
                .reset()
                .a(truncate(response, 100))
                .fg(Ansi.Color.YELLOW)
                .a(" (")
                .a(responseTimeMs + "ms")
                .a(")")
                .reset());
    }

    /**
     * Print tool call initiation.
     */
    public void printToolCallStart(String agentName, String toolName) {
        System.out.println(ansi()
                .fg(Ansi.Color.YELLOW)
                .a("🔧 [")
                .a(agentName)
                .a("] Calling tool: ")
                .reset()
                .a(toolName));
    }

    /**
     * Print tool execution result with timing.
     */
    public void printToolCallResult(boolean success, String result, long executionTimeMs) {
        String status = success ? "✓" : "✗";
        Ansi.Color color = success ? Ansi.Color.GREEN : Ansi.Color.RED;

        System.out.println(ansi()
                .fg(color)
                .a("  " + status + " ")
                .reset()
                .a("Result: ")
                .a(truncate(result, 100))
                .fg(Ansi.Color.YELLOW)
                .a(" (")
                .a(executionTimeMs + "ms")
                .a(")")
                .reset());
    }
}
