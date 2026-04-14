package com.example.authorization.domain.model;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) throw new IllegalArgumentException("amount não pode ser nulo");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency não pode ser vazio");
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isGreaterThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Moedas diferentes: " + this.currency + " vs " + other.currency);
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
