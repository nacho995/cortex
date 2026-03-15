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

@Command(name = "ask", description = "Ask a specialized expert agent")
public class AskCommand implements Runnable {
    @Option(names = {"-s", "--server"}, description = "AI service URL", defaultValue = "https://cortex-ai.fly.dev")
    private String server;
    @Option(names = {"-p", "--project"}, description = "Project directory for context")
    private String project;
    @Option(names = {"-l", "--lang"}, description = "Language code", defaultValue = "es")
    private String lang;
    @Option(names = {"--save"}, description = "Save generated files to project")
    private boolean save;
    @Parameters(index = "0", description = "Expert + question. Example: @java 'implement JWT auth'")
    private String input;

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String YELLOW = "\u001B[38;2;255;200;0m";
    private static final String RED = "\u001B[91m";

    private static final Map<String, String> EXPERT_COLORS = Map.ofEntries(
        Map.entry("java", "\u001B[38;2;255;140;0m"),
        Map.entry("spring", "\u001B[38;2;0;200;0m"),
        Map.entry("angular", "\u001B[38;2;220;0;0m"),
        Map.entry("react", "\u001B[38;2;97;218;251m"),
        Map.entry("python", "\u001B[38;2;55;118;171m"),
        Map.entry("dotnet", "\u001B[38;2;140;80;255m"),
        Map.entry("node", "\u001B[38;2;104;159;56m"),
        Map.entry("devops", "\u001B[38;2;0;230;120m"),
        Map.entry("database", "\u001B[38;2;255;165;0m"),
        Map.entry("security", "\u001B[38;2;255;80;80m"),
        Map.entry("css", "\u001B[38;2;255;105;180m"),
        Map.entry("testing", "\u001B[38;2;180;180;0m")
    );

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".html", ".css",
        ".json", ".xml", ".yml", ".yaml", ".toml", ".properties"
    );

    private static final Set<String> IGNORE_DIRS = Set.of(
        "node_modules", ".git", "target", "build", "dist", "__pycache__",
        ".venv", "venv", ".idea", ".vscode", ".architect", "coverage"
    );

    @Override
    public void run() {
        try {
            // Parse @expert question
            String expert;
            String question;
            
            if (input.startsWith("@")) {
                int spaceIdx = input.indexOf(' ');
                if (spaceIdx == -1) {
                    showExperts();
                    return;
                }
                expert = input.substring(1, spaceIdx).toLowerCase();
                question = input.substring(spaceIdx + 1).trim();
            } else {
                // No @ prefix, show available experts
                showExperts();
                return;
            }

            if (question.isEmpty()) {
                showExperts();
                return;
            }

            String color = EXPERT_COLORS.getOrDefault(expert, CYAN);

            System.out.println();
            System.out.println(BOLD + color + "  CORTEX EXPERT: @" + expert.toUpperCase() + RESET);
            System.out.println(DIM + "  Question: " + question + RESET);

            Gson gson = new Gson();
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("expert", expert);
            bodyMap.put("question", question);
            bodyMap.put("lang", lang);

            // Add project context if available
            if (project != null) {
                Path projectPath = Path.of(project.startsWith("~") 
                    ? System.getProperty("user.home") + project.substring(1) : project).toAbsolutePath();
                
                bodyMap.put("project_path", projectPath.toString());
                System.out.println(DIM + "  Project: " + projectPath + RESET);

                // Read context.json if available
                Path contextPath = projectPath.resolve(".architect/context.json");
                if (Files.exists(contextPath)) {
                    bodyMap.put("context", gson.fromJson(Files.readString(contextPath), Object.class));
                }

                // Read project files for context
                List<Map<String, String>> filesContext = new ArrayList<>();
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
                            .limit(15)
                            .forEach(p -> {
                                try {
                                    String content = Files.readString(p);
                                    if (content.length() > 2000) content = content.substring(0, 2000) + "\n...";
                                    Map<String, String> fileMap = new HashMap<>();
                                    fileMap.put("path", projectPath.relativize(p).toString());
                                    fileMap.put("content", content);
                                    filesContext.add(fileMap);
                                } catch (IOException e) { /* skip */ }
                            });
                    }
                    bodyMap.put("files_context", filesContext);
                }
            }

            String token = TokenHelper.loadToken();
            if (token != null) bodyMap.put("token", token);

            System.out.println(DIM + "  Thinking..." + RESET);
            System.out.println();

            String body = gson.toJson(bodyMap);
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server + "/ask"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println(RED + "  Error: " + response.body() + RESET);
                return;
            }

            JsonObject json;
            try {
                json = gson.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                System.out.println(RED + "  Error parsing response" + RESET);
                return;
            }

            // Show expert name
            String expertName = json.has("name") ? json.get("name").getAsString() : expert;
            System.out.println("  " + color + "\u2501".repeat(58) + RESET);
            System.out.printf("  %s[%s]%s%n", color, expertName, RESET);
            System.out.println("  " + color + "\u2501".repeat(58) + RESET);

            // Show response
            if (json.has("response")) {
                String responseText = json.get("response").getAsString();
                for (String line : responseText.split("\n")) {
                    System.out.println("  " + line);
                }
            }

            // Save files if --save and files were generated
            if (save && json.has("files") && project != null) {
                JsonArray files = json.getAsJsonArray("files");
                if (files.size() > 0) {
                    Path projectPath = Path.of(project.startsWith("~")
                        ? System.getProperty("user.home") + project.substring(1) : project).toAbsolutePath();
                    
                    System.out.println();
                    System.out.println("  " + BOLD + GREEN + "Files saved:" + RESET);
                    for (JsonElement elem : files) {
                        JsonObject file = elem.getAsJsonObject();
                        String filePath = file.get("path").getAsString();
                        String content = file.get("content").getAsString();
                        Path fullPath = projectPath.resolve(filePath);
                        Files.createDirectories(fullPath.getParent());
                        Files.writeString(fullPath, content);
                        System.out.println("    " + GREEN + "+" + RESET + " " + filePath);
                    }
                }
            }

            System.out.println();

        } catch (IOException | InterruptedException e) {
            System.out.println(RED + "  Error: Could not connect to AI service." + RESET);
        }
    }

    private void showExperts() {
        System.out.println();
        System.out.println(BOLD + CYAN + "  AVAILABLE EXPERTS" + RESET);
        System.out.println();
        System.out.println("  " + EXPERT_COLORS.getOrDefault("java", "") + "@java" + RESET + "       Java 21, Spring Boot, JPA, Hexagonal Architecture");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("angular", "") + "@angular" + RESET + "    Angular 17+, TypeScript, RxJS, Signals");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("react", "") + "@react" + RESET + "      React 18+, Hooks, Next.js, Redux");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("python", "") + "@python" + RESET + "     Python, FastAPI, SQLAlchemy, Pydantic");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("dotnet", "") + "@dotnet" + RESET + "     C#, .NET 8+, ASP.NET Core, EF Core");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("node", "") + "@node" + RESET + "       Node.js, Express, MongoDB, Prisma");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("devops", "") + "@devops" + RESET + "     Docker, K8s, GitHub Actions, AWS");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("database", "") + "@database" + RESET + "   PostgreSQL, MongoDB, Redis, Schema Design");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("security", "") + "@security" + RESET + "   OWASP, JWT/OAuth, Encryption, Hardening");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("css", "") + "@css" + RESET + "        Modern CSS, Animations, Tailwind, Design Systems");
        System.out.println("  " + EXPERT_COLORS.getOrDefault("testing", "") + "@testing" + RESET + "    JUnit, Jest, pytest, Cypress, TDD");
        System.out.println();
        System.out.println("  " + DIM + "Usage: ask \"@expert your question\"" + RESET);
        System.out.println("  " + DIM + "Example: ask \"@java implement JWT auth in Spring Boot\"" + RESET);
        System.out.println("  " + DIM + "Example: ask -p /project --save \"@react create a navbar component\"" + RESET);
        System.out.println();
    }
}
