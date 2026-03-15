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
            String token = TokenHelper.loadToken();
            if (token != null) bodyMap.put("token", token);
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
                    .uri(URI.create(server + "/debate-stream"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<java.io.InputStream> response = client.send(request, 
                HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes());
                System.out.println("\u001B[91m  Error (" + response.statusCode() + "): " + errorBody + RESET);
                return;
            }

            // Read SSE stream
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body()));
            
            String line;
            String currentAgent = "";
            
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if (data.isEmpty()) continue;
                
                try {
                    com.google.gson.JsonObject event = gson.fromJson(data, com.google.gson.JsonObject.class);
                    String type = event.get("type").getAsString();
                    
                    switch (type) {
                        case "round_start" -> {
                            String label = event.get("label").getAsString();
                            System.out.println();
                            System.out.println(BOLD + "\u001B[38;2;180;180;180m" + "  ╔══════════════════════════════════════════════════════════╗" + RESET);
                            System.out.printf(BOLD + "\u001B[38;2;180;180;180m" + "  ║  %-56s║%n" + RESET, label);
                            System.out.println(BOLD + "\u001B[38;2;180;180;180m" + "  ╚══════════════════════════════════════════════════════════╝" + RESET);
                            System.out.println();
                        }
                        case "agent_start" -> {
                            String name = event.get("name").getAsString();
                            String role = event.get("role").getAsString();
                            String color = agentColor(role);
                            currentAgent = name;
                            System.out.println("  " + color + "━".repeat(58) + RESET);
                            System.out.printf("  %s[%s]%s %s(%s)%s%n", color, name.toUpperCase(), RESET, DIM, role, RESET);
                            System.out.println("  " + color + "━".repeat(58) + RESET);
                            System.out.print("  ");
                        }
                        case "token" -> {
                            String content = event.get("content").getAsString();
                            // Handle newlines in the content
                            String formatted = content.replace("\n", "\n  ");
                            System.out.print(formatted);
                            System.out.flush();
                        }
                        case "agent_end" -> {
                            System.out.println();
                            System.out.println();
                        }
                        case "consensus" -> {
                            com.google.gson.JsonObject consensus = event.getAsJsonObject("consensus");
                            com.google.gson.JsonObject votes = consensus.getAsJsonObject("votes");
                            int total = consensus.get("total").getAsInt();
                            int approve = votes.has("approve") ? votes.get("approve").getAsInt() : 0;
                            int conditional = votes.has("conditional") ? votes.get("conditional").getAsInt() : 0;
                            int reject = votes.has("reject") ? votes.get("reject").getAsInt() : 0;
                            String level = consensus.get("level").getAsString();
                            
                            System.out.println(BOLD + "\u001B[38;2;255;215;0m" + "  ╔══════════════════════════════════════════════════════════╗" + RESET);
                            System.out.println(BOLD + "\u001B[38;2;255;215;0m" + "  ║  CONSENSUS                                               ║" + RESET);
                            System.out.println(BOLD + "\u001B[38;2;255;215;0m" + "  ╚══════════════════════════════════════════════════════════╝" + RESET);
                            System.out.println();
                            
                            int barLen = 40;
                            int greenLen = total > 0 ? (approve * barLen / total) : 0;
                            int yellowLen = total > 0 ? (conditional * barLen / total) : 0;
                            int redLen = barLen - greenLen - yellowLen;
                            String bar = "\u001B[42m" + " ".repeat(greenLen) + "\u001B[43m" + " ".repeat(yellowLen) + "\u001B[41m" + " ".repeat(redLen) + RESET;
                            System.out.println("  " + bar);
                            System.out.printf("  \u001B[32m%d APPROVE\u001B[0m  \u001B[33m%d CONDITIONAL\u001B[0m  \u001B[91m%d REJECT\u001B[0m  (of %d)%n", approve, conditional, reject, total);
                            
                            String levelColor = switch (level) {
                                case "unanimous", "strong" -> "\u001B[32m";
                                case "majority" -> "\u001B[33m";
                                default -> "\u001B[91m";
                            };
                            System.out.println("  Level: " + levelColor + BOLD + level.toUpperCase() + RESET);
                            System.out.println();
                        }
                        case "done" -> {
                            // Build complete debate JSON for --from-debate
                            String savedTopic = event.has("topic") ? event.get("topic").getAsString() : topic;
                            String saveJson = gson.toJson(java.util.Map.of("topic", savedTopic));
                            Path debateFile = Path.of(System.getProperty("user.home"), ".cortex", "last-debate.json");
                            Files.createDirectories(debateFile.getParent());
                            Files.writeString(debateFile, saveJson);
                            System.out.println("  " + DIM + "Debate saved. Use 'create --from-debate' to generate code." + RESET);
                            System.out.println();
                        }
                    }
                } catch (Exception e) {
                    // Skip unparseable events
                }
            }
            reader.close();

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to AI service. Is it running on port 8000?" + RESET);
        }
    }
}
