CREATE TABLE aegisflow.orders (
    tenant_id varchar(128) NOT NULL CHECK (length(trim(tenant_id)) > 0),
    order_id uuid NOT NULL,
    business_transaction_id varchar(128) NOT NULL CHECK (length(trim(business_transaction_id)) > 0),
    correlation_id varchar(128) NOT NULL CHECK (length(trim(correlation_id)) > 0),
    status varchar(32) NOT NULL CHECK (status IN ('CREATED','CONFIRMED','CANCELLED')),
    total_amount numeric(19,4) NOT NULL CHECK (total_amount > 0),
    currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    lock_version bigint NOT NULL CHECK (lock_version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    PRIMARY KEY (tenant_id,order_id),
    UNIQUE (tenant_id,business_transaction_id)
);
CREATE TABLE aegisflow.order_lines (
    tenant_id varchar(128) NOT NULL,
    order_id uuid NOT NULL,
    line_no integer NOT NULL CHECK (line_no BETWEEN 1 AND 100),
    sku varchar(128) NOT NULL CHECK (length(trim(sku)) > 0),
    quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    PRIMARY KEY (tenant_id,order_id,line_no),
    UNIQUE (tenant_id,order_id,sku),
    FOREIGN KEY (tenant_id,order_id) REFERENCES aegisflow.orders(tenant_id,order_id)
);
CREATE TABLE aegisflow.outbox_event (
    tenant_id varchar(128) NOT NULL,
    outbox_id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL CHECK (aggregate_version >= 1),
    event_id uuid NOT NULL,
    event_type varchar(64) NOT NULL CHECK (event_type IN ('OrderCreated','OrderConfirmed','OrderCancelled')),
    envelope jsonb NOT NULL CHECK (jsonb_typeof(envelope) = 'object'),
    payload_hash varchar(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    payload_hash_version varchar(64) NOT NULL CHECK (payload_hash_version = 'aegisflow-json-v1'),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    publish_attempts integer NOT NULL DEFAULT 0 CHECK (publish_attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    last_error_code varchar(64),
    lease_owner varchar(128),
    lease_until timestamptz,
    fencing_token bigint NOT NULL DEFAULT 0 CHECK (fencing_token >= 0),
    PRIMARY KEY (tenant_id,outbox_id),
    UNIQUE (tenant_id,event_id),
    CONSTRAINT uq_order_outbox_global_event UNIQUE (event_id),
    UNIQUE (tenant_id,aggregate_id,aggregate_version),
    FOREIGN KEY (tenant_id,aggregate_id) REFERENCES aegisflow.orders(tenant_id,order_id),
    CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CHECK ((envelope->>'tenantId' = tenant_id AND envelope->>'eventId' = event_id::text
        AND envelope->>'sourceSystem' = 'order-service' AND envelope->>'eventType' = event_type
        AND envelope->>'schemaVersion' = '1.0' AND jsonb_typeof(envelope->'details') = 'object') IS TRUE),
    CHECK (envelope ?& ARRAY['tenantId','eventId','sourceSystem','businessTransactionId','schemaVersion','occurredAt','correlationId','eventType','details'])
);
CREATE INDEX outbox_pending_idx ON aegisflow.outbox_event(status,next_attempt_at,created_at)
    WHERE status = 'PENDING';
CREATE TABLE aegisflow.idempotency_record (
    tenant_id varchar(128) NOT NULL,
    operation varchar(32) NOT NULL CHECK (operation = 'CREATE_ORDER'),
    idempotency_key varchar(128) NOT NULL CHECK (length(trim(idempotency_key)) > 0),
    request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    request_hash_version varchar(64) NOT NULL CHECK (request_hash_version = 'aegisflow-json-v1'),
    result_code varchar(32) NOT NULL CHECK (result_code = 'CREATED'),
    resource_id uuid NOT NULL,
    resource_version bigint NOT NULL CHECK (resource_version >= 1),
    event_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id,operation,idempotency_key),
    FOREIGN KEY (tenant_id,resource_id) REFERENCES aegisflow.orders(tenant_id,order_id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (tenant_id,event_id) REFERENCES aegisflow.outbox_event(tenant_id,event_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE FUNCTION aegisflow.reject_outbox_payload_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Outbox deletion requires a future retention migration'; END IF;
    IF (NEW.tenant_id,NEW.outbox_id,NEW.aggregate_id,NEW.aggregate_version,NEW.event_id,NEW.event_type,
        NEW.envelope,NEW.payload_hash,NEW.payload_hash_version,NEW.created_at)
       IS DISTINCT FROM
       (OLD.tenant_id,OLD.outbox_id,OLD.aggregate_id,OLD.aggregate_version,OLD.event_id,OLD.event_type,
        OLD.envelope,OLD.payload_hash,OLD.payload_hash_version,OLD.created_at)
    THEN RAISE EXCEPTION 'Outbox identity and envelope are immutable'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER outbox_immutable BEFORE UPDATE OR DELETE ON aegisflow.outbox_event
    FOR EACH ROW EXECUTE FUNCTION aegisflow.reject_outbox_payload_change();
GRANT USAGE ON SCHEMA aegisflow TO "${runtimeRole}";
GRANT SELECT,INSERT,UPDATE ON aegisflow.orders,aegisflow.order_lines TO "${runtimeRole}";
GRANT SELECT,INSERT ON aegisflow.idempotency_record,aegisflow.outbox_event TO "${runtimeRole}";
GRANT UPDATE(status,published_at,publish_attempts,next_attempt_at,last_error_code,lease_owner,lease_until,fencing_token)
    ON aegisflow.outbox_event TO "${runtimeRole}";
