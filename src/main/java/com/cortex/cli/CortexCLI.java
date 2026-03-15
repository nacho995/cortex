package com.cortex.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "cortex", version = "0.1.0",
    description = "AI Architecture Decision Engine",
    subcommands = {
        DebateCommand.class,
        InitCommand.class,
        GenerateCommand.class,
        ReviewCommand.class,
        HealthCommand.class,
        ContextCommand.class
    },
    mixinStandardHelpOptions = true
)
public class CortexCLI implements Runnable {

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
        sb.append("\u001B[0m");
        return sb.toString();
    }

    @Override
    public void run() {
        System.out.println();
        System.out.println(gradient("   ██████╗ ██████╗ ██████╗ ████████╗███████╗██╗  ██╗"));
        System.out.println(gradient("  ██╔════╝██╔═══██╗██╔══██╗╚══██╔══╝██╔════╝╚██╗██╔╝"));
        System.out.println(gradient("  ██║     ██║   ██║██████╔╝   ██║   █████╗   ╚███╔╝ "));
        System.out.println(gradient("  ██║     ██║   ██║██╔══██╗   ██║   ██╔══╝   ██╔██╗ "));
        System.out.println(gradient("  ╚██████╗╚██████╔╝██║  ██║   ██║   ███████╗██╔╝ ██╗"));
        System.out.println(gradient("   ╚═════╝ ╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚══════╝╚═╝  ╚═╝"));
        System.out.println(gradient("        AI Architecture Decision Engine v0.1.0"));
        System.out.println();
        System.out.println("  \u001B[2mCommands:\u001B[0m");
        System.out.println("    \u001B[36minit\u001B[0m       Scan a project and generate context");
        System.out.println("    \u001B[36mdebate\u001B[0m     Multi-agent architecture debate");
        System.out.println("    \u001B[36mgenerate\u001B[0m   Generate code from an ADR");
        System.out.println("    \u001B[36mreview\u001B[0m     Code review with agent personas");
        System.out.println("    \u001B[36mhealth\u001B[0m     Project health score analysis");
        System.out.println("    \u001B[36mcontext\u001B[0m    Show current project context");
        System.out.println();
        System.out.println("  \u001B[2mUse 'cortex <command> --help' for more info\u001B[0m");
        System.out.println();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CortexCLI()).execute(args);
        System.exit(exitCode);
    }
}
