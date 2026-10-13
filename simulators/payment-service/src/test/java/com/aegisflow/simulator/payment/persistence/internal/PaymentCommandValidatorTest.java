package com.aegisflow.simulator.payment.persistence.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisflow.simulator.payment.CreatePaymentCommand;
import com.aegisflow.simulator.payment.PaymentDeclineReason;
import com.aegisflow.simulator.payment.PaymentStatus;
import com.aegisflow.simulator.payment.PaymentValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCommandValidatorTest {

    private final PaymentCommandValidator validator = new PaymentCommandValidator();

    @Test
    void normalizesAmountToIsoCurrencyFractionDigitsAndDatabaseScale() {
        NormalizedPaymentCommand normalized = validator.validate(command(
                PaymentStatus.AUTHORIZED, new BigDecimal("1499"), "INR", null, "corr-one"));

        assertEquals("1499.00", normalized.eventAmount());
        assertEquals(new BigDecimal("1499.0000"), normalized.amount());
    }

    @Test
    void rejectsFractionalPrecisionBeyondTheCurrencyContract() {
        CreatePaymentCommand invalid = command(
                PaymentStatus.AUTHORIZED, new BigDecimal("1.001"), "INR", null, "corr-one");

        assertThrows(PaymentValidationException.class, () -> validator.validate(invalid));
    }

    @Test
    void rejectsMissingTenantAndLowercaseCurrency() {
        CreatePaymentCommand missingTenant = new CreatePaymentCommand(
                " ",
                "request-1",
                UUID.randomUUID(),
                "txn-1",
                "order-1",
                PaymentStatus.AUTHORIZED,
                new BigDecimal("10.00"),
                "INR",
                null,
                "corr-one");
        assertThrows(PaymentValidationException.class, () -> validator.validate(missingTenant));

        CreatePaymentCommand lowercaseCurrency = command(
                PaymentStatus.AUTHORIZED, new BigDecimal("10.00"), "inr", null, "corr-one");
        assertThrows(PaymentValidationException.class, () -> validator.validate(lowercaseCurrency));
    }

    @Test
    void acceptsTenantIdsUpToOneHundredTwentyEightCharacters() {
        String maximumTenant = "t".repeat(128);
        CreatePaymentCommand accepted = new CreatePaymentCommand(
                maximumTenant,
                "request-1",
                UUID.randomUUID(),
                "txn-1",
                "order-1",
                PaymentStatus.AUTHORIZED,
                new BigDecimal("10.00"),
                "INR",
                null,
                "corr-one");
        assertEquals(maximumTenant, validator.validate(accepted).tenantId());

        CreatePaymentCommand tooLong = new CreatePaymentCommand(
                "t".repeat(129),
                "request-1",
                UUID.randomUUID(),
                "txn-1",
                "order-1",
                PaymentStatus.AUTHORIZED,
                new BigDecimal("10.00"),
                "INR",
                null,
                "corr-one");
        assertThrows(PaymentValidationException.class, () -> validator.validate(tooLong));
    }

    @Test
    void requiresAReasonOnlyForDeclines() {
        assertThrows(
                PaymentValidationException.class,
                () -> validator.validate(command(
                        PaymentStatus.DECLINED, new BigDecimal("10.00"), "INR", null, "corr-one")));

        assertThrows(
                PaymentValidationException.class,
                () -> validator.validate(command(
                        PaymentStatus.AUTHORIZED,
                        new BigDecimal("10.00"),
                        "INR",
                        PaymentDeclineReason.SIMULATED_DECLINE,
                        "corr-one")));
    }

    @Test
    void rejectsValuesOutsideNumericNineteenFour() {
        CreatePaymentCommand tooLarge = command(
                PaymentStatus.AUTHORIZED,
                new BigDecimal("1000000000000000.00"),
                "INR",
                null,
                "corr-one");

        assertThrows(PaymentValidationException.class, () -> validator.validate(tooLarge));
    }

    private static CreatePaymentCommand command(
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            PaymentDeclineReason declineReason,
            String correlationId) {
        return new CreatePaymentCommand(
                "tenant-one",
                "request-1",
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "txn-1",
                "order-1",
                status,
                amount,
                currency,
                declineReason,
                correlationId);
    }
}
