package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Command(name = "usage", description = "Check your usage and plan")
public class UsageCommand implements Runnable {
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String YELLOW = "\u001B[38;2;255;200;0m";
    private static final String DIM = "\u001B[2m";

    @Override
    public void run() {
        try {
            String token = TokenHelper.loadToken();
            if (token == null) {
                System.out.println("\u001B[91m  No token found. Run 'cortex register <email>' first." + RESET);
                return;
            }

            Gson gson = new Gson();
            String body = gson.toJson(Map.of("token", token));

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/usage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            if (json.has("error")) {
                System.out.println("\u001B[91m  Error: " + json.get("error").getAsString() + RESET);
                return;
            }

            String email = json.get("email").getAsString();
            String plan = json.get("plan").getAsString();
            int today = json.get("today").getAsInt();
            int limit = json.get("daily_limit").getAsInt();
            int remaining = json.get("remaining_today").getAsInt();
            int total = json.get("total_calls").getAsInt();
            String model = json.get("model").getAsString();

            String planColor = switch (plan) {
                case "pro" -> CYAN;
                case "enterprise" -> GREEN;
                default -> YELLOW;
            };

            // Usage bar
            int barLen = 30;
            int filled = limit > 0 ? Math.min((today * barLen / limit), barLen) : 0;
            String barColor = remaining > limit * 0.3 ? GREEN : remaining > 0 ? YELLOW : "\u001B[91m";
            String bar = barColor + "█".repeat(filled) + DIM + "░".repeat(barLen - filled) + RESET;

            System.out.println();
            System.out.println(BOLD + CYAN + "  CORTEX USAGE" + RESET);
            System.out.println();
            System.out.println("  " + BOLD + "Email:  " + RESET + email);
            System.out.println("  " + BOLD + "Plan:   " + RESET + planColor + plan.toUpperCase() + RESET);
            System.out.println("  " + BOLD + "Model:  " + RESET + model);
            System.out.println();
            System.out.println("  " + BOLD + "Today:  " + RESET + bar + " " + today + "/" + limit);
            System.out.println("  " + BOLD + "Left:   " + RESET + remaining + " calls remaining");
            System.out.println("  " + BOLD + "Total:  " + RESET + total + " calls all time");
            System.out.println();

            if (plan.equals("free")) {
                System.out.println("  " + YELLOW + "Upgrade for more calls and better models:" + RESET);
                System.out.println("    " + BOLD + "Pro ($9/mo)" + RESET + "        200 calls/day + GPT-4o-mini");
                System.out.println("    " + BOLD + "Enterprise ($29/mo)" + RESET + " Unlimited  + GPT-4o");
                System.out.println("  " + DIM + "Run 'cortex upgrade' to subscribe." + RESET);
                System.out.println();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to service." + RESET);
        }
    }
}
