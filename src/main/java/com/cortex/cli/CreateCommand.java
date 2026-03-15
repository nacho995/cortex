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

@Command(name = "create", description = "Generate a project from a description")
public class CreateCommand implements Runnable {
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;
    @Option(names = {"-p", "--project"}, description = "Path to existing project for context")
    private String project;
    @Option(names = {"-o", "--output"}, description = "Output directory (default: current directory)", defaultValue = ".")
    private String output;
    @Option(names = {"-l", "--lang"}, description = "Language code", defaultValue = "es")
    private String lang;
    @Option(names = {"--from-debate"}, description = "Use last debate results as context for generation")
    private boolean fromDebate;
    @Option(names = {"--provider"}, description = "AI provider: groq (free) or openai (bring your key)", defaultValue = "groq")
    private String provider;
    @Option(names = {"--api-key"}, description = "Your API key for the provider (OpenAI, etc)")
    private String apiKey;
    @Option(names = {"--model"}, description = "Model to use (e.g. gpt-4o, gpt-4o-mini)")
    private String model;
    @Parameters(index = "0", description = "What to build (e.g. 'todo app with Spring Boot')")
    private String prompt;

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String YELLOW = "\u001B[38;2;255;200;0m";

    @Override
    public void run() {
        try {
            // Resolve ~ to home directory
            String resolvedOutput = output.startsWith("~")
                ? System.getProperty("user.home") + output.substring(1)
                : output;

            Gson gson = new Gson();
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("prompt", prompt);
            bodyMap.put("lang", lang);

            if (project != null) {
                Path contextPath = Path.of(project, ".architect", "context.json");
                if (Files.exists(contextPath)) {
                    bodyMap.put("context", gson.fromJson(Files.readString(contextPath), Object.class));
                }
            }

            if (fromDebate) {
                Path debateFile = Path.of(System.getProperty("user.home"), ".cortex", "last-debate.json");
                if (Files.exists(debateFile)) {
                    String debateContent = Files.readString(debateFile);
                    bodyMap.put("debate_context", gson.fromJson(debateContent, Object.class));
                    System.out.println(DIM + "  Using last debate as context" + RESET);
                } else {
                    System.out.println(YELLOW + "  Warning: No previous debate found. Run 'debate' first." + RESET);
                }
            }

            bodyMap.put("provider", provider);
            if (apiKey != null) bodyMap.put("api_key", apiKey);
            if (model != null) bodyMap.put("model", model);
            String token = TokenHelper.loadToken();
            if (token != null) bodyMap.put("token", token);

            String body = gson.toJson(bodyMap);

            System.out.println();
            System.out.println(BOLD + CYAN + "  CORTEX CREATE" + RESET);
            System.out.println(DIM + "  Prompt: " + prompt + RESET);
            System.out.println(DIM + "  Output: " + resolvedOutput + RESET);
            System.out.println(DIM + "  Generating project..." + RESET);
            System.out.println();

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            
            // Check for HTTP errors
            if (response.statusCode() != 200) {
                System.out.println("\u001B[91m  Error (" + response.statusCode() + "): " + responseBody + RESET);
                return;
            }

            // Try to parse as JSON
            JsonObject json;
            try {
                json = gson.fromJson(responseBody, JsonObject.class);
            } catch (Exception parseError) {
                // Not JSON - show raw response
                System.out.println("\u001B[91m  Error: Unexpected response from server:" + RESET);
                System.out.println("  " + responseBody.substring(0, Math.min(responseBody.length(), 500)));
                return;
            }

            if (json == null) {
                System.out.println("\u001B[91m  Error: Empty response from server." + RESET);
                return;
            }

            // Check for error in response
            if (json.has("detail")) {
                System.out.println("\u001B[91m  Error: " + json.get("detail").getAsString() + RESET);
                return;
            }

            if (!json.has("files") || json.getAsJsonArray("files").size() == 0) {
                // No files parsed - show raw code
                if (json.has("code")) {
                    System.out.println(json.get("code").getAsString());
                } else {
                    System.out.println("\u001B[91m  Error: No files generated." + RESET);
                }
                return;
            }

            JsonArray files = json.getAsJsonArray("files");
            Path outputDir = Path.of(resolvedOutput);
            Files.createDirectories(outputDir);

            int count = 0;
            System.out.println("  " + BOLD + GREEN + "Creating files:" + RESET);
            System.out.println();

            for (JsonElement elem : files) {
                JsonObject file = elem.getAsJsonObject();
                String filePath = file.get("path").getAsString();
                String content = file.get("content").getAsString();

                Path fullPath = outputDir.resolve(filePath);
                Files.createDirectories(fullPath.getParent());
                Files.writeString(fullPath, content);

                System.out.println("    " + GREEN + "+" + RESET + " " + filePath);
                count++;
            }

            System.out.println();
            System.out.println("  " + BOLD + count + " files created" + RESET + " in " + CYAN + outputDir.toAbsolutePath() + RESET);
            System.out.println();

            // Show tree structure
            System.out.println("  " + DIM + "Project structure:" + RESET);
            for (JsonElement elem : files) {
                String filePath = elem.getAsJsonObject().get("path").getAsString();
                String[] parts = filePath.split("/");
                String indent = "    " + "  ".repeat(parts.length - 1);
                String fileName = parts[parts.length - 1];
                System.out.println(indent + DIM + fileName + RESET);
            }
            System.out.println();

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to AI service." + RESET);
        }
    }
}
