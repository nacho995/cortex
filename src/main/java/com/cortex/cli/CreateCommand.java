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
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            if (!json.has("files")) {
                // Fallback: show raw code if no files parsed
                if (json.has("code")) {
                    System.out.println(json.get("code").getAsString());
                } else {
                    System.out.println("\u001B[91m  Error: " + response.body() + RESET);
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
