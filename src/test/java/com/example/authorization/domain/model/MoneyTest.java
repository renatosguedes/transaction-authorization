package com.example.authorization.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void should_throw_when_comparing_different_currencies() {
        Money brl = Money.of(new BigDecimal("100.00"), "BRL");
        Money usd = Money.of(new BigDecimal("100.00"), "USD");

        assertThatThrownBy(() -> brl.isGreaterThan(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BRL")
                .hasMessageContaining("USD");
    }
}
