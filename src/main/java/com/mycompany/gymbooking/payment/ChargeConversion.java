package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts JOD prices into the currency the card is actually charged in. Stripe can't charge JOD,
 * so payments are charged in USD at the dinar's fixed peg; both values are configurable so a
 * provider that supports JOD can charge it directly (currency JOD, rate 1).
 */
@Component
public class ChargeConversion {

    public static final String PRICE_CURRENCY = "JOD";

    private final String currency;
    private final BigDecimal rate;

    public ChargeConversion(@Value("${app.payments.charge-currency:JOD}") String currency,
                            @Value("${app.payments.jod-exchange-rate:1}") BigDecimal rate) {
        this.currency = currency.trim().toUpperCase(Locale.ROOT);
        this.rate = rate;
        if (!this.currency.matches("[A-Z]{3}")) {
            throw new IllegalStateException("app.payments.charge-currency must be a 3-letter currency code");
        }
        if (rate.signum() <= 0) {
            throw new IllegalStateException("app.payments.jod-exchange-rate must be positive");
        }
        if (this.currency.equals(PRICE_CURRENCY) && rate.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException("app.payments.jod-exchange-rate must be 1 when charging in JOD");
        }
    }

    public String currency() {
        return currency;
    }

    /** Rounded half-up to the charge currency's smallest unit. */
    public BigDecimal toChargeAmount(BigDecimal jod) {
        return jod.multiply(rate).setScale(CurrencyUnits.decimals(currency), RoundingMode.HALF_UP);
    }
}
