package com.aegisflow.core.eventing.internal;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class EventValidatorTests {
    private final EventValidator validator = new EventValidator(new IntakePolicy(Map.of(
            "order-service", Set.of("tenant-a"), "payment-service", Set.of("tenant-b"),
            "warehouse-service", Set.of("tenant-a"))));
    static Map<String, Object> order() {
        var event = new LinkedHashMap<String, Object>();
        event.put("tenantId", "tenant-a"); event.put("sourceSystem", "order-service");
        event.put("eventId", "c2fa0ffd-e560-4540-97fd-18ff63b12345"); event.put("eventType", "OrderCreated");
        event.put("businessTransactionId", "txn-1"); event.put("correlationId", "corr-1");
        event.put("schemaVersion", "1.0"); event.put("occurredAt", "2026-10-20T00:00:00.000Z");
        event.put("details", new LinkedHashMap<>(Map.of("orderId", "order-1", "orderVersion", 1,
                "amount", "12.0", "currency", "INR", "lineItems", List.of(Map.of("sku", "sku-1", "quantity", 2)))));
        return event;
    }
    static ConsumerRecord<byte[], byte[]> record(Map<String, Object> event) {
        return raw(new JsonMapper().writeValueAsString(event));
    }
    static ConsumerRecord<byte[], byte[]> raw(String json) {
        return new ConsumerRecord<>("aegisflow.order.events.v1", 0, 4,
                "tenant-a:txn-1".getBytes(StandardCharsets.UTF_8), json.getBytes(StandardCharsets.UTF_8));
    }
    @Test void formattingDecimalAndTimestampNormalizationPreserveCanonicalIdentity() {
        var event = order();
        var first = validator.validate(record(event));
        ((Map<String,Object>)event.get("details")).put("amount", "12.00");
        event.put("occurredAt", "2026-10-20T00:00:00Z");
        var second = validator.validate(raw(" \n" + new JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(event)));
        assertThat(second.hash()).isEqualTo(first.hash());
        assertThat(first.envelope()).contains("\"amount\":\"12.00\"");
    }
    @ParameterizedTest @ValueSource(strings = {"tenantId","sourceSystem","eventId","eventType","businessTransactionId",
            "correlationId","schemaVersion","occurredAt","details"})
    void everyRequiredEnvelopeFieldIsRequired(String field) {
        var event = order(); event.remove(field);
        assertThatThrownBy(() -> validator.validate(record(event))).isInstanceOf(EventValidator.Rejected.class);
    }
    @Test void topicAndSourceTenantBindingOverridePayloadClaims() {
        var event = order(); event.put("tenantId", "tenant-b");
        assertThatThrownBy(() -> validator.validate(record(event))).hasMessage("TENANT_NOT_TRUSTED");
        var spoof = order(); spoof.put("sourceSystem", "payment-service");
        assertThatThrownBy(() -> validator.validate(record(spoof))).hasMessage("SOURCE_NOT_TRUSTED");
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "[]", "null", "{} {}", "{", "{\"tenantId\":\"a\",\"tenantId\":\"b\"}"})
    void rejectsMalformedOrIncompleteJson(String json) {
        assertThatThrownBy(() -> validator.validate(raw(json))).isInstanceOf(EventValidator.Rejected.class);
    }
    @Test void rejectsInvalidUtf8OversizeTombstoneAndExcessiveNesting() {
        for (byte[] value : new byte[][]{null, new byte[EventValidator.MAX_BYTES + 1], new byte[]{(byte)0xc3, 0x28}}) {
            assertThatThrownBy(() -> validator.validate(new ConsumerRecord<>("aegisflow.order.events.v1",0,0,new byte[0],value)))
                    .isInstanceOf(EventValidator.Rejected.class);
        }
        assertThatThrownBy(() -> validator.validate(raw("[".repeat(25)+"0"+"]".repeat(25))))
                .hasMessage("MALFORMED_JSON");
    }
    @Test void compatibleMinorExtensionsAreRetainedButMajorAndUnknownEventAreQuarantined() {
        var event = order(); event.put("schemaVersion", "1.1"); event.put("optional", Map.of("note", "untrusted data"));
        assertThat(validator.validate(record(event)).envelope()).contains("untrusted data");
        event.put("schemaVersion", "2.0");
        assertThatThrownBy(() -> validator.validate(record(event))).hasMessage("SCHEMA_UNSUPPORTED");
        event.put("schemaVersion", "1.0"); event.put("eventType", "OrderConfirmed");
        assertThatThrownBy(() -> validator.validate(record(event))).hasMessage("EVENT_UNSUPPORTED");
    }
    @Test void rejectsNonIntegralVersionBadCurrencyAndKeyMismatch() {
        var event = order(); var details = (Map<String,Object>)event.get("details");
        details.put("orderVersion", 1.5);
        assertThatThrownBy(() -> validator.validate(record(event))).hasMessage("SCHEMA_INVALID");
        details.put("orderVersion", 1); details.put("currency", "BAD");
        assertThatThrownBy(() -> validator.validate(record(event))).hasMessage("SCHEMA_INVALID");
        var changed = order(); changed.put("businessTransactionId", "other");
        assertThatThrownBy(() -> validator.validate(record(changed))).hasMessage("KEY_MISMATCH");
    }
    @Test void validatesBothExistingNegativeSchemas() {
        var event = order();
        event.put("sourceSystem", "payment-service"); event.put("tenantId", "tenant-b"); event.put("eventType","PaymentDeclined");
        event.put("details", Map.of("paymentId","payment-1","orderId","order-1","paymentVersion",1,
                "amount","12.00","currency","INR","reasonCode","SIMULATED_DECLINE"));
        assertThat(validator.validate(new ConsumerRecord<>("aegisflow.payment.events.v1",0,0,
                "tenant-b:txn-1".getBytes(StandardCharsets.UTF_8),new JsonMapper().writeValueAsBytes(event))).type()).isEqualTo("PaymentDeclined");
        event.put("sourceSystem","warehouse-service"); event.put("tenantId","tenant-a"); event.put("eventType","InventoryRejected");
        event.put("details",Map.of("reservationId","r-1","orderId","order-1","reservationVersion",1,"reasonCode","OUT_OF_STOCK"));
        assertThat(validator.validate(new ConsumerRecord<>("aegisflow.warehouse.events.v1",0,0,
                "tenant-a:txn-1".getBytes(StandardCharsets.UTF_8),new JsonMapper().writeValueAsBytes(event))).type()).isEqualTo("InventoryRejected");
    }

    @Test void optionalNumericChangesCannotCollapseThroughFloatingPointRounding() {
        String event = new JsonMapper().writeValueAsString(order());
        String a = event.substring(0,event.length()-1) + ",\"optionalNumber\":9007199254740992.1}";
        String b = event.substring(0,event.length()-1) + ",\"optionalNumber\":9007199254740992.2}";
        assertThat(validator.validate(raw(a)).hash()).isNotEqualTo(validator.validate(raw(b)).hash());
    }

    @Test void postgresUnsafeStringsAndExtremeOptionalNumbersArePoisonBeforeDatabaseInsertion() {
        String event = new JsonMapper().writeValueAsString(order());
        for (String extra : new String[]{"\"optional\":\"\\u0000\"", "\"\\u0000\":\"value\"",
                "\"optional\":\"\\uD800\"", "\"optional\":1e999999", "\"optional\":1e-999999"}) {
            String poison = event.substring(0,event.length()-1) + "," + extra + "}";
            assertThatThrownBy(() -> validator.validate(raw(poison))).isInstanceOf(EventValidator.Rejected.class);
        }
    }
}
