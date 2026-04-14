package com.example.authorization.domain.model;

public enum DenialReason {
    INVALID_AMOUNT("Valor inválido ou fora dos limites permitidos"),
    MISSING_FIELDS("Campos obrigatórios ausentes"),
    RATE_LIMIT_EXCEEDED("Limite de transações por período excedido");

    private final String description;

    DenialReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
