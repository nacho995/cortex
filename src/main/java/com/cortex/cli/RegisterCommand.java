package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;

@Command(name = "register", description = "Create a Cortex account")
public class RegisterCommand implements Runnable {
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;
    @Parameters(index = "0", description = "Your email address")
    private String email;

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String DIM = "\u001B[2m";

    @Override
    public void run() {
        try {
            Gson gson = new Gson();
            String body = gson.toJson(Map.of("email", email));

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            String token = json.has("token") ? json.get("token").getAsString() : null;
            String plan = json.has("plan") ? json.get("plan").getAsString() : "free";
            String message = json.has("message") ? json.get("message").getAsString() : "";

            if (token != null) {
                // Save token locally
                Path tokenFile = Path.of(System.getProperty("user.home"), ".cortex", "token");
                Files.createDirectories(tokenFile.getParent());
                Files.writeString(tokenFile, token);

                System.out.println();
                System.out.println(BOLD + GREEN + "  Registration successful!" + RESET);
                System.out.println();
                System.out.println("  " + DIM + message + RESET);
                System.out.println("  " + BOLD + "Token: " + RESET + CYAN + token + RESET);
                System.out.println("  " + BOLD + "Plan:  " + RESET + plan);
                System.out.println("  " + BOLD + "Email: " + RESET + email);
                System.out.println();
                System.out.println("  " + DIM + "Token saved to ~/.cortex/token" + RESET);
                System.out.println("  " + DIM + "All commands will use this token automatically." + RESET);
                System.out.println();
            } else {
                System.out.println("\u001B[91m  Error: " + response.body() + RESET);
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("\u001B[91m  Error: Could not connect to service." + RESET);
        }
    }
}
