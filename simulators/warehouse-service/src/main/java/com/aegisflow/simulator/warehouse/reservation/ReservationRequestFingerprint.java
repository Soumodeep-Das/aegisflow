package com.aegisflow.simulator.warehouse.reservation;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
final class ReservationRequestFingerprint {

    private static final String OPERATION = "CREATE_RESERVATION";

    private final ObjectMapper objectMapper;
    private final CanonicalJson canonicalJson;

    ReservationRequestFingerprint(ObjectMapper objectMapper, CanonicalJson canonicalJson) {
        this.objectMapper = objectMapper;
        this.canonicalJson = canonicalJson;
    }

    String hash(CreateReservationCommand command) {
        return canonicalJson.sha256(material(command));
    }

    String canonicalMaterial(CreateReservationCommand command) {
        return canonicalJson.canonicalString(material(command));
    }

    private tools.jackson.databind.node.ObjectNode material(CreateReservationCommand command) {
        var material = objectMapper.createObjectNode();
        material.put("businessTransactionId", command.businessTransactionId());
        var lineItems = material.putArray("lineItems");
        for (var line : command.lines()) {
            var item = lineItems.addObject();
            item.put("quantity", line.quantity());
            item.put("sku", line.sku());
        }
        material.put("operation", OPERATION);
        material.put("orderId", command.orderId());
        if (command.rejectionReason() == null) {
            material.putNull("rejectionReason");
        } else {
            material.put("rejectionReason", command.rejectionReason().name());
        }
        material.put("reservationId", command.reservationId().toString());
        material.put("status", command.status().name());
        material.put("tenantId", command.tenantId());
        return material;
    }
}
