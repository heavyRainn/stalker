package com.trading.crypto.model;

public enum RiskEvaluation {
    ACCEPTABLE("Acceptable Risk"),
    MODERATE("Moderate Risk"),
    HIGH("High Risk"),
    TOO_HIGH("Too High Risk");

    private final String description;

    RiskEvaluation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
