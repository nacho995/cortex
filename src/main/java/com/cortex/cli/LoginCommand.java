package com.cortex.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "login", description = "Save your API token")
public class LoginCommand implements Runnable {
    @Parameters(index = "0", description = "Your API token (ctx_...)")
    private String token;

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String DIM = "\u001B[2m";

    @Override
    public void run() {
        try {
            Path tokenFile = Path.of(System.getProperty("user.home"), ".cortex", "token");
            Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, token.trim());

            System.out.println();
            System.out.println(BOLD + GREEN + "  Token saved!" + RESET);
            System.out.println("  " + DIM + "Saved to ~/.cortex/token" + RESET);
            System.out.println("  " + DIM + "All commands will use this token automatically." + RESET);
            System.out.println();
        } catch (IOException e) {
            System.out.println("\u001B[91m  Error saving token: " + e.getMessage() + RESET);
        }
    }
}
