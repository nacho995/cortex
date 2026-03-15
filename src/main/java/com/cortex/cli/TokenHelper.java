package com.cortex.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TokenHelper {
    private static final Path TOKEN_PATH = Path.of(
        System.getProperty("user.home"), ".cortex", "token"
    );

    public static String loadToken() {
        try {
            if (Files.exists(TOKEN_PATH)) {
                return Files.readString(TOKEN_PATH).trim();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }
}
