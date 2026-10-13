CREATE TABLE aegisflow.payments (
    tenant_id varchar(128) NOT NULL,
    payment_id uuid NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    order_id varchar(128) NOT NULL,
    status varchar(16) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency varchar(3) NOT NULL,
    decline_reason_code varchar(64),
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (tenant_id, payment_id),
    CONSTRAINT uq_payments_tenant_order UNIQUE (tenant_id, order_id),
    CONSTRAINT ck_payments_tenant CHECK (length(btrim(tenant_id)) > 0),
    CONSTRAINT ck_payments_business_transaction CHECK (length(btrim(business_transaction_id)) > 0),
    CONSTRAINT ck_payments_order CHECK (length(btrim(order_id)) > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('AUTHORIZED', 'DECLINED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payments_decline_reason CHECK ((
        (status = 'AUTHORIZED' AND decline_reason_code IS NULL)
        OR (status = 'DECLINED' AND decline_reason_code IN (
            'INSUFFICIENT_FUNDS',
            'PAYMENT_METHOD_REJECTED',
            'RISK_REJECTED',
            'SIMULATED_DECLINE'
        ))
    ) IS TRUE),
    CONSTRAINT ck_payments_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_payments_time_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_payments_tenant_transaction
    ON aegisflow.payments (tenant_id, business_transaction_id);

CREATE TABLE aegisflow.outbox_event (
    tenant_id varchar(128) NOT NULL,
    outbox_id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    event_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    schema_version varchar(16) NOT NULL,
    source_system varchar(64) NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    correlation_id varchar(128) NOT NULL,
    envelope jsonb NOT NULL,
    payload_hash varchar(64) NOT NULL,
    payload_hash_version varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    publish_attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_error_code varchar(64),
    lease_owner varchar(128),
    lease_until timestamptz,
    fencing_token bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_event PRIMARY KEY (tenant_id, outbox_id),
    CONSTRAINT uq_outbox_tenant_event UNIQUE (tenant_id, event_id),
    CONSTRAINT uq_outbox_event_global UNIQUE (event_id),
    CONSTRAINT uq_outbox_aggregate_version UNIQUE (tenant_id, aggregate_id, aggregate_version),
    CONSTRAINT fk_outbox_payment FOREIGN KEY (tenant_id, aggregate_id)
        REFERENCES aegisflow.payments (tenant_id, payment_id),
    CONSTRAINT ck_outbox_tenant CHECK (length(btrim(tenant_id)) > 0),
    CONSTRAINT ck_outbox_aggregate_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_outbox_event_type CHECK (event_type IN ('PaymentAuthorized', 'PaymentDeclined')),
    CONSTRAINT ck_outbox_schema_version CHECK (schema_version = '1.0'),
    CONSTRAINT ck_outbox_source_system CHECK (source_system = 'payment-service'),
    CONSTRAINT ck_outbox_business_transaction CHECK (length(btrim(business_transaction_id)) > 0),
    CONSTRAINT ck_outbox_correlation CHECK (length(btrim(correlation_id)) > 0),
    CONSTRAINT ck_outbox_envelope_consistency CHECK ((
        jsonb_typeof(envelope) = 'object'
        AND envelope ?& ARRAY[
            'tenantId',
            'eventId',
            'sourceSystem',
            'businessTransactionId',
            'schemaVersion',
            'occurredAt',
            'correlationId',
            'eventType',
            'details'
        ]
        AND jsonb_typeof(envelope -> 'tenantId') = 'string'
        AND jsonb_typeof(envelope -> 'eventId') = 'string'
        AND jsonb_typeof(envelope -> 'sourceSystem') = 'string'
        AND jsonb_typeof(envelope -> 'businessTransactionId') = 'string'
        AND jsonb_typeof(envelope -> 'schemaVersion') = 'string'
        AND jsonb_typeof(envelope -> 'occurredAt') = 'string'
        AND jsonb_typeof(envelope -> 'correlationId') = 'string'
        AND jsonb_typeof(envelope -> 'eventType') = 'string'
        AND jsonb_typeof(envelope -> 'details') = 'object'
        AND envelope ->> 'tenantId' = tenant_id
        AND envelope ->> 'eventId' = event_id::text
        AND envelope ->> 'sourceSystem' = source_system
        AND envelope ->> 'businessTransactionId' = business_transaction_id
        AND envelope ->> 'schemaVersion' = schema_version
        AND (envelope ->> 'occurredAt')::timestamptz = created_at
        AND envelope ->> 'correlationId' = correlation_id
        AND envelope ->> 'eventType' = event_type
        AND envelope -> 'details' ?& ARRAY[
            'paymentId',
            'orderId',
            'amount',
            'currency',
            'paymentVersion'
        ]
        AND jsonb_typeof(envelope -> 'details' -> 'paymentId') = 'string'
        AND jsonb_typeof(envelope -> 'details' -> 'orderId') = 'string'
        AND jsonb_typeof(envelope -> 'details' -> 'amount') = 'string'
        AND jsonb_typeof(envelope -> 'details' -> 'currency') = 'string'
        AND jsonb_typeof(envelope -> 'details' -> 'paymentVersion') = 'number'
        AND envelope -> 'details' ->> 'paymentId' = aggregate_id::text
        AND (envelope -> 'details' ->> 'paymentVersion')::bigint = aggregate_version
        AND (
            (event_type = 'PaymentAuthorized'
                AND NOT (envelope -> 'details' ? 'reasonCode'))
            OR (event_type = 'PaymentDeclined'
                AND jsonb_typeof(envelope -> 'details' -> 'reasonCode') = 'string'
                AND envelope -> 'details' ->> 'reasonCode' IN (
                    'INSUFFICIENT_FUNDS',
                    'PAYMENT_METHOD_REJECTED',
                    'RISK_REJECTED',
                    'SIMULATED_DECLINE'
                ))
        )
    ) IS TRUE),
    CONSTRAINT ck_outbox_payload_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_outbox_payload_hash_version CHECK (payload_hash_version = 'aegisflow-json-v1'),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_publish_attempts CHECK (publish_attempts >= 0),
    CONSTRAINT ck_outbox_fencing_token CHECK (fencing_token >= 0),
    CONSTRAINT ck_outbox_published_time CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CONSTRAINT ck_outbox_lease_pair CHECK ((lease_owner IS NULL) = (lease_until IS NULL))
);

CREATE INDEX ix_outbox_pending
    ON aegisflow.outbox_event (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE aegisflow.idempotency_record (
    tenant_id varchar(128) NOT NULL,
    operation varchar(32) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash_version varchar(32) NOT NULL,
    request_hash varchar(64) NOT NULL,
    result_code varchar(16) NOT NULL,
    resource_id uuid NOT NULL,
    resource_version bigint NOT NULL,
    event_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_payment FOREIGN KEY (tenant_id, resource_id)
        REFERENCES aegisflow.payments (tenant_id, payment_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_idempotency_outbox FOREIGN KEY (tenant_id, event_id)
        REFERENCES aegisflow.outbox_event (tenant_id, event_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_idempotency_tenant CHECK (length(btrim(tenant_id)) > 0),
    CONSTRAINT ck_idempotency_operation CHECK (operation = 'CREATE_PAYMENT'),
    CONSTRAINT ck_idempotency_key CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT ck_idempotency_hash_version CHECK (request_hash_version = 'aegisflow-json-v1'),
    CONSTRAINT ck_idempotency_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotency_result CHECK (result_code IN ('AUTHORIZED', 'DECLINED')),
    CONSTRAINT ck_idempotency_resource_version CHECK (resource_version > 0)
);

CREATE FUNCTION aegisflow.reject_outbox_identity_change()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, aegisflow
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'outbox rows are append-only' USING ERRCODE = '55000';
    END IF;

    IF ROW(
        OLD.tenant_id,
        OLD.outbox_id,
        OLD.aggregate_id,
        OLD.aggregate_version,
        OLD.event_id,
        OLD.event_type,
        OLD.schema_version,
        OLD.source_system,
        OLD.business_transaction_id,
        OLD.correlation_id,
        OLD.envelope,
        OLD.payload_hash,
        OLD.payload_hash_version,
        OLD.created_at
    ) IS DISTINCT FROM ROW(
        NEW.tenant_id,
        NEW.outbox_id,
        NEW.aggregate_id,
        NEW.aggregate_version,
        NEW.event_id,
        NEW.event_type,
        NEW.schema_version,
        NEW.source_system,
        NEW.business_transaction_id,
        NEW.correlation_id,
        NEW.envelope,
        NEW.payload_hash,
        NEW.payload_hash_version,
        NEW.created_at
    ) THEN
        RAISE EXCEPTION 'outbox identity and payload are immutable' USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_outbox_immutable
    BEFORE UPDATE OR DELETE ON aegisflow.outbox_event
    FOR EACH ROW EXECUTE FUNCTION aegisflow.reject_outbox_identity_change();

REVOKE ALL ON TABLE aegisflow.payments FROM PUBLIC;
REVOKE ALL ON TABLE aegisflow.outbox_event FROM PUBLIC;
REVOKE ALL ON TABLE aegisflow.idempotency_record FROM PUBLIC;
REVOKE ALL ON FUNCTION aegisflow.reject_outbox_identity_change() FROM PUBLIC;

GRANT USAGE ON SCHEMA aegisflow TO "${runtimeRole}";
GRANT SELECT, INSERT, UPDATE ON TABLE aegisflow.payments TO "${runtimeRole}";
GRANT SELECT, INSERT ON TABLE aegisflow.idempotency_record TO "${runtimeRole}";
GRANT SELECT, INSERT ON TABLE aegisflow.outbox_event TO "${runtimeRole}";
GRANT UPDATE (
    status,
    published_at,
    publish_attempts,
    next_attempt_at,
    last_error_code,
    lease_owner,
    lease_until,
    fencing_token
) ON TABLE aegisflow.outbox_event TO "${runtimeRole}";
