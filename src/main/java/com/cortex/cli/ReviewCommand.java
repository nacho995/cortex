package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

@Command(name = "review", description = "Code review with AI agent personas")
public class ReviewCommand implements Runnable {
    @Option(names = {"-p", "--project"}, description = "Path to project root", required = true)
    private String project;
    @Option(names = {"-l", "--lang"}, description = "Language code", defaultValue = "es")
    private String lang;
    @Option(names = {"--agents"}, description = "Path to custom agents YAML directory")
    private String agentsDir;
    @Parameters(index = "0", description = "File to review (relative to project root)")
    private String file;

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

    @Override
    public void run() {
        try {
            Path filePath = Path.of(project, file);
            if (!Files.exists(filePath)) {
                System.out.println("\u001B[91m  Error: File not found: " + filePath + RESET);
                return;
            }

            String fileContent = Files.readString(filePath);
            Gson gson = new Gson();

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("file_path", file);
            bodyMap.put("file_content", fileContent);
            bodyMap.put("lang", lang);

            // Add project context if available
            Path contextPath = Path.of(project, ".architect", "context.json");
            if (Files.exists(contextPath)) {
                bodyMap.put("context", gson.fromJson(Files.readString(contextPath), Object.class));
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
            System.out.println(BOLD + "\u001B[38;2;0;200;255m  CORTEX CODE REVIEW" + RESET);
            System.out.println(DIM + "  File: " + file + RESET);
            System.out.println(DIM + "  Lines: " + fileContent.split("\n").length + RESET);
            System.out.println();

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8000/review"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            JsonArray reviews = json.getAsJsonArray("reviews");

            if (reviews == null) {
                System.out.println("\u001B[91m  Error: Could not parse review response." + RESET);
                return;
            }

            for (JsonElement elem : reviews) {
                JsonObject review = elem.getAsJsonObject();
                String name = review.get("name").getAsString();
                String role = review.get("role").getAsString();
                String reviewText = review.get("review").getAsString();
                String color = agentColor(role);

                System.out.println("  " + color + "━".repeat(58) + RESET);
                System.out.printf("  %s[%s]%s %s(%s)%s%n", color, name.toUpperCase(), RESET, DIM, role, RESET);
                System.out.println("  " + color + "━".repeat(58) + RESET);
                for (String line : reviewText.split("\n")) {
                    System.out.println("  " + line);
                }
                System.out.println();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to AI service. Is it running on port 8000?" + RESET);
        }
    }
}
