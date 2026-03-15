package com.cortex.cli;

import java.util.Map;

public class Consensus {
    private Map<String, Integer> votes;
    private int total;
    private String level;

    public Map<String, Integer> getVotes() { return votes; }
    public int getTotal() { return total; }
    public String getLevel() { return level; }
}
