package com.cortex.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import java.io.Console;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private void interactiveMode() {
        printBanner();
        printWelcome();

        Scanner scanner = new Scanner(System.in);
        String prompt = "  " + BOLD + CYAN + "cortex" + RESET + DIM + " > " + RESET;

        while (true) {
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

            // Parse and execute the command
            String[] cmdArgs = parseArgs(line);
            if (cmdArgs.length == 0) continue;

            // Only process valid commands, ignore everything else
            String firstWord = cmdArgs[0].toLowerCase();
            Set<String> validCommands = Set.of(
                "debate", "init", "generate", "create", "add", "review",
                "health", "context", "diagram", "watch", "register",
                "login", "usage", "upgrade", "fix", "ask"
            );

            if (!validCommands.contains(firstWord)) {
                // Silently ignore non-command input (pasted errors, etc)
                continue;
            }

            try {
                new CommandLine(new CortexCLI()).execute(cmdArgs);
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
