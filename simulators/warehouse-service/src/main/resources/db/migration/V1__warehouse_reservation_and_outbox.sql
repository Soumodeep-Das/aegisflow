-- The aegisflow schema and migration/runtime roles are provisioned before Flyway runs.
-- Flyway executes this migration as the warehouse migration role.

CREATE TABLE aegisflow.reservations (
    tenant_id varchar(128) NOT NULL,
    reservation_id uuid NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    order_id varchar(128) NOT NULL,
    status varchar(16) NOT NULL,
    rejection_reason varchar(32),
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_reservations PRIMARY KEY (tenant_id, reservation_id),
    CONSTRAINT uq_reservations_business_transaction
        UNIQUE (tenant_id, business_transaction_id),
    CONSTRAINT uq_reservations_order UNIQUE (tenant_id, order_id),
    CONSTRAINT ck_reservations_tenant_nonblank CHECK (btrim(tenant_id) <> ''),
    CONSTRAINT ck_reservations_business_transaction_nonblank
        CHECK (btrim(business_transaction_id) <> ''),
    CONSTRAINT ck_reservations_order_nonblank CHECK (btrim(order_id) <> ''),
    CONSTRAINT ck_reservations_status CHECK (status IN ('RESERVED', 'REJECTED')),
    CONSTRAINT ck_reservations_rejection_reason CHECK ((
        (status = 'RESERVED' AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND rejection_reason = 'OUT_OF_STOCK')
    ) IS TRUE),
    CONSTRAINT ck_reservations_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_reservations_timestamps CHECK (updated_at >= created_at)
);

CREATE TABLE aegisflow.reservation_lines (
    tenant_id varchar(128) NOT NULL,
    reservation_id uuid NOT NULL,
    sku varchar(128) NOT NULL,
    quantity integer NOT NULL,
    CONSTRAINT pk_reservation_lines PRIMARY KEY (tenant_id, reservation_id, sku),
    CONSTRAINT fk_reservation_lines_reservation
        FOREIGN KEY (tenant_id, reservation_id)
        REFERENCES aegisflow.reservations (tenant_id, reservation_id),
    CONSTRAINT ck_reservation_lines_tenant_nonblank CHECK (btrim(tenant_id) <> ''),
    CONSTRAINT ck_reservation_lines_sku_nonblank CHECK (btrim(sku) <> ''),
    CONSTRAINT ck_reservation_lines_quantity CHECK (quantity BETWEEN 1 AND 1000000)
);

CREATE TABLE aegisflow.outbox_event (
    tenant_id varchar(128) NOT NULL,
    outbox_id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    event_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    envelope jsonb NOT NULL,
    payload_hash char(64) NOT NULL,
    payload_hash_version varchar(32) NOT NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz(6) NOT NULL,
    published_at timestamptz(6),
    publish_attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz(6),
    last_error_code varchar(64),
    lease_owner varchar(128),
    lease_until timestamptz(6),
    fencing_token bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_event PRIMARY KEY (tenant_id, outbox_id),
    CONSTRAINT uq_outbox_event_tenant_event UNIQUE (tenant_id, event_id),
    CONSTRAINT uq_outbox_event_global_event UNIQUE (event_id),
    CONSTRAINT uq_outbox_event_aggregate_version
        UNIQUE (tenant_id, aggregate_id, aggregate_version),
    CONSTRAINT fk_outbox_event_reservation
        FOREIGN KEY (tenant_id, aggregate_id)
        REFERENCES aegisflow.reservations (tenant_id, reservation_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_outbox_tenant_nonblank CHECK (btrim(tenant_id) <> ''),
    CONSTRAINT ck_outbox_aggregate_version CHECK (aggregate_version >= 1),
    CONSTRAINT ck_outbox_event_type
        CHECK (event_type IN ('InventoryReserved', 'InventoryRejected')),
    CONSTRAINT ck_outbox_envelope_object CHECK ((jsonb_typeof(envelope) = 'object') IS TRUE),
    CONSTRAINT ck_outbox_envelope_contract CHECK ((
        envelope ?& ARRAY[
            'tenantId', 'eventId', 'sourceSystem', 'businessTransactionId',
            'schemaVersion', 'occurredAt', 'correlationId', 'eventType', 'details'
        ]
        AND envelope->>'tenantId' = tenant_id
        AND envelope->>'eventId' = event_id::text
        AND envelope->>'sourceSystem' = 'warehouse-service'
        AND envelope->>'eventType' = event_type
        AND envelope->>'schemaVersion' = '1.0'
        AND jsonb_typeof(envelope->'details') = 'object'
    ) IS TRUE),
    CONSTRAINT ck_outbox_payload_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_outbox_payload_hash_version
        CHECK (payload_hash_version = 'aegisflow-json-v1'),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_publication_time CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CONSTRAINT ck_outbox_publish_attempts CHECK (publish_attempts >= 0),
    CONSTRAINT ck_outbox_pending_retry_time CHECK (
        status <> 'PENDING' OR next_attempt_at IS NOT NULL
    ),
    CONSTRAINT ck_outbox_lease_pair CHECK (
        (lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
    ),
    CONSTRAINT ck_outbox_fencing_token CHECK (fencing_token >= 0)
);

CREATE INDEX ix_outbox_pending_publication
    ON aegisflow.outbox_event (next_attempt_at, created_at, outbox_id)
    WHERE status = 'PENDING';

CREATE TABLE aegisflow.idempotency_record (
    tenant_id varchar(128) NOT NULL,
    operation varchar(32) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    request_hash_version varchar(32) NOT NULL,
    resource_id uuid NOT NULL,
    resource_version bigint NOT NULL,
    event_id uuid NOT NULL,
    result_code varchar(16) NOT NULL,
    created_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_reservation
        FOREIGN KEY (tenant_id, resource_id)
        REFERENCES aegisflow.reservations (tenant_id, reservation_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_idempotency_outbox_event
        FOREIGN KEY (tenant_id, event_id)
        REFERENCES aegisflow.outbox_event (tenant_id, event_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_idempotency_tenant_nonblank CHECK (btrim(tenant_id) <> ''),
    CONSTRAINT ck_idempotency_operation CHECK (operation = 'CREATE_RESERVATION'),
    CONSTRAINT ck_idempotency_key_nonblank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_idempotency_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotency_hash_version
        CHECK (request_hash_version = 'aegisflow-json-v1'),
    CONSTRAINT ck_idempotency_resource_version CHECK (resource_version >= 1),
    CONSTRAINT ck_idempotency_result CHECK (result_code IN ('RESERVED', 'REJECTED'))
);

CREATE FUNCTION aegisflow.protect_outbox_identity_and_payload()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'outbox rows are append-only';
    END IF;

    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.outbox_id IS DISTINCT FROM OLD.outbox_id
       OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
       OR NEW.aggregate_version IS DISTINCT FROM OLD.aggregate_version
       OR NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.envelope IS DISTINCT FROM OLD.envelope
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
       OR NEW.payload_hash_version IS DISTINCT FROM OLD.payload_hash_version
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'outbox identity and payload are immutable';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_outbox_immutable
BEFORE UPDATE OR DELETE ON aegisflow.outbox_event
FOR EACH ROW EXECUTE FUNCTION aegisflow.protect_outbox_identity_and_payload();

REVOKE ALL ON aegisflow.reservations FROM PUBLIC;
REVOKE ALL ON aegisflow.reservation_lines FROM PUBLIC;
REVOKE ALL ON aegisflow.idempotency_record FROM PUBLIC;
REVOKE ALL ON aegisflow.outbox_event FROM PUBLIC;

GRANT USAGE ON SCHEMA aegisflow TO "${runtimeRole}";
GRANT SELECT, INSERT, UPDATE ON aegisflow.reservations TO "${runtimeRole}";
GRANT SELECT, INSERT, UPDATE ON aegisflow.reservation_lines TO "${runtimeRole}";
GRANT SELECT, INSERT ON aegisflow.idempotency_record TO "${runtimeRole}";
GRANT SELECT, INSERT ON aegisflow.outbox_event TO "${runtimeRole}";
GRANT UPDATE (
    status, published_at, publish_attempts, next_attempt_at,
    last_error_code, lease_owner, lease_until, fencing_token
) ON aegisflow.outbox_event TO "${runtimeRole}";

COMMENT ON TABLE aegisflow.outbox_event IS
    'Durable publication intent only; Kafka relay is intentionally deferred to P0.3.';
