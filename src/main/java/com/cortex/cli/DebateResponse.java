package com.cortex.cli;

import java.util.List;

public class DebateResponse {
    private String topic;
    private List<RoundResult> rounds;
    private Consensus consensus;

    public String getTopic() { return topic; }
    public List<RoundResult> getRounds() { return rounds; }
    public Consensus getConsensus() { return consensus; }
}
