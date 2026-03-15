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
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
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
                    // Auto-install dependencies if node_modules missing
                    Path nodeModules = projectPath.resolve("node_modules");
                    Path clientNodeModules = projectPath.resolve("client/node_modules");

                    if (!Files.isDirectory(nodeModules)) {
                        System.out.println("    " + DIM + "Installing backend dependencies..." + RESET);
                        try {
                            Process install = new ProcessBuilder("bash", "-c", "cd " + projectPath + " && npm install 2>&1")
                                .redirectErrorStream(true).start();
                            install.waitFor();
                        } catch (Exception e) { /* continue */ }
                    }

                    if (Files.exists(projectPath.resolve("client/package.json")) && !Files.isDirectory(clientNodeModules)) {
                        System.out.println("    " + DIM + "Installing frontend dependencies..." + RESET);
                        try {
                            Process install = new ProcessBuilder("bash", "-c", "cd " + projectPath + "/client && npm install 2>&1")
                                .redirectErrorStream(true).start();
                            install.waitFor();
                        } catch (Exception e) { /* continue */ }
                    }

                    // Run build
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

            String projectType = detectProjectType(projectPath);
            System.out.println(DIM + "  Detected: " + projectType + " project" + RESET);

            Gson gson = new Gson();
            int maxAttempts = 5;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                System.out.println();
                System.out.println(BOLD + "  Round " + attempt + "/" + maxAttempts + RESET);

                // Step 1: Detect errors
                String error;
                if (attempt == 1 && !errorMsg.isEmpty()) {
                    error = errorMsg;
                } else if (attempt == 1 && errorFile != null) {
                    Path errPath = projectPath.resolve(errorFile);
                    if (!Files.exists(errPath)) errPath = Path.of(errorFile);
                    error = Files.readString(errPath);
                } else {
                    System.out.println(DIM + "  Running build..." + RESET);
                    error = runBuildAndCaptureErrors(projectPath, projectType);
                }

                // Check if build is clean
                if (error.trim().isEmpty() || (!error.contains("Error") && !error.contains("error") && !error.contains("ERROR") && !error.contains("failed") && !error.contains("Failed"))) {
                    System.out.println();
                    System.out.println("  " + GREEN + BOLD + "Build successful! No errors." + RESET);
                    if (attempt > 1) {
                        System.out.println("  " + GREEN + "Fixed in " + (attempt - 1) + " round(s)." + RESET);
                    }
                    System.out.println();
                    return;
                }

                // Show errors (truncated)
                String[] errorLines = error.split("\n");
                int showLines = Math.min(errorLines.length, 8);
                System.out.println("  " + RED + "Errors:" + RESET);
                for (int i = 0; i < showLines; i++) {
                    System.out.println("    " + DIM + errorLines[i] + RESET);
                }
                if (errorLines.length > showLines) {
                    System.out.println("    " + DIM + "... (" + (errorLines.length - showLines) + " more lines)" + RESET);
                }

                // Step 2: Read project files - send FULL content of error files
                List<Map<String, String>> filesContext = new ArrayList<>();
                Set<String> errorFilePaths = new HashSet<>();

                // Extract file paths mentioned in errors
                for (String line : errorLines) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(/[\\w/.-]+\\.(css|js|jsx|ts|tsx|java|py|json|html))").matcher(line);
                    while (m.find()) {
                        errorFilePaths.add(m.group(1));
                    }
                }

                // Read ALL files (not truncated for error files)
                if (Files.isDirectory(projectPath)) {
                    try (Stream<Path> walk = Files.walk(projectPath, 4)) {
                        walk.filter(Files::isRegularFile)
                            .filter(p -> {
                                for (Path part : projectPath.relativize(p)) {
                                    if (IGNORE_DIRS.contains(part.toString())) return false;
                                }
                                String name = p.getFileName().toString();
                                return CODE_EXTENSIONS.stream().anyMatch(name::endsWith);
                            })
                            .limit(25)
                            .forEach(p -> {
                                try {
                                    String content = Files.readString(p);
                                    String absPath = p.toAbsolutePath().toString();
                                    // Send FULL content for files mentioned in errors
                                    boolean isErrorFile = errorFilePaths.stream().anyMatch(absPath::endsWith);
                                    if (!isErrorFile && content.length() > 3000) {
                                        content = content.substring(0, 3000) + "\n... (truncated)";
                                    }
                                    Map<String, String> fileMap = new HashMap<>();
                                    fileMap.put("path", projectPath.relativize(p).toString());
                                    fileMap.put("content", content);
                                    filesContext.add(fileMap);
                                } catch (IOException e) { /* skip */ }
                            });
                    }
                }

                System.out.println(DIM + "  Sending " + filesContext.size() + " files to AI..." + RESET);

                // Step 3: Send to AI
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
                    System.out.println(RED + "  AI Error: " + response.body() + RESET);
                    return;
                }

                JsonObject json;
                try {
                    json = gson.fromJson(response.body(), JsonObject.class);
                } catch (Exception e) {
                    System.out.println(RED + "  Parse error" + RESET);
                    return;
                }

                if (json == null || !json.has("files") || json.getAsJsonArray("files").size() == 0) {
                    System.out.println(YELLOW + "  AI couldn't generate fixes. Try again." + RESET);
                    return;
                }

                // Step 4: Write fixed files
                JsonArray files = json.getAsJsonArray("files");
                int created = 0, modified = 0;

                for (JsonElement elem : files) {
                    JsonObject file = elem.getAsJsonObject();
                    String filePath = file.get("path").getAsString();
                    String content = file.get("content").getAsString();

                    Path fullPath = projectPath.resolve(filePath);
                    boolean exists = Files.exists(fullPath);

                    Files.createDirectories(fullPath.getParent());
                    Files.writeString(fullPath, content);
                    String lintError = LintHelper.validate(fullPath);
                    if (lintError != null) {
                        System.out.println("    \u001B[91m!\u001B[0m " + filePath + " \u001B[91m" + lintError + "\u001B[0m");
                    }

                    if (exists) {
                        System.out.println("    " + YELLOW + "~" + RESET + " " + filePath);
                        modified++;
                    } else {
                        System.out.println("    " + GREEN + "+" + RESET + " " + filePath);
                        created++;
                    }
                }

                System.out.println("  " + DIM + created + " created, " + modified + " fixed" + RESET);

                // Loop continues → will rebuild and check
            }

            // If we get here, max attempts reached
            System.out.println();
            System.out.println(YELLOW + "  Max attempts reached. Some errors may remain." + RESET);
            System.out.println(DIM + "  Run 'fix' again or fix manually." + RESET);
            System.out.println();

        } catch (IOException | InterruptedException e) {
            System.out.println(RED + "  Error: Could not connect to AI service." + RESET);
        }
    }
}
