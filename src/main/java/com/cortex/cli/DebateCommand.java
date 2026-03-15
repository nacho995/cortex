package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Command(name = "debate", description = "Multi-agent architecture debate with rounds and consensus")
public class DebateCommand implements Runnable {
    @Option(names = {"-l", "--lang"}, description = "Language code (en, es, fr...)", defaultValue = "es")
    private String lang;
    @Option(names = {"-p", "--project"}, description = "Path to project (with .architect/context.json)")
    private String project;
    @Option(names = {"--adr"}, description = "Generate an ADR from the debate")
    private boolean adr;
    @Option(names = {"-r", "--rounds"}, description = "Number of debate rounds (1-5)", defaultValue = "2")
    private int rounds;
    @Option(names = {"--agents"}, description = "Path to custom agents YAML directory")
    private String agentsDir;
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;
    @Parameters(index = "0", description = "The topic to debate")
    private String topic;

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";

    private String agentColor(String role) {
        return switch (role) {
            case "architect" -> "\u001B[38;2;0;200;255m";
            case "pragmatic" -> "\u001B[38;2;255;200;0m";
            case "security"  -> "\u001B[38;2;255;80;80m";
            case "devops"    -> "\u001B[38;2;0;230;120m";
            default -> {
                int hash = Math.abs(role.hashCode());
                int r = 100 + (hash % 156);
                int g = 100 + ((hash / 156) % 156);
                int b = 100 + ((hash / 24336) % 156);
                yield String.format("\u001B[38;2;%d;%d;%dm", r, g, b);
            }
        };
    }

    private String voteSymbol(String vote) {
        if (vote == null) return "";
        return switch (vote) {
            case "approve"     -> "\u001B[32m[APPROVE]\u001B[0m";
            case "reject"      -> "\u001B[91m[REJECT]\u001B[0m";
            case "conditional" -> "\u001B[33m[CONDITIONAL]\u001B[0m";
            default            -> "\u001B[37m[?]\u001B[0m";
        };
    }

    @Override
    public void run() {
        try {
            Gson gson = new Gson();

            // Build body
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("topic", topic);
            bodyMap.put("lang", lang);
            bodyMap.put("rounds", rounds);
            if (project != null) {
                Path contextPath = Path.of(project, ".architect", "context.json");
                if (Files.exists(contextPath)) {
                    bodyMap.put("context", gson.fromJson(Files.readString(contextPath), Object.class));
                }
            }
            if (agentsDir != null) {
                bodyMap.put("agents_dir", agentsDir);
            } else if (project != null) {
                // Auto-detect agents dir in project
                Path defaultAgentsDir = Path.of(project, ".architect", "agents");
                if (Files.isDirectory(defaultAgentsDir)) {
                    bodyMap.put("agents_dir", defaultAgentsDir.toString());
                }
            }
            String body = gson.toJson(bodyMap);

            System.out.println();
            System.out.println(BOLD + "\u001B[38;2;0;200;255m" + "  CORTEX DEBATE" + RESET);
            System.out.println(DIM + "  Topic: " + topic + RESET);
            System.out.println(DIM + "  Rounds: " + rounds + " | Lang: " + lang + RESET);
            if (project != null) {
                System.out.println(DIM + "  Project: " + project + RESET);
            }
            System.out.println();

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/debate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            DebateResponse debateResponse = gson.fromJson(response.body(), DebateResponse.class);

            if (debateResponse == null || debateResponse.getRounds() == null) {
                System.out.println("\u001B[91mError: Could not parse response from AI service.\u001B[0m");
                return;
            }

            // Display each round
            for (RoundResult round : debateResponse.getRounds()) {
                String roundLabel;
                if (round.getRound() == debateResponse.getRounds().size()) {
                    roundLabel = "FINAL ROUND - VERDICT";
                } else {
                    roundLabel = "ROUND " + round.getRound();
                }
                System.out.println(BOLD + "\u001B[38;2;180;180;180m" + "  ╔══════════════════════════════════════════════════════════╗" + RESET);
                System.out.printf(BOLD + "\u001B[38;2;180;180;180m" + "  ║  %-56s║%n" + RESET, roundLabel);
                System.out.println(BOLD + "\u001B[38;2;180;180;180m" + "  ╚══════════════════════════════════════════════════════════╝" + RESET);
                System.out.println();

                for (Agent agent : round.getAgents()) {
                    String color = agentColor(agent.getRole());
                    String vote = (agent.getVote() != null) ? " " + voteSymbol(agent.getVote()) : "";
                    System.out.println("  " + color + "━".repeat(58) + RESET);
                    System.out.printf("  %s[%s]%s %s(%s)%s%s%n", color, agent.getName().toUpperCase(), RESET, DIM, agent.getRole(), RESET, vote);
                    System.out.println("  " + color + "━".repeat(58) + RESET);
                    // Indent argument text
                    for (String line : agent.getArgument().split("\n")) {
                        System.out.println("  " + line);
                    }
                    System.out.println();
                }
            }

            // Display consensus
            Consensus consensus = debateResponse.getConsensus();
            if (consensus != null) {
                System.out.println(BOLD + "\u001B[38;2;255;215;0m" + "  ╔══════════════════════════════════════════════════════════╗" + RESET);
                System.out.println(BOLD + "\u001B[38;2;255;215;0m" + "  ║  CONSENSUS                                               ║" + RESET);
                System.out.println(BOLD + "\u001B[38;2;255;215;0m" + "  ╚══════════════════════════════════════════════════════════╝" + RESET);
                System.out.println();

                Map<String, Integer> votes = consensus.getVotes();
                int total = consensus.getTotal();
                int approve = votes.getOrDefault("approve", 0);
                int conditional = votes.getOrDefault("conditional", 0);
                int reject = votes.getOrDefault("reject", 0);

                // Progress bar
                int barLen = 40;
                int greenLen = total > 0 ? (approve * barLen / total) : 0;
                int yellowLen = total > 0 ? (conditional * barLen / total) : 0;
                int redLen = barLen - greenLen - yellowLen;
                String bar = "\u001B[42m" + " ".repeat(greenLen) + "\u001B[43m" + " ".repeat(yellowLen) + "\u001B[41m" + " ".repeat(redLen) + RESET;
                System.out.println("  " + bar);
                System.out.printf("  \u001B[32m%d APPROVE\u001B[0m  \u001B[33m%d CONDITIONAL\u001B[0m  \u001B[91m%d REJECT\u001B[0m  (of %d)%n", approve, conditional, reject, total);

                String levelColor = switch (consensus.getLevel()) {
                    case "unanimous" -> "\u001B[32m";
                    case "strong" -> "\u001B[32m";
                    case "majority" -> "\u001B[33m";
                    default -> "\u001B[91m";
                };
                System.out.println("  Level: " + levelColor + BOLD + consensus.getLevel().toUpperCase() + RESET);
                System.out.println();
            }

            // Save debate results for later use with create --from-debate
            String debateJson = gson.toJson(debateResponse);
            Path debateFile = Path.of(System.getProperty("user.home"), ".cortex", "last-debate.json");
            Files.createDirectories(debateFile.getParent());
            Files.writeString(debateFile, response.body());
            System.out.println("  " + DIM + "Debate saved. Use 'create --from-debate' to generate code from this debate." + RESET);
            System.out.println();

            // Generate ADR if requested
            if (adr && debateResponse.getRounds() != null) {
                System.out.println(DIM + "  Generating ADR..." + RESET);

                // Use the last round agents for the ADR summary
                List<Agent> finalAgents = debateResponse.getRounds().get(debateResponse.getRounds().size() - 1).getAgents();
                StringBuilder agentsSummary = new StringBuilder("[");
                for (int i = 0; i < finalAgents.size(); i++) {
                    Agent a = finalAgents.get(i);
                    if (i > 0) agentsSummary.append(",");
                    Map<String, String> agentMap = new HashMap<>();
                    agentMap.put("name", a.getName());
                    agentMap.put("role", a.getRole());
                    agentMap.put("stance", a.getVote() != null ? a.getVote() : "---");
                    agentMap.put("argument", a.getArgument());
                    agentsSummary.append(gson.toJson(agentMap));
                }
                agentsSummary.append("]");

                Map<String, Object> adrBodyMap = new HashMap<>();
                adrBodyMap.put("topic", topic);
                adrBodyMap.put("lang", lang);
                adrBodyMap.put("agents", gson.fromJson(agentsSummary.toString(), Object.class));
                String adrBody = gson.toJson(adrBodyMap);

                HttpRequest adrRequest = HttpRequest.newBuilder()
                        .uri(URI.create(server + "/generate-adr"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(adrBody))
                        .build();
                HttpResponse<String> adrResponse = client.send(adrRequest, HttpResponse.BodyHandlers.ofString());
                JsonObject adrJson = gson.fromJson(adrResponse.body(), JsonObject.class);
                String adrContent = adrJson.get("adr").getAsString();
                System.out.println();
                System.out.println(adrContent);

                if (project != null) {
                    Path decisionsDir = Path.of(project, ".architect", "decisions");
                    Files.createDirectories(decisionsDir);
                    Files.writeString(decisionsDir.resolve("ADR-001.md"), adrContent);
                    System.out.println("\n\u001B[32m  ADR saved to: " + decisionsDir.resolve("ADR-001.md") + RESET);
                }
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to AI service. Is it running on port 8000?" + RESET);
        }
    }
}
