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
import com.google.gson.JsonElement;

@Command(name = "health", description = "Analyze project health score")
public class HealthCommand implements Runnable {
    @Option(names = {"-p", "--project"}, description = "Path to project root", required = true)
    private String project;
    @Option(names = {"-l", "--lang"}, description = "Language code", defaultValue = "es")
    private String lang;

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";

    @Override
    public void run() {
        try {
            Gson gson = new Gson();
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("lang", lang);

            Path contextPath = Path.of(project, ".architect", "context.json");
            if (Files.exists(contextPath)) {
                bodyMap.put("context", gson.fromJson(Files.readString(contextPath), Object.class));
            } else {
                System.out.println("\u001B[91m  Error: No context found. Run 'cortex init' first." + RESET);
                return;
            }

            String body = gson.toJson(bodyMap);

            System.out.println();
            System.out.println(BOLD + "\u001B[38;2;0;200;255m  CORTEX HEALTH CHECK" + RESET);
            System.out.println(DIM + "  Project: " + project + RESET);
            System.out.println();

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8000/health-check"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            // Display scores
            JsonObject scores = json.getAsJsonObject("scores");
            int overall = json.has("overall") ? json.get("overall").getAsInt() : 0;

            if (scores != null) {
                for (Map.Entry<String, JsonElement> entry : scores.entrySet()) {
                    String category = entry.getKey();
                    JsonObject scoreObj = entry.getValue().getAsJsonObject();
                    int score = scoreObj.get("score").getAsInt();
                    String detail = scoreObj.has("detail") ? scoreObj.get("detail").getAsString() : "";

                    String barColor;
                    if (score >= 80) barColor = "\u001B[32m";
                    else if (score >= 60) barColor = "\u001B[33m";
                    else barColor = "\u001B[91m";

                    int filled = score / 5;
                    int empty = 20 - filled;
                    String bar = barColor + "█".repeat(filled) + "\u001B[2m░\u001B[0m".repeat(empty);

                    System.out.printf("  %-18s %s %s%3d/100%s  %s%s%s%n",
                        category, bar, barColor, score, RESET, DIM, detail, RESET);
                }
                System.out.println();

                String overallColor;
                if (overall >= 80) overallColor = "\u001B[32m";
                else if (overall >= 60) overallColor = "\u001B[33m";
                else overallColor = "\u001B[91m";

                System.out.println("  " + BOLD + "Overall: " + overallColor + overall + "/100" + RESET);
                System.out.println();
            }

            // Display recommendations
            if (json.has("recommendations")) {
                System.out.println("  " + BOLD + "Recommendations:" + RESET);
                for (JsonElement rec : json.getAsJsonArray("recommendations")) {
                    System.out.println("  \u001B[33m  >\u001B[0m " + rec.getAsString());
                }
                System.out.println();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to AI service. Is it running on port 8000?" + RESET);
        }
    }
}
