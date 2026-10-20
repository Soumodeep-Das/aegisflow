-- Intake acceptance only: business facts, correlation, deadlines and incidents remain P0.4.
CREATE TABLE aegisflow.inbox_message (
    tenant_id varchar(128) NOT NULL CHECK (length(btrim(tenant_id)) > 0),
    source_system varchar(32) NOT NULL CHECK (source_system IN ('order-service','payment-service','warehouse-service')),
    event_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    business_transaction_id varchar(128) NOT NULL CHECK (length(btrim(business_transaction_id)) > 0),
    schema_version varchar(16) NOT NULL CHECK (schema_version ~ '^1\.[0-9]{1,4}$'),
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    correlation_id varchar(128) NOT NULL CHECK (length(btrim(correlation_id)) > 0),
    channel_identity varchar(128) NOT NULL CHECK (length(btrim(channel_identity)) > 0),
    topic varchar(249) NOT NULL,
    partition_id integer NOT NULL CHECK (partition_id >= 0),
    offset_id bigint NOT NULL CHECK (offset_id >= 0),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    payload_hash char(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    payload_hash_version varchar(32) NOT NULL CHECK (payload_hash_version = 'aegisflow-inbox-json-v1'),
    raw_payload_hash char(64) NOT NULL CHECK (raw_payload_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) NOT NULL CHECK (status = 'ACCEPTED'),
    PRIMARY KEY (tenant_id,source_system,event_id),
    CHECK ((source_system='order-service' AND topic='aegisflow.order.events.v1' AND event_type='OrderCreated')
        OR (source_system='payment-service' AND topic='aegisflow.payment.events.v1' AND event_type IN ('PaymentAuthorized','PaymentDeclined'))
        OR (source_system='warehouse-service' AND topic='aegisflow.warehouse.events.v1' AND event_type IN ('InventoryReserved','InventoryRejected'))),
    CHECK (payload ?& ARRAY['tenantId','sourceSystem','eventId','eventType','businessTransactionId','schemaVersion','occurredAt','correlationId','details']),
    CHECK ((payload->>'tenantId'=tenant_id AND payload->>'sourceSystem'=source_system
        AND payload->>'eventId'=event_id::text AND payload->>'eventType'=event_type
        AND payload->>'businessTransactionId'=business_transaction_id AND payload->>'schemaVersion'=schema_version
        AND payload->>'correlationId'=correlation_id AND jsonb_typeof(payload->'details')='object') IS TRUE)
);
CREATE INDEX inbox_transaction_lookup ON aegisflow.inbox_message(tenant_id,business_transaction_id,occurred_at);
CREATE TABLE aegisflow.quarantine_record (
    quarantine_id uuid PRIMARY KEY,
    channel_identity varchar(128) NOT NULL,
    topic varchar(249) NOT NULL,
    partition_id integer NOT NULL CHECK (partition_id >= 0),
    offset_id bigint NOT NULL CHECK (offset_id >= 0),
    raw_payload_hash char(64) NOT NULL CHECK (raw_payload_hash ~ '^[0-9a-f]{64}$'),
    payload_bytes integer NOT NULL CHECK (payload_bytes >= -1),
    reason_code varchar(32) NOT NULL CHECK (reason_code IN
      ('SIZE_OR_TOMBSTONE','MALFORMED_JSON','SCHEMA_INVALID','SOURCE_NOT_TRUSTED','TENANT_NOT_TRUSTED',
       'SCHEMA_UNSUPPORTED','EVENT_UNSUPPORTED','IDENTITY_OR_TIME_INVALID','KEY_MISMATCH','EVENT_ID_CONFLICT')),
    received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    review_status varchar(16) NOT NULL DEFAULT 'UNREVIEWED' CHECK (review_status = 'UNREVIEWED'),
    UNIQUE(channel_identity,topic,partition_id,offset_id,raw_payload_hash)
);
COMMENT ON TABLE aegisflow.quarantine_record IS 'Technical intake evidence; no tenant claim confers authority; no automatic replay';
-- Append-only for the runtime role, including accepted payloads and technical evidence.
GRANT SELECT, INSERT ON aegisflow.inbox_message, aegisflow.quarantine_record TO "${runtimeRole}";
