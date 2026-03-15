package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Map;

@Command(name = "context", description = "Show current project context")
public class ContextCommand implements Runnable {
    @Option(names = {"-p", "--project"}, description = "Path to project root", required = true)
    private String project;

    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";

    @Override
    public void run() {
        try {
            Path contextPath = Path.of(project, ".architect", "context.json");
            if (!Files.exists(contextPath)) {
                System.out.println("\u001B[91m  Error: No context found. Run 'cortex init " + project + "' first." + RESET);
                return;
            }

            String content = Files.readString(contextPath);
            Gson gson = new Gson();
            JsonObject ctx = gson.fromJson(content, JsonObject.class);

            System.out.println();
            System.out.println(BOLD + CYAN + "  PROJECT CONTEXT" + RESET);
            System.out.println(CYAN + "  " + "━".repeat(50) + RESET);

            printField("Project", ctx, "project_name");
            printArray("Languages", ctx, "languages");
            printArray("Frameworks", ctx, "frameworks");
            printArray("Build Tools", ctx, "build_tools");
            printArray("Infrastructure", ctx, "infrastructure");
            printArray("Entry Points", ctx, "entry_points");

            // Dependencies
            if (ctx.has("dependencies")) {
                JsonObject deps = ctx.getAsJsonObject("dependencies");
                for (Map.Entry<String, JsonElement> entry : deps.entrySet()) {
                    JsonArray arr = entry.getValue().getAsJsonArray();
                    System.out.printf("  %s%-18s%s ", BOLD, "Deps (" + entry.getKey() + ")", RESET);
                    for (int i = 0; i < Math.min(arr.size(), 8); i++) {
                        if (i > 0) System.out.print(", ");
                        System.out.print(DIM + arr.get(i).getAsString() + RESET);
                    }
                    if (arr.size() > 8) System.out.print(DIM + " (+" + (arr.size() - 8) + " more)" + RESET);
                    System.out.println();
                }
            }

            // Structure preview
            if (ctx.has("structure")) {
                System.out.println();
                System.out.println("  " + BOLD + "Structure:" + RESET);
                JsonArray structure = ctx.getAsJsonArray("structure");
                for (int i = 0; i < Math.min(structure.size(), 20); i++) {
                    String line = structure.get(i).getAsString();
                    if (line.endsWith("/")) {
                        System.out.println("  " + CYAN + line + RESET);
                    } else {
                        System.out.println("  " + DIM + line + RESET);
                    }
                }
                if (structure.size() > 20) {
                    System.out.println("  " + DIM + "... (" + (structure.size() - 20) + " more entries)" + RESET);
                }
            }

            System.out.println();

        } catch (IOException e) {
            System.out.println("\u001B[91m  Error reading context: " + e.getMessage() + RESET);
        }
    }

    private void printField(String label, JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            System.out.printf("  %s%-18s%s %s%n", BOLD, label, RESET, obj.get(key).getAsString());
        }
    }

    private void printArray(String label, JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray(key);
            if (arr.size() > 0) {
                System.out.printf("  %s%-18s%s ", BOLD, label, RESET);
                for (int i = 0; i < arr.size(); i++) {
                    if (i > 0) System.out.print(", ");
                    System.out.print(arr.get(i).getAsString());
                }
                System.out.println();
            }
        }
    }
}
