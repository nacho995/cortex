package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

@Command(name = "watch", description = "Auto-review files on change")
public class WatchCommand implements Runnable {
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
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String YELLOW = "\u001B[38;2;255;200;0m";

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".html", ".css", ".json", ".xml"
    );

    private String agentColor(String role) {
        return switch (role) {
            case "architect" -> "\u001B[38;2;0;200;255m";
            case "pragmatic" -> "\u001B[38;2;255;200;0m";
            case "security"  -> "\u001B[38;2;255;80;80m";
            case "devops"    -> "\u001B[38;2;0;230;120m";
            default          -> "\u001B[37m";
        };
    }

    @Override
    public void run() {
        try {
            Path projectPath = Path.of(project).toAbsolutePath();
            Path srcDir = projectPath.resolve("src");
            if (!Files.isDirectory(srcDir)) srcDir = projectPath;

            System.out.println();
            System.out.println(BOLD + CYAN + "  CORTEX WATCH" + RESET);
            System.out.println(DIM + "  Watching: " + srcDir + RESET);
            System.out.println(DIM + "  Press Ctrl+C to stop" + RESET);
            System.out.println();

            WatchService watcher = FileSystems.getDefault().newWatchService();
            registerRecursive(srcDir, watcher);

            Gson gson = new Gson();

            while (true) {
                WatchKey key = watcher.take();
                Path dir = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    Path changed = dir.resolve((Path) event.context());
                    String fileName = changed.getFileName().toString();

                    if (!CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith)) continue;
                    if (!Files.isRegularFile(changed)) continue;

                    String relativePath = projectPath.relativize(changed).toString();
                    System.out.println("  " + YELLOW + "[CHANGE]" + RESET + " " + relativePath);

                    // Quick review
                    try {
                        String content = Files.readString(changed);
                        Map<String, Object> bodyMap = new HashMap<>();
                        bodyMap.put("file_path", relativePath);
                        bodyMap.put("file_content", content);
                        bodyMap.put("lang", lang);
                        String token = TokenHelper.loadToken();
                        if (token != null) bodyMap.put("token", token);

                        Path contextPath = Path.of(project, ".architect", "context.json");
                        if (Files.exists(contextPath)) {
                            bodyMap.put("context", gson.fromJson(Files.readString(contextPath), Object.class));
                        }

                        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(server + "/review"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(bodyMap)))
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        JsonArray reviews = json.getAsJsonArray("reviews");

                        if (reviews != null) {
                            for (JsonElement elem : reviews) {
                                JsonObject review = elem.getAsJsonObject();
                                String name = review.get("name").getAsString();
                                String role = review.get("role").getAsString();
                                String reviewText = review.get("review").getAsString();
                                String color = agentColor(role);
                                // Show first line only for compact watch output
                                String firstLine = reviewText.split("\n")[0];
                                if (firstLine.length() > 80) firstLine = firstLine.substring(0, 77) + "...";
                                System.out.printf("    %s[%s]%s %s%n", color, name.toUpperCase(), RESET, firstLine);
                            }
                        }
                        System.out.println();
                    } catch (Exception e) {
                        System.out.println("    " + DIM + "Review skipped: " + e.getMessage() + RESET);
                    }
                }

                key.reset();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("\n  " + DIM + "Watch stopped." + RESET);
        }
    }

    private void registerRecursive(Path start, WatchService watcher) throws IOException {
        Files.walk(start, 5)
            .filter(Files::isDirectory)
            .filter(p -> {
                String name = p.getFileName().toString();
                return !name.startsWith(".") && !name.equals("node_modules") 
                    && !name.equals("target") && !name.equals("build")
                    && !name.equals("__pycache__") && !name.equals("dist");
            })
            .forEach(p -> {
                try {
                    p.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                } catch (IOException e) { /* skip */ }
            });
    }
}
