package com.cortex.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates files after writing. Detects syntax errors before the user sees them.
 */
public class LintHelper {

    /**
     * Validate a file after writing. Returns error message or null if valid.
     */
    public static String validate(Path filePath) {
        String name = filePath.getFileName().toString();
        
        try {
            String content = Files.readString(filePath);
            
            // JSON validation
            if (name.endsWith(".json")) {
                return validateJson(content, filePath);
            }
            
            // CSS validation
            if (name.endsWith(".css")) {
                return validateCss(content, filePath);
            }
            
            // JS/JSX validation
            if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx")) {
                return validateJs(content, filePath);
            }
            
            // Java validation
            if (name.endsWith(".java")) {
                return validateJava(content, filePath);
            }
            
            // Python validation
            if (name.endsWith(".py")) {
                return validatePython(filePath);
            }
            
            // HTML validation
            if (name.endsWith(".html")) {
                return validateHtml(content, filePath);
            }
            
            return null; // Unknown type, skip
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String validateJson(String content, Path path) {
        content = content.trim();
        if (!content.startsWith("{") && !content.startsWith("[")) {
            return "JSON doesn't start with { or [";
        }
        
        // Count braces
        int braces = 0, brackets = 0;
        boolean inString = false;
        char prev = 0;
        for (char c : content.toCharArray()) {
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') braces++;
                else if (c == '}') braces--;
                else if (c == '[') brackets++;
                else if (c == ']') brackets--;
            }
            prev = c;
        }
        if (braces != 0) return "JSON has unmatched braces (balance: " + braces + ")";
        if (brackets != 0) return "JSON has unmatched brackets (balance: " + brackets + ")";
        
        // Check for trailing content after closing brace
        int lastBrace = content.lastIndexOf('}');
        int lastBracket = content.lastIndexOf(']');
        int lastClose = Math.max(lastBrace, lastBracket);
        if (lastClose > 0 && lastClose < content.length() - 1) {
            String trailing = content.substring(lastClose + 1).trim();
            if (!trailing.isEmpty()) {
                return "JSON has trailing content after closing: " + trailing.substring(0, Math.min(trailing.length(), 50));
            }
        }
        
        return null;
    }
    
    private static String validateCss(String content, Path path) {
        // Count braces
        int braces = 0;
        int lineNum = 1;
        int errorLine = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') lineNum++;
            if (c == '{') braces++;
            else if (c == '}') {
                braces--;
                if (braces < 0) {
                    errorLine = lineNum;
                    break;
                }
            }
        }
        if (braces > 0) return "CSS has " + braces + " unclosed block(s) - missing }";
        if (braces < 0) return "CSS has extra } at line " + errorLine;
        
        // Check for truncated properties (line ending without ; or { or } or */ or empty)
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("/*") || line.startsWith("*") || line.startsWith("//") || line.startsWith("@")) continue;
            if (line.endsWith("{") || line.endsWith("}") || line.endsWith(";") || line.endsWith("*/") || line.endsWith(",")) continue;
            // Check if it's a property without semicolon
            if (line.contains(":") && !line.endsWith("{") && !line.contains("http") && i < lines.length - 1) {
                String nextLine = (i + 1 < lines.length) ? lines[i + 1].trim() : "";
                if (!nextLine.isEmpty() && !nextLine.startsWith("/*") && !nextLine.equals("}")) {
                    return "CSS property missing semicolon at line " + (i + 1) + ": " + line;
                }
            }
        }
        
        return null;
    }
    
    private static String validateJs(String content, Path path) {
        // Count braces, brackets, parens
        int braces = 0, brackets = 0, parens = 0;
        boolean inString = false;
        boolean inTemplate = false;
        boolean inComment = false;
        boolean inLineComment = false;
        char prev = 0;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inComment) {
                if (prev == '*' && c == '/') inComment = false;
                prev = c;
                continue;
            }
            if (c == '/' && i + 1 < content.length()) {
                if (content.charAt(i + 1) == '/') { inLineComment = true; continue; }
                if (content.charAt(i + 1) == '*') { inComment = true; continue; }
            }
            
            if (c == '`') inTemplate = !inTemplate;
            if ((c == '"' || c == '\'') && prev != '\\' && !inTemplate) inString = !inString;
            
            if (!inString && !inTemplate) {
                if (c == '{') braces++;
                else if (c == '}') braces--;
                else if (c == '[') brackets++;
                else if (c == ']') brackets--;
                else if (c == '(') parens++;
                else if (c == ')') parens--;
            }
            prev = c;
        }
        
        if (braces != 0) return "JS has unmatched braces { } (balance: " + braces + ")";
        if (brackets != 0) return "JS has unmatched brackets [ ] (balance: " + brackets + ")";
        if (parens != 0) return "JS has unmatched parentheses ( ) (balance: " + parens + ")";
        
        // Check for common JSX issues
        if (path.toString().endsWith(".jsx") || path.toString().endsWith(".tsx")) {
            if (!content.contains("export") && !content.contains("module.exports")) {
                return "JSX file has no export statement";
            }
        }
        
        return null;
    }
    
    private static String validateJava(String content, Path path) {
        int braces = 0;
        boolean inString = false;
        boolean inComment = false;
        boolean inLineComment = false;
        char prev = 0;
        
        for (char c : content.toCharArray()) {
            if (inLineComment) { if (c == '\n') inLineComment = false; continue; }
            if (inComment) { if (prev == '*' && c == '/') inComment = false; prev = c; continue; }
            if (c == '/' && prev == '/') { inLineComment = true; continue; }
            if (c == '*' && prev == '/') { inComment = true; continue; }
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') braces++;
                else if (c == '}') braces--;
            }
            prev = c;
        }
        
        if (braces != 0) return "Java has unmatched braces (balance: " + braces + ")";
        if (!content.contains("class ") && !content.contains("interface ") && !content.contains("enum ")) {
            return "Java file has no class/interface/enum declaration";
        }
        
        return null;
    }
    
    private static String validatePython(Path path) {
        try {
            Process p = new ProcessBuilder("python3", "-m", "py_compile", path.toString())
                .redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            p.waitFor();
            if (p.exitValue() != 0) return output.toString().trim();
        } catch (Exception e) { /* python not available, skip */ }
        return null;
    }
    
    private static String validateHtml(String content, Path path) {
        // Simple check for unclosed tags
        if (content.contains("<html") && !content.contains("</html>")) return "HTML missing </html>";
        if (content.contains("<body") && !content.contains("</body>")) return "HTML missing </body>";
        if (content.contains("<head") && !content.contains("</head>")) return "HTML missing </head>";
        return null;
    }
}
