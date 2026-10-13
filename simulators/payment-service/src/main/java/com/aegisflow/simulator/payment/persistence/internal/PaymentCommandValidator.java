package com.aegisflow.simulator.payment.persistence.internal;

import com.aegisflow.simulator.payment.CreatePaymentCommand;
import com.aegisflow.simulator.payment.PaymentStatus;
import com.aegisflow.simulator.payment.PaymentValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
final class PaymentCommandValidator {

    private static final int MONEY_PRECISION = 19;
    private static final int MONEY_SCALE = 4;

    NormalizedPaymentCommand validate(CreatePaymentCommand command) {
        if (command == null) {
            throw new PaymentValidationException("command is required");
        }

        String tenantId = requiredText("tenantId", command.tenantId(), 128);
        String idempotencyKey = requiredText("idempotencyKey", command.idempotencyKey(), 128);
        if (command.paymentId() == null) {
            throw new PaymentValidationException("paymentId is required");
        }
        String businessTransactionId = requiredText(
                "businessTransactionId", command.businessTransactionId(), 128);
        String orderId = requiredText("orderId", command.orderId(), 128);
        String correlationId = requiredText("correlationId", command.correlationId(), 128);
        if (command.status() == null) {
            throw new PaymentValidationException("status is required");
        }

        String currencyCode = requiredText("currency", command.currency(), 3);
        if (currencyCode.length() != 3 || !currencyCode.equals(currencyCode.toUpperCase())) {
            throw new PaymentValidationException("currency must be an uppercase ISO 4217 code");
        }

        Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new PaymentValidationException("currency must be an ISO 4217 code");
        }
        int currencyScale = currency.getDefaultFractionDigits();
        if (currencyScale < 0 || currencyScale > MONEY_SCALE) {
            throw new PaymentValidationException("currency fraction digits must fit numeric(19,4)");
        }

        BigDecimal amount = command.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentValidationException("amount must be positive");
        }
        BigDecimal normalized;
        try {
            normalized = amount.setScale(currencyScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new PaymentValidationException("amount scale exceeds the currency fraction digits");
        }
        BigDecimal stored = normalized.setScale(MONEY_SCALE);
        if (stored.precision() > MONEY_PRECISION) {
            throw new PaymentValidationException("amount exceeds numeric(19,4)");
        }

        if (command.status() == PaymentStatus.DECLINED && command.declineReason() == null) {
            throw new PaymentValidationException("declineReason is required for DECLINED");
        }
        if (command.status() == PaymentStatus.AUTHORIZED && command.declineReason() != null) {
            throw new PaymentValidationException("declineReason must be absent for AUTHORIZED");
        }

        return new NormalizedPaymentCommand(
                tenantId,
                idempotencyKey,
                command.paymentId(),
                businessTransactionId,
                orderId,
                command.status(),
                stored,
                normalized.toPlainString(),
                currencyCode,
                command.declineReason(),
                correlationId);
    }

    private static String requiredText(String field, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new PaymentValidationException(field + " is required");
        }
        if (!value.equals(value.trim())) {
            throw new PaymentValidationException(field + " must not have surrounding whitespace");
        }
        if (value.length() > maximumLength) {
            throw new PaymentValidationException(field + " exceeds " + maximumLength + " characters");
        }
        return value;
    }
}
