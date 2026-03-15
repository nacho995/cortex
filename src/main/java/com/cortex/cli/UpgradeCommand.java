package com.cortex.cli;

import picocli.CommandLine.Command;

@Command(name = "upgrade", description = "Upgrade your Cortex plan")
public class UpgradeCommand implements Runnable {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[38;2;0;200;255m";
    private static final String GREEN = "\u001B[38;2;0;230;120m";
    private static final String YELLOW = "\u001B[38;2;255;200;0m";
    private static final String DIM = "\u001B[2m";

    @Override
    public void run() {
        System.out.println();
        System.out.println(BOLD + CYAN + "  CORTEX PLANS" + RESET);
        System.out.println();
        System.out.println("  " + DIM + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println("  " + YELLOW + BOLD + "  FREE" + RESET + "                              $0/mo");
        System.out.println("    10 calls/day | Llama 3.1 8B (Groq)");
        System.out.println("  " + DIM + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println("  " + CYAN + BOLD + "  PRO" + RESET + "                               $9/mo");
        System.out.println("    200 calls/day | GPT-4o-mini (OpenAI)");
        System.out.println("    " + GREEN + "Subscribe: " + RESET + "https://buy.stripe.com/5kQ6oI5uEcHW4hZerC0VO00");
        System.out.println("  " + DIM + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println("  " + GREEN + BOLD + "  ENTERPRISE" + RESET + "                         $29/mo");
        System.out.println("    Unlimited calls | GPT-4o (OpenAI)");
        System.out.println("    " + GREEN + "Subscribe: " + RESET + "https://buy.stripe.com/7sYeVee1a23i3dVerC0VO01");
        System.out.println("  " + DIM + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        System.out.println("  " + DIM + "After payment, your plan upgrades automatically." + RESET);
        System.out.println();
    }
}
