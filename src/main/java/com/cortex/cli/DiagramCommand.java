package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Command(name = "diagram", description = "Generate ASCII architecture diagram")
public class DiagramCommand implements Runnable {
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;
    @Option(names = {"-p", "--project"}, description = "Path to project root", required = true)
    private String project;
    @Option(names = {"-l", "--lang"}, description = "Language code", defaultValue = "es")
    private String lang;

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";

    @Override
    public void run() {
        try {
            Path contextPath = Path.of(project, ".architect", "context.json");
            if (!Files.exists(contextPath)) {
                System.out.println("\u001B[91m  Error: No context. Run 'cortex init " + project + "' first." + RESET);
                return;
            }

            Gson gson = new Gson();
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("context", gson.fromJson(Files.readString(contextPath), Object.class));
            bodyMap.put("lang", lang);
            String token = TokenHelper.loadToken();
            if (token != null) bodyMap.put("token", token);

            String body = gson.toJson(bodyMap);

            System.out.println();
            System.out.println(BOLD + CYAN + "  CORTEX DIAGRAM" + RESET);
            System.out.println(DIM + "  Generating architecture diagram..." + RESET);
            System.out.println();

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/diagram"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            if (json.has("diagram")) {
                String diagram = json.get("diagram").getAsString();
                for (String line : diagram.split("\n")) {
                    System.out.println("  " + CYAN + line + RESET);
                }
            } else {
                System.out.println("\u001B[91m  Error: " + response.body() + RESET);
            }
            System.out.println();

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to AI service." + RESET);
        }
    }
}
