package com.cortex.cli;

public class Agent {
    private String name;
    private String role;
    private String stance;
    private String argument;
    private String vote;

    public Agent(String name, String role, String stance, String argument) {
        this.name = name;
        this.role = role;
        this.stance = stance;
        this.argument = argument;
    }

    public String getName() { return name; }
    public String getRole() { return role; }
    public String getStance() { return stance; }
    public String getArgument() { return argument; }
    public String getVote() { return vote; }
}
