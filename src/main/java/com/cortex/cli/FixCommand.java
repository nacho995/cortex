package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

@Command(name = "fix", description = "Auto-detect and fix project errors")
public class FixCommand implements Runnable {
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;
    @Option(names = {"-p", "--project"}, description = "Project directory", defaultValue = ".")
    private String project;
    @Option(names = {"-l", "--lang"}, description = "Language code", defaultValue = "es")
    private String lang;
    @Option(names = {"--file"}, description = "Read errors from a file")
    private String errorFile;
    @Parameters(index = "0", defaultValue = "", description = "Error description (optional, auto-detects if empty)")
    private String errorMsg;

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

    private String detectProjectType(Path projectPath) {
        if (Files.exists(projectPath.resolve("package.json"))) return "node";
        if (Files.exists(projectPath.resolve("pom.xml"))) return "maven";
        if (Files.exists(projectPath.resolve("build.gradle"))) return "gradle";
        if (Files.exists(projectPath.resolve("requirements.txt"))) return "python";
        return "unknown";
    }

    private String runBuildAndCaptureErrors(Path projectPath, String projectType) {
        try {
            String[] cmd;
            switch (projectType) {
                case "node" -> {
                    // Check if client/package.json exists (React project)
                    if (Files.exists(projectPath.resolve("client/package.json"))) {
                        cmd = new String[]{"bash", "-c", "cd " + projectPath + "/client && npx react-scripts build 2>&1 | tail -50"};
                    } else {
                        cmd = new String[]{"bash", "-c", "cd " + projectPath + " && node --check server.js 2>&1; npm run build 2>&1 | tail -50"};
                    }
                }
                case "maven" -> cmd = new String[]{"bash", "-c", "cd " + projectPath + " && mvn compile 2>&1 | tail -50"};
                case "python" -> cmd = new String[]{"bash", "-c", "cd " + projectPath + " && python -m py_compile *.py 2>&1 | tail -50"};
                default -> { return "Could not detect project type for auto-build"; }
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return "Build failed: " + e.getMessage();
        }
    }

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

            // Determine error source
            String error;
            if (errorFile != null) {
                // Read from file
                Path errPath = projectPath.resolve(errorFile);
                if (!Files.exists(errPath)) errPath = Path.of(errorFile);
                error = Files.readString(errPath);
                System.out.println(DIM + "  Errors from: " + errorFile + RESET);
            } else if (!errorMsg.isEmpty()) {
                // Use provided message
                error = errorMsg;
                System.out.println(DIM + "  Error: " + errorMsg.substring(0, Math.min(errorMsg.length(), 80)) + RESET);
            } else {
                // Auto-detect: run build and capture errors
                String projectType = detectProjectType(projectPath);
                System.out.println(DIM + "  Detected: " + projectType + " project" + RESET);
                System.out.println(DIM + "  Running build to detect errors..." + RESET);
                error = runBuildAndCaptureErrors(projectPath, projectType);
                
                if (error.trim().isEmpty() || (!error.contains("Error") && !error.contains("error") && !error.contains("ERROR") && !error.contains("failed"))) {
                    System.out.println();
                    System.out.println("  " + GREEN + "No errors detected! Project builds successfully." + RESET);
                    System.out.println();
                    return;
                }
            }

            System.out.println();

            // Show detected errors (truncated)
            String[] errorLines = error.split("\n");
            int showLines = Math.min(errorLines.length, 10);
            System.out.println("  " + RED + "Errors found:" + RESET);
            for (int i = 0; i < showLines; i++) {
                System.out.println("  " + DIM + errorLines[i] + RESET);
            }
            if (errorLines.length > showLines) {
                System.out.println("  " + DIM + "... (" + (errorLines.length - showLines) + " more lines)" + RESET);
            }
            System.out.println();

            // Scan project files
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
                                if (content.length() > 3000) content = content.substring(0, 3000) + "\n...";
                                Map<String, String> fileMap = new HashMap<>();
                                fileMap.put("path", projectPath.relativize(p).toString());
                                fileMap.put("content", content);
                                filesContext.add(fileMap);
                            } catch (IOException e) { /* skip */ }
                        });
                }
            }

            System.out.println(DIM + "  Read " + filesContext.size() + " files | Fixing..." + RESET);
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

            if (json == null || !json.has("files") || json.getAsJsonArray("files").size() == 0) {
                if (json != null && json.has("code")) {
                    System.out.println(json.get("code").getAsString());
                } else {
                    System.out.println(RED + "  Could not generate fixes." + RESET);
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
