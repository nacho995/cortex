package com.cortex.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import java.io.Console;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Command(name = "cortex", version = "0.1.0",
    description = "AI Architecture Decision Engine",
    subcommands = {
        DebateCommand.class,
        InitCommand.class,
        GenerateCommand.class,
        CreateCommand.class,
        AddCommand.class,
        FixCommand.class,
        ReviewCommand.class,
        HealthCommand.class,
        ContextCommand.class,
        DiagramCommand.class,
        WatchCommand.class,
        RegisterCommand.class,
        LoginCommand.class,
        UsageCommand.class,
        UpgradeCommand.class,
        AskCommand.class
    },
    mixinStandardHelpOptions = true
)
public class CortexCLI implements Runnable {

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String YELLOW = "\u001B[38;2;255;200;0m";
    private static final String RED = "\u001B[91m";

    private String gradient(String text) {
        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            double ratio = (double) i / Math.max(len - 1, 1);
            int r, g, b;
            if (ratio < 0.5) {
                double t = ratio * 2;
                r = (int)(255 * (1 - t));
                g = (int)(255 * t);
                b = (int)(255 * t);
            } else {
                double t = (ratio - 0.5) * 2;
                r = 0;
                g = 255;
                b = (int)(255 * (1 - t));
            }
            sb.append(String.format("\u001B[38;2;%d;%d;%dm%c", r, g, b, text.charAt(i)));
        }
        sb.append(RESET);
        return sb.toString();
    }

    private void printBanner() {
        System.out.println();
        System.out.println(gradient("   ██████╗ ██████╗ ██████╗ ████████╗███████╗██╗  ██╗"));
        System.out.println(gradient("  ██╔════╝██╔═══██╗██╔══██╗╚══██╔══╝██╔════╝╚██╗██╔╝"));
        System.out.println(gradient("  ██║     ██║   ██║██████╔╝   ██║   █████╗   ╚███╔╝ "));
        System.out.println(gradient("  ██║     ██║   ██║██╔══██╗   ██║   ██╔══╝   ██╔██╗ "));
        System.out.println(gradient("  ╚██████╗╚██████╔╝██║  ██║   ██║   ███████╗██╔╝ ██╗"));
        System.out.println(gradient("   ╚═════╝ ╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚══════╝╚═╝  ╚═╝"));
        System.out.println(gradient("        AI Architecture Decision Engine v0.1.0"));
        System.out.println();
    }

    private void printWelcome() {
        System.out.println("  " + BOLD + "Welcome to Cortex!" + RESET);
        System.out.println("  " + DIM + "Type a command below or 'help' to see all options." + RESET);
        System.out.println("  " + DIM + "Type 'exit' or 'quit' to leave." + RESET);
        System.out.println();
        System.out.println("  " + CYAN + "Quick start:" + RESET);
        System.out.println("    " + GREEN + "debate" + RESET + " \"should we use microservices\"");
        System.out.println("    " + GREEN + "init" + RESET + " /path/to/your/project");
        System.out.println("    " + GREEN + "review" + RESET + " -p /path src/Main.java");
        System.out.println("    " + GREEN + "health" + RESET + " -p /path");
        System.out.println("    " + GREEN + "create" + RESET + " \"todo app with Spring Boot and PostgreSQL\"");
        System.out.println("    " + GREEN + "add" + RESET + " -p /path \"add JWT authentication\"");
        System.out.println("    " + GREEN + "fix" + RESET + " -p /path                   " + DIM + "auto-detect & fix errors" + RESET);
        System.out.println("    " + GREEN + "diagram" + RESET + " -p /path              " + DIM + "ASCII architecture diagram" + RESET);
        System.out.println("    " + GREEN + "watch" + RESET + " -p /path               " + DIM + "auto-review on file changes" + RESET);
        System.out.println("    " + GREEN + "ask" + RESET + " \"@java implement JWT auth\"  " + DIM + "ask specialized experts" + RESET);
        System.out.println();
        System.out.println("  " + DIM + "Account:" + RESET);
        System.out.println("    " + GREEN + "register" + RESET + " your@email.com");
        System.out.println("    " + GREEN + "usage" + RESET + "                    " + DIM + "check your plan & usage" + RESET);
        System.out.println("    " + GREEN + "upgrade" + RESET + "                  " + DIM + "see Pro & Enterprise plans" + RESET);
    }

    private void printHelp() {
        System.out.println();
        System.out.println("  " + BOLD + CYAN + "Available Commands:" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "debate" + RESET + " \"topic\"                 Multi-agent debate with consensus");
        System.out.println("    " + DIM + "Options: --project, --lang, --rounds, --adr, --agents, --server" + RESET);
        System.out.println("    " + DIM + "Example: debate --project /my-app --rounds 3 \"add Redis caching\"" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "init" + RESET + " /path                     Scan project and generate context");
        System.out.println("    " + DIM + "Example: init /home/user/my-project" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "review" + RESET + " -p /path file.java       Code review with 4 agent perspectives");
        System.out.println("    " + DIM + "Example: review -p /my-app src/main/java/App.java" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "health" + RESET + " -p /path                 Project health score (5 dimensions)");
        System.out.println("    " + DIM + "Example: health -p /my-app" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "generate" + RESET + " -p /path --from-adr F  Generate code from ADR decision");
        System.out.println("    " + DIM + "Example: generate -p /my-app --from-adr ADR-001.md" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "context" + RESET + " -p /path                Show current project context");
        System.out.println("    " + DIM + "Example: context -p /my-app" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "create" + RESET + " \"description\"            Generate project in current dir");
        System.out.println("    " + DIM + "Example: create \"todo app with MERN stack\"" + RESET);
        System.out.println("    " + DIM + "Example: create -o ~/dir \"REST API with FastAPI\"" + RESET);
        System.out.println("    " + DIM + "Pro:     create --provider openai --api-key sk-... \"complex app\"" + RESET);
        System.out.println("    " + DIM + "After debate: create --from-debate \"implement the decision\"" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "add" + RESET + " -p /path \"instruction\"    Add features to existing project");
        System.out.println("    " + DIM + "Example: add \"add JWT authentication\"" + RESET);
        System.out.println("    " + DIM + "Example: add -p /my-app \"add unit tests for UserService\"" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "fix" + RESET + " -p /path                    Auto-detect and fix project errors");
        System.out.println("    " + DIM + "Example: fix -p .                       (auto-runs build)" + RESET);
        System.out.println("    " + DIM + "Example: fix -p . --file errors.txt     (from file)" + RESET);
        System.out.println("    " + DIM + "Example: fix -p . \"short error desc\"    (one-liner)" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "diagram" + RESET + " -p /path                Generate ASCII architecture diagram");
        System.out.println("    " + DIM + "Example: diagram -p /my-app" + RESET);
        System.out.println();
        System.out.println("  " + GREEN + "watch" + RESET + " -p /path                  Auto-review on file changes (live)");
        System.out.println("    " + DIM + "Example: watch -p /my-app" + RESET);
        System.out.println();
        System.out.println("  " + BOLD + CYAN + "Expert Agents:" + RESET);
        System.out.println("  " + GREEN + "ask" + RESET + " \"@expert question\"          Ask a specialized expert");
        System.out.println("    " + DIM + "Experts: @java @angular @react @python @dotnet @node @devops @database @security @css @testing" + RESET);
        System.out.println("    " + DIM + "Example: ask \"@react create todo list with hooks\"" + RESET);
        System.out.println("    " + DIM + "Example: ask -p /project --save \"@java add JWT auth\"" + RESET);
        System.out.println();
        System.out.println("  " + BOLD + CYAN + "Account:" + RESET);
        System.out.println("  " + GREEN + "register" + RESET + " <email>             Create account & get token");
        System.out.println("  " + GREEN + "login" + RESET + " <token>                Save token from another device");
        System.out.println("  " + GREEN + "usage" + RESET + "                        Check your usage & plan");
        System.out.println("  " + GREEN + "upgrade" + RESET + "                      View plans & pricing");
        System.out.println();
        System.out.println("  " + BOLD + CYAN + "Session:" + RESET);
        System.out.println("  " + GREEN + "proyecto" + RESET + " /path              Set active project for this session");
        System.out.println("  " + GREEN + "project" + RESET + " /path               Set active project for this session");
        System.out.println("    " + DIM + "Example: proyecto ~/my-app  (prompt shows [my-app] when set)" + RESET);
        System.out.println();
        System.out.println("  " + YELLOW + "help" + RESET + "                            Show this help");
        System.out.println("  " + YELLOW + "clear" + RESET + "                           Clear the screen");
        System.out.println("  " + RED + "exit" + RESET + " / " + RED + "quit" + RESET + "                      Exit Cortex");
        System.out.println();
    }

    private String[] parseArgs(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (c == ' ') {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args.toArray(new String[0]);
    }

    private String[] interpretNaturalLanguage(String input, String lower, String currentProject) {
        // Extract paths from input
        String detectedPath = null;
        String userHome = System.getProperty("user.home");
        String cwd = System.getProperty("user.dir");
        for (String word : input.split("\\s+")) {
            // Remove trailing punctuation
            word = word.replaceAll("[.,;:!?]+$", "");
            
            // Explicit paths: ~/project, /home/user/project, ./project
            if (word.startsWith("~/") || word.startsWith("/") || word.startsWith("./")) {
                detectedPath = word;
                break;
            }
            
            // Skip stopwords
            if (word.toLowerCase().matches("(el|la|los|las|de|del|en|con|para|una|uno|un|mi|tu|su|al|por|que|como|sobre|si|no|y|o|a|es|esta|este|esto)")) {
                continue;
            }
            
            // Check if word looks like a project name (has hyphen, dot, or is camelCase)
            if (word.matches("[a-zA-Z][a-zA-Z0-9._-]*") && word.length() > 2) {
                // Check in home directory first (most common)
                java.nio.file.Path homePath = java.nio.file.Path.of(userHome, word);
                if (java.nio.file.Files.isDirectory(homePath)) {
                    detectedPath = homePath.toString();
                    break;
                }
                // Check current working directory
                java.nio.file.Path cwdPath = java.nio.file.Path.of(cwd, word);
                if (java.nio.file.Files.isDirectory(cwdPath)) {
                    detectedPath = cwdPath.toString();
                    break;
                }
                // Check parent of cwd
                java.nio.file.Path parentPath = java.nio.file.Path.of(cwd).getParent();
                if (parentPath != null) {
                    java.nio.file.Path siblingPath = parentPath.resolve(word);
                    if (java.nio.file.Files.isDirectory(siblingPath)) {
                        detectedPath = siblingPath.toString();
                        break;
                    }
                }
            }
        }

        // Fall back to current project if no path detected
        if (detectedPath == null && currentProject != null) {
            detectedPath = currentProject;
        }

        // Check if it's a runtime description (should go to chat, not fix)
        boolean isRuntimeDescription = lower.matches(".*(me dice|me sale|me aparece|cuando|en la pagina|al intentar|al hacer|al pulsar|al clickar|el frontend|la app dice|la aplicacion dice|en el navegador|en pantalla).*");
        
        // FIX intent: only for actual build/code fix requests, not runtime descriptions
        if (!isRuntimeDescription && lower.matches(".*(arregla|fix|bug|soluciona|repara|corrige|no compila|si hay errores|sigue fallando|todavia|aun no|still).*")) {
            if (detectedPath != null) {
                return new String[]{"fix", "-p", detectedPath};
            }
            return new String[]{"fix", "-p", "."};
        }

        // CREATE intent: crea, create, haz, hazme, genera, construye, build
        if (lower.matches(".*(^crea |^hazme |^haz |^genera |^construye |^build |^make |crear |generar ).*")) {
            String prompt = input;
            // Remove trigger words at the start
            prompt = prompt.replaceFirst("(?i)^(crea|hazme|haz|genera|construye|build|make)\\s+", "");
            if (detectedPath != null) {
                return new String[]{"create", "-o", detectedPath, prompt};
            }
            return new String[]{"create", prompt};
        }

        // DEBATE intent: debate, debati, opina, discute, que piensas
        if (lower.matches(".*(^debate |debatir|opina|discute|que piensas|que opinas|analiza si|deberian).*")) {
            String topic = input;
            topic = topic.replaceFirst("(?i)^(debate|debatir|opina|discute|que piensas|que opinas)\\s+(sobre |de |si )?", "");
            if (detectedPath != null) {
                return new String[]{"debate", "-p", detectedPath, topic};
            }
            return new String[]{"debate", topic};
        }

        // ASK intent: pregunta, ask, experto, @expert
        if (lower.matches(".*\\s@\\w+.*") || lower.startsWith("@") || lower.matches(".*(pregunta|experto|expert|consulta).*")) {
            // Extract @expert if present
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?<![\\w.])@(\\w+)").matcher(input);
            if (matcher.find()) {
                String expert = matcher.group(0);
                String question = input.replaceFirst("(?i)(preguntale? al experto de \\w+|ask |consulta )?(sobre )?", "");
                if (detectedPath != null) {
                    return new String[]{"ask", "-p", detectedPath, expert + " " + question};
                }
                return new String[]{"ask", expert + " " + question};
            }
            // Extract expert from "preguntale al experto de java"
            java.util.regex.Matcher expertMatcher = java.util.regex.Pattern.compile("(?i)experto de (\\w+)").matcher(input);
            if (expertMatcher.find()) {
                String expert = "@" + expertMatcher.group(1).toLowerCase();
                String question = input.replaceFirst("(?i)preguntale? al experto de \\w+\\s*", "");
                if (question.isEmpty()) question = "ayudame con mi proyecto";
                return new String[]{"ask", expert + " " + question};
            }
        }

        // REVIEW intent: revisa, review, analiza el codigo
        if (lower.matches(".*(revisa|review|analiza el codigo|code review|revisame).*")) {
            // Try to extract file path
            java.util.regex.Matcher fileMatcher = java.util.regex.Pattern.compile("(\\S+\\.(java|py|js|ts|jsx|tsx|css|html))").matcher(input);
            if (fileMatcher.find() && detectedPath != null) {
                return new String[]{"review", "-p", detectedPath, fileMatcher.group(1)};
            }
            if (detectedPath != null) {
                return new String[]{"review", "-p", detectedPath, "."};
            }
        }

        // HEALTH intent: salud, health, como esta, estado, score
        if (lower.matches(".*(salud|health|como esta|estado del|score|diagnostico|chequea).*")) {
            if (detectedPath != null) {
                return new String[]{"health", "-p", detectedPath};
            }
            return new String[]{"health", "-p", "."};
        }

        // ADD intent: agrega, add, anade, mete, pon, mejora, cambia, modifica, actualiza
        if (lower.matches(".*(^agrega |^add |^añade |^mete |^pon |^mejora |^cambia |^modifica |^actualiza ).*")) {
            String instruction = input.replaceFirst("(?i)^(agrega|add|añade|mete|pon|mejora|cambia|modifica|actualiza)\\s+(a |al |en |el |la |los |las )?", "");
            if (detectedPath != null) {
                return new String[]{"add", "-p", detectedPath, instruction};
            }
            return new String[]{"add", "-p", ".", instruction};
        }

        // DIAGRAM intent: diagrama, diagram, arquitectura
        if (lower.matches(".*(diagrama|diagram|arquitectura|estructura).*") && detectedPath != null) {
            return new String[]{"diagram", "-p", detectedPath};
        }

        // INIT intent: escanea, scan, init, inicializa
        if (lower.matches(".*(escanea|scan|inicializa|init).*")) {
            if (detectedPath != null) {
                return new String[]{"init", detectedPath};
            }
        }

        // CONTEXT intent: contexto, context, muestra el proyecto
        if (lower.matches(".*(contexto|context|muestra el proyecto|info del proyecto).*")) {
            if (detectedPath != null) {
                return new String[]{"context", "-p", detectedPath};
            }
        }

        // UPGRADE intent: planes, plans, upgrade, precio, price, suscripcion
        if (lower.matches(".*(planes|plans|upgrade|precio|price|suscri).*")) {
            return new String[]{"upgrade"};
        }

        // USAGE intent: uso, usage, cuanto llevo, mi plan
        if (lower.matches(".*(mi uso|usage|cuanto llevo|mi plan|mi cuenta).*")) {
            return new String[]{"usage"};
        }

        // Nothing matched - return null (will be ignored)
        return null;
    }

    private void handleChat(String message, String currentProject, java.util.List<String> chatHistory) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            
            if (currentProject == null) {
                System.out.println("  " + DIM + "No hay proyecto activo. Usa 'proyecto <nombre>' primero." + RESET);
                return;
            }
            
            java.nio.file.Path pp = java.nio.file.Path.of(currentProject);
            
            // Detect if user wants to run a command
            String msgLower = message.toLowerCase();
            if (msgLower.matches(".*(arranca|ejecuta|corre|run|start|inicia|lanza|npm|node|mvn|python).*") && !msgLower.contains("crea") && !msgLower.contains("cambia")) {
                handleRunCommand(message, currentProject);
                return;
            }
            
            // Build context: directory tree + FULL relevant files
            StringBuilder context = new StringBuilder();
            
            // 1. Project type
            String projectType = "unknown";
            if (java.nio.file.Files.exists(pp.resolve("client/package.json"))) projectType = "MERN";
            else if (java.nio.file.Files.exists(pp.resolve("package.json"))) projectType = "Node.js";
            else if (java.nio.file.Files.exists(pp.resolve("pom.xml"))) projectType = "Java/Spring Boot";
            else if (java.nio.file.Files.exists(pp.resolve("requirements.txt"))) projectType = "Python";
            context.append("[Type: ").append(projectType).append("]\n");
            
            // 2. Directory tree
            context.append("[Structure:\n");
            try (java.util.stream.Stream<java.nio.file.Path> tree = java.nio.file.Files.walk(pp, 4)) {
                tree.filter(p -> {
                        String s = p.toString();
                        return !s.contains("node_modules") && !s.contains(".git") && !s.contains("/build/") && !s.contains("/dist/");
                    })
                    .sorted()
                    .forEach(p -> {
                        String rel = pp.relativize(p).toString();
                        if (rel.isEmpty()) return;
                        int depth = rel.split("/").length - 1;
                        String indent = "  ".repeat(depth);
                        String name = p.getFileName().toString();
                        if (java.nio.file.Files.isDirectory(p)) context.append(indent).append(name).append("/\n");
                        else context.append(indent).append(name).append("\n");
                    });
            } catch (Exception e) { /* skip */ }
            context.append("]\n");
            
            // 3. Smart file selection: find files relevant to the user's message
            java.util.List<java.nio.file.Path> relevantFiles = new java.util.ArrayList<>();
            
            // Extract keywords from message to find relevant files
            String[] keywords = message.toLowerCase()
                .replaceAll("[^a-z0-9\\s/.]", "")
                .split("\\s+");
            
            try (java.util.stream.Stream<java.nio.file.Path> allFiles = java.nio.file.Files.walk(pp, 5)) {
                java.util.List<java.nio.file.Path> sourceFiles = allFiles
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString();
                        return !s.contains("node_modules") && !s.contains(".git") && !s.contains("/build/") && !s.contains("/dist/") && !s.contains("package-lock");
                    })
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".css") || name.endsWith(".json") || name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".html") || name.endsWith(".env");
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                // Score files by relevance to message
                for (java.nio.file.Path f : sourceFiles) {
                    String relPath = pp.relativize(f).toString().toLowerCase();
                    String fileName = f.getFileName().toString().toLowerCase();
                    int score = 0;
                    
                    for (String kw : keywords) {
                        if (kw.length() < 3) continue;
                        if (relPath.contains(kw)) score += 3;
                        if (fileName.contains(kw)) score += 5;
                        // Check file content for keyword
                        try {
                            String content = java.nio.file.Files.readString(f);
                            if (content.toLowerCase().contains(kw)) score += 1;
                        } catch (Exception e) { /* skip */ }
                    }
                    
                    // Always include key config files
                    if (fileName.equals("package.json") || fileName.equals(".env") || fileName.equals("server.js") || fileName.equals("app.jsx") || fileName.equals("app.js")) {
                        score += 10;
                    }
                    
                    if (score > 0) relevantFiles.add(f);
                }
                
                // Sort by relevance (most relevant first) and take top 10
                // If no relevant files found, take the first 10 source files
                if (relevantFiles.isEmpty()) {
                    relevantFiles.addAll(sourceFiles.subList(0, Math.min(sourceFiles.size(), 10)));
                } else if (relevantFiles.size() > 10) {
                    relevantFiles = relevantFiles.subList(0, 10);
                }
            } catch (Exception e) { /* skip */ }
            
            // 4. Read FULL content of relevant files
            context.append("[Files:\n");
            for (java.nio.file.Path f : relevantFiles) {
                try {
                    String relPath = pp.relativize(f).toString();
                    String content = java.nio.file.Files.readString(f);
                    // Send FULL content, only truncate if > 3000 chars
                    if (content.length() > 3000) content = content.substring(0, 3000) + "\n... (truncated, " + content.length() + " chars total)";
                    context.append("--- ").append(relPath).append(" ---\n").append(content).append("\n\n");
                } catch (Exception e) { /* skip */ }
            }
            context.append("]\n");
            
            // 5. Build message with history
            StringBuilder fullMessage = new StringBuilder();
            fullMessage.append(context);
            int histStart = Math.max(0, chatHistory.size() - 6);
            for (int i = histStart; i < chatHistory.size(); i++) {
                fullMessage.append(chatHistory.get(i)).append("\n");
            }
            fullMessage.append("User: ").append(message);
            
            // 6. Send to AI
            String body = gson.toJson(java.util.Map.of("message", fullMessage.toString(), "lang", "es"));
            
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://cortex-ai.fly.dev/chat"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .build();
            
            System.out.println("  " + DIM + "Pensando..." + RESET);
            
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                com.google.gson.JsonObject json = gson.fromJson(response.body(), com.google.gson.JsonObject.class);
                if (json != null && json.has("response")) {
                    String text = json.get("response").getAsString();
                    System.out.println();
                    
                    // Print response (limit 40 lines)
                    String[] lines = text.split("\n");
                    for (int i = 0; i < Math.min(lines.length, 40); i++) {
                        System.out.println("  " + lines[i]);
                    }
                    
                    // Write files if AI generated FILE: blocks
                    if (json.has("files") && json.getAsJsonArray("files") != null) {
                        com.google.gson.JsonArray files = json.getAsJsonArray("files");
                        if (files.size() > 0) {
                            System.out.println();
                            int created = 0, modified = 0;
                            for (com.google.gson.JsonElement elem : files) {
                                com.google.gson.JsonObject file = elem.getAsJsonObject();
                                String filePath = file.get("path").getAsString();
                                String content = file.get("content").getAsString();
                                java.nio.file.Path fullPath = pp.resolve(filePath);
                                boolean existed = java.nio.file.Files.exists(fullPath);
                                java.nio.file.Files.createDirectories(fullPath.getParent());
                                java.nio.file.Files.writeString(fullPath, content);
                                if (existed) {
                                    System.out.println("    " + YELLOW + "~" + RESET + " " + filePath + DIM + " (modified)" + RESET);
                                    modified++;
                                } else {
                                    System.out.println("    " + GREEN + "+" + RESET + " " + filePath + DIM + " (created)" + RESET);
                                    created++;
                                }
                            }
                            System.out.println("  " + DIM + created + " created, " + modified + " modified" + RESET);
                        }
                    }
                    
                    // Save history
                    chatHistory.add("User: " + message);
                    chatHistory.add("Cortex: " + (text.length() > 500 ? text.substring(0, 500) : text));
                    while (chatHistory.size() > 12) chatHistory.remove(0);
                }
            } else {
                System.out.println("  " + DIM + "Error del servidor. Intenta de nuevo." + RESET);
            }
        } catch (java.net.http.HttpTimeoutException e) {
            System.out.println("  " + DIM + "Timeout. Intenta de nuevo." + RESET);
        } catch (Exception e) {
            System.out.println("  " + DIM + "Error: " + e.getMessage() + RESET);
        }
    }
    
    private void handleRunCommand(String message, String currentProject) {
        try {
            java.nio.file.Path pp = java.nio.file.Path.of(currentProject);
            String projectType = "unknown";
            if (java.nio.file.Files.exists(pp.resolve("client/package.json"))) projectType = "MERN";
            else if (java.nio.file.Files.exists(pp.resolve("package.json"))) projectType = "Node.js";
            else if (java.nio.file.Files.exists(pp.resolve("pom.xml"))) projectType = "Java";
            else if (java.nio.file.Files.exists(pp.resolve("requirements.txt"))) projectType = "Python";
            
            System.out.println();
            switch (projectType) {
                case "MERN" -> {
                    // Check .env for port
                    String port = "5000";
                    java.nio.file.Path envFile = pp.resolve(".env");
                    if (java.nio.file.Files.exists(envFile)) {
                        for (String line : java.nio.file.Files.readAllLines(envFile)) {
                            if (line.startsWith("PORT=")) port = line.split("=")[1].trim();
                        }
                    }
                    System.out.println("  " + BOLD + CYAN + "Para arrancar tu proyecto MERN:" + RESET);
                    System.out.println();
                    System.out.println("  " + GREEN + "1." + RESET + " MongoDB (si no esta corriendo):");
                    System.out.println("     " + DIM + "docker start mongodb" + RESET);
                    System.out.println();
                    System.out.println("  " + GREEN + "2." + RESET + " Backend (terminal 1):");
                    System.out.println("     " + BOLD + "cd " + currentProject + " && npm run dev" + RESET);
                    System.out.println("     " + DIM + "→ http://localhost:" + port + RESET);
                    System.out.println();
                    System.out.println("  " + GREEN + "3." + RESET + " Frontend (terminal 2):");
                    System.out.println("     " + BOLD + "cd " + currentProject + "/client && npm start" + RESET);
                    System.out.println("     " + DIM + "→ http://localhost:3000" + RESET);
                }
                case "Node.js" -> {
                    System.out.println("  " + BOLD + "cd " + currentProject + " && npm run dev" + RESET);
                }
                case "Java" -> {
                    System.out.println("  " + BOLD + "cd " + currentProject + " && mvn spring-boot:run" + RESET);
                }
                case "Python" -> {
                    System.out.println("  " + BOLD + "cd " + currentProject + " && uvicorn main:app --reload" + RESET);
                }
                default -> System.out.println("  " + DIM + "No se como arrancar este tipo de proyecto." + RESET);
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("  " + DIM + "Error: " + e.getMessage() + RESET);
        }
    }

    private void interactiveMode() {
        printBanner();
        printWelcome();

        Scanner scanner = new Scanner(System.in);
        String currentProject = null;
        String currentProjectName = null;
        java.util.List<String> chatHistory = new java.util.ArrayList<>();

        while (true) {
            String prompt;
            if (currentProjectName != null) {
                prompt = "  " + BOLD + CYAN + "cortex" + RESET + " " + DIM + "[" + currentProjectName + "]" + RESET + DIM + " > " + RESET;
            } else {
                prompt = "  " + BOLD + CYAN + "cortex" + RESET + DIM + " > " + RESET;
            }

            System.out.print(prompt);
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("q")) {
                System.out.println();
                System.out.println("  " + DIM + "Goodbye! Happy architecting." + RESET);
                System.out.println();
                break;
            }

            if (line.equalsIgnoreCase("help") || line.equals("?")) {
                printHelp();
                continue;
            }

            if (line.equalsIgnoreCase("clear") || line.equalsIgnoreCase("cls")) {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                printBanner();
                continue;
            }

            if (line.equalsIgnoreCase("banner")) {
                printBanner();
                continue;
            }

            // Natural language intent detection
            String lineLower = line.toLowerCase();
            String[] cmdArgs;

            // Set project manually with "proyecto <path>" or "project <path>"
            if (lineLower.startsWith("proyecto ") || lineLower.startsWith("project ")) {
                String path = line.substring(line.indexOf(' ') + 1).trim();
                if (path.startsWith("~")) {
                    path = System.getProperty("user.home") + path.substring(1);
                }
                java.nio.file.Path p = java.nio.file.Path.of(path).toAbsolutePath();
                if (java.nio.file.Files.isDirectory(p)) {
                    currentProject = p.toString();
                    currentProjectName = p.getFileName().toString();
                    System.out.println("  " + GREEN + "Project set: " + currentProject + RESET);
                } else {
                    // Check home dir
                    p = java.nio.file.Path.of(System.getProperty("user.home"), path).toAbsolutePath();
                    if (java.nio.file.Files.isDirectory(p)) {
                        currentProject = p.toString();
                        currentProjectName = p.getFileName().toString();
                        System.out.println("  " + GREEN + "Project set: " + currentProject + RESET);
                    } else {
                        System.out.println("  " + RED + "Directory not found: " + path + RESET);
                    }
                }
                System.out.println();
                continue;
            }

            // Check if it's already a valid command
            String[] rawArgs = parseArgs(line);
            if (rawArgs.length == 0) continue;

            Set<String> validCommands = Set.of(
                "debate", "init", "generate", "create", "add", "review",
                "health", "context", "diagram", "watch", "register",
                "login", "usage", "upgrade", "fix", "ask"
            );

            if (validCommands.contains(rawArgs[0].toLowerCase())) {
                // It's already a command, execute directly
                cmdArgs = rawArgs;
            } else {
                // Natural language → detect intent and build command
                cmdArgs = interpretNaturalLanguage(line, lineLower, currentProject);
                if (cmdArgs == null) {
                    // Fallback: send to AI chat
                    handleChat(line, currentProject, chatHistory);
                    System.out.println();
                    continue;
                }
                // Show interpreted command
                System.out.println("  " + DIM + "→ " + String.join(" ", cmdArgs) + RESET);
            }

            try {
                new CommandLine(new CortexCLI()).execute(cmdArgs);

                // Update session project from command args
                for (int i = 0; i < cmdArgs.length; i++) {
                    if (("-p".equals(cmdArgs[i]) || "--project".equals(cmdArgs[i])) && i + 1 < cmdArgs.length) {
                        String candidateStr = cmdArgs[i + 1];
                        if (candidateStr.startsWith("~")) {
                            candidateStr = System.getProperty("user.home") + candidateStr.substring(1);
                        }
                        java.nio.file.Path candidate = java.nio.file.Path.of(candidateStr).toAbsolutePath();
                        if (java.nio.file.Files.isDirectory(candidate)) {
                            currentProject = candidate.toString();
                            currentProjectName = candidate.getFileName().toString();
                        }
                        break;
                    }
                    if ("init".equals(cmdArgs[0]) && i == 1) {
                        String candidateStr = cmdArgs[1];
                        if (candidateStr.startsWith("~")) {
                            candidateStr = System.getProperty("user.home") + candidateStr.substring(1);
                        }
                        java.nio.file.Path candidate = java.nio.file.Path.of(candidateStr).toAbsolutePath();
                        if (java.nio.file.Files.isDirectory(candidate)) {
                            currentProject = candidate.toString();
                            currentProjectName = candidate.getFileName().toString();
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("  " + RED + "Error: " + e.getMessage() + RESET);
            }

            System.out.println();
        }

        scanner.close();
    }

    @Override
    public void run() {
        // When called with no args, enter interactive mode
        interactiveMode();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            // No args: interactive mode
            CortexCLI cli = new CortexCLI();
            cli.interactiveMode();
        } else {
            // Args provided: execute command directly and exit
            int exitCode = new CommandLine(new CortexCLI()).execute(args);
            System.exit(exitCode);
        }
    }
}
