package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Command(name = "init", description = "Initialize a new project with Cortex")
public class InitCommand implements Runnable {
    @Parameters(index = "0", description = "The project path to initialize")
    private String path;

    @Override
    public void run() {
        System.out.println("Initializing project: " + path);
        try {
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8000/init"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"path\": \"%s\"}", path)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Path architectDir = Path.of(path, ".architect");
            Files.createDirectories(architectDir);
            Files.writeString(architectDir.resolve("context.json"), response.body());

            JsonObject json = new Gson().fromJson(response.body(), JsonObject.class);
            System.out.println("\u001B[32m" + "Project initialized at: " + architectDir + "\u001B[0m");
            if (json.has("message")) {
                System.out.println(json.get("message").getAsString());
            }
        } catch (IOException | InterruptedException e) {
            System.out.println(
                    "\u001B[91m" + "Error: Could not connect to AI service. Is it running on port 8000?" + "\u001B[0m");
        }
    }
}
