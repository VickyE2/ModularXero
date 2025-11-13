package com.vicky.modularxero.common.values;

import java.math.BigDecimal;

/**
 * Wraps a monetary amount plus its currency symbol.
 * Under the hood uses BigDecimal for precision.
 */
public class CurrencyValue extends MessageValue<BigDecimal> {
    private final String symbol;

    public CurrencyValue(BigDecimal amount, String symbol) {
        super(amount);
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Currency symbol must be provided");
        }
        this.symbol = symbol;
    }

    /** e.g. "$", "€", "₦" */
    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return symbol + super.get().toPlainString();
    }
}
