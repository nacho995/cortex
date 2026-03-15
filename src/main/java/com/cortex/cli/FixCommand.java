package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

@Command(name = "fix", description = "Fix errors by pasting the error message")
public class FixCommand implements Runnable {
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;
    @Option(names = {"-p", "--project"}, description = "Project directory", defaultValue = ".")
    private String project;
    @Option(names = {"-l", "--lang"}, description = "Language code", defaultValue = "es")
    private String lang;
    @Parameters(index = "0", description = "The error message to fix")
    private String error;

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String YELLOW = "\u001B[38;2;255;200;0m";
    private static final String RED = "\u001B[91m";

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".html", ".css",
        ".json", ".xml", ".yml", ".yaml", ".toml", ".properties", ".sql",
        ".md", ".sh", ".env"
    );

    private static final Set<String> IGNORE_DIRS = Set.of(
        "node_modules", ".git", "target", "build", "dist", "__pycache__",
        ".venv", "venv", ".idea", ".vscode", ".architect", "coverage", ".next"
    );

    @Override
    public void run() {
        try {
            String resolvedProject = project.startsWith("~")
                ? System.getProperty("user.home") + project.substring(1)
                : project;
            Path projectPath = Path.of(resolvedProject).toAbsolutePath();

            System.out.println();
            System.out.println(BOLD + RED + "  CORTEX FIX" + RESET);
            System.out.println(DIM + "  Project: " + projectPath + RESET);
            System.out.println(DIM + "  Analyzing error..." + RESET);
            System.out.println();

            // Show the error
            System.out.println("  " + RED + "Error:" + RESET);
            for (String line : error.split("\n")) {
                System.out.println("  " + DIM + line + RESET);
            }
            System.out.println();

            // Scan project files for context
            List<Map<String, String>> filesContext = new ArrayList<>();
            if (Files.isDirectory(projectPath)) {
                try (Stream<Path> walk = Files.walk(projectPath, 4)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            for (Path part : projectPath.relativize(p)) {
                                if (IGNORE_DIRS.contains(part.toString())) return false;
                            }
                            String name = p.getFileName().toString();
                            return CODE_EXTENSIONS.stream().anyMatch(name::endsWith)
                                || name.equals("Dockerfile") || name.equals("Makefile");
                        })
                        .limit(25)
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p);
                                if (content.length() > 3000) content = content.substring(0, 3000) + "\n... (truncated)";
                                Map<String, String> fileMap = new HashMap<>();
                                fileMap.put("path", projectPath.relativize(p).toString());
                                fileMap.put("content", content);
                                filesContext.add(fileMap);
                            } catch (IOException e) { /* skip */ }
                        });
                }
            }

            System.out.println(DIM + "  Read " + filesContext.size() + " project files for context" + RESET);
            System.out.println(DIM + "  Fixing..." + RESET);
            System.out.println();

            Gson gson = new Gson();
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("error", error);
            bodyMap.put("project_path", projectPath.toString());
            bodyMap.put("files_context", filesContext);
            bodyMap.put("lang", lang);
            String token = TokenHelper.loadToken();
            if (token != null) bodyMap.put("token", token);

            String body = gson.toJson(bodyMap);

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/fix"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println(RED + "  Error (" + response.statusCode() + "): " + response.body() + RESET);
                return;
            }

            JsonObject json;
            try {
                json = gson.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                System.out.println(RED + "  Error parsing response" + RESET);
                return;
            }

            if (json == null || !json.has("files")) {
                if (json != null && json.has("code")) {
                    System.out.println(json.get("code").getAsString());
                }
                return;
            }

            JsonArray files = json.getAsJsonArray("files");
            int created = 0, modified = 0;

            System.out.println("  " + BOLD + GREEN + "Fixes applied:" + RESET);
            System.out.println();

            for (JsonElement elem : files) {
                JsonObject file = elem.getAsJsonObject();
                String filePath = file.get("path").getAsString();
                String content = file.get("content").getAsString();

                Path fullPath = projectPath.resolve(filePath);
                boolean exists = Files.exists(fullPath);

                Files.createDirectories(fullPath.getParent());
                Files.writeString(fullPath, content);

                if (exists) {
                    System.out.println("    " + YELLOW + "~" + RESET + " " + filePath + DIM + " (fixed)" + RESET);
                    modified++;
                } else {
                    System.out.println("    " + GREEN + "+" + RESET + " " + filePath + DIM + " (created)" + RESET);
                    created++;
                }
            }

            System.out.println();
            System.out.println("  " + BOLD + created + " created, " + modified + " fixed" + RESET);
            System.out.println();

        } catch (IOException | InterruptedException e) {
            System.out.println(RED + "  Error: Could not connect to AI service." + RESET);
        }
    }
}
