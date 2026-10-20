-- P0.3 accepted payloads and permissions stay unchanged. Extra identity supports tenant/transaction provenance FKs.
ALTER TABLE aegisflow.inbox_message ADD CONSTRAINT inbox_projection_identity
    UNIQUE (tenant_id, source_system, event_id, business_transaction_id);
CREATE INDEX inbox_projection_scan ON aegisflow.inbox_message(received_at,tenant_id,source_system,event_id);

-- Owned by eventing; neither APPLIED nor BLOCKED changes the meaning of inbox ACCEPTED.
CREATE TABLE aegisflow.inbox_projection_progress (
    tenant_id varchar(128) NOT NULL,
    source_system varchar(32) NOT NULL,
    event_id uuid NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    projector_version integer NOT NULL CHECK (projector_version=1),
    status varchar(16) NOT NULL CHECK (status IN ('APPLIED','BLOCKED')),
    reason_code varchar(40),
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id,source_system,event_id),
    FOREIGN KEY (tenant_id,source_system,event_id,business_transaction_id)
        REFERENCES aegisflow.inbox_message(tenant_id,source_system,event_id,business_transaction_id),
    CHECK ((status='APPLIED' AND reason_code IS NULL) OR
           (status='BLOCKED' AND reason_code IS NOT NULL AND reason_code IN ('ACCEPTED_SHAPE_UNSUPPORTED','TRANSACTION_FACT_LIMIT','PERSISTENCE_CONSTRAINT')))
);
CREATE INDEX projection_blocked_lookup ON aegisflow.inbox_projection_progress(tenant_id,business_transaction_id)
    WHERE status='BLOCKED';

-- Correlation owns facts and materialized transactions. Facts preserve exact accepted provenance.
CREATE TABLE aegisflow.observed_fact (
    tenant_id varchar(128) NOT NULL,
    fact_id uuid NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    source_system varchar(32) NOT NULL,
    inbox_event_id uuid NOT NULL,
    fact_type varchar(64) NOT NULL CHECK (fact_type IN
        ('OrderCreated','PaymentAuthorized','PaymentDeclined','InventoryReserved','InventoryRejected')),
    source_entity_id varchar(128) NOT NULL CHECK (length(btrim(source_entity_id))>0),
    source_entity_version bigint NOT NULL CHECK (source_entity_version>0),
    order_id varchar(128) NOT NULL CHECK (length(btrim(order_id))>0),
    effective_at timestamptz NOT NULL,
    observed_at timestamptz NOT NULL,
    projected_at timestamptz NOT NULL,
    schema_version varchar(16) NOT NULL,
    canonical_value jsonb NOT NULL CHECK (jsonb_typeof(canonical_value)='object'),
    payload_hash char(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    payload_hash_version varchar(32) NOT NULL CHECK (payload_hash_version='aegisflow-inbox-json-v1'),
    PRIMARY KEY (tenant_id,fact_id),
    UNIQUE (tenant_id,source_system,inbox_event_id),
    UNIQUE (tenant_id,business_transaction_id,fact_id),
    FOREIGN KEY (tenant_id,source_system,inbox_event_id,business_transaction_id)
        REFERENCES aegisflow.inbox_message(tenant_id,source_system,event_id,business_transaction_id)
);
CREATE INDEX fact_transaction_lookup ON aegisflow.observed_fact(tenant_id,business_transaction_id,source_system,source_entity_version);

CREATE TABLE aegisflow.business_transaction (
    tenant_id varchar(128) NOT NULL CHECK (length(btrim(tenant_id))>0),
    business_transaction_id varchar(128) NOT NULL CHECK (length(btrim(business_transaction_id))>0),
    contract_id varchar(32) NOT NULL CHECK (contract_id='ECOM_ORDER_V1'),
    contract_version integer NOT NULL CHECK (contract_version=1),
    configuration_version varchar(64) NOT NULL CHECK (length(btrim(configuration_version))>0),
    inventory_duration_seconds integer NOT NULL CHECK (inventory_duration_seconds BETWEEN 1 AND 86400),
    lateness_seconds integer NOT NULL CHECK (lateness_seconds BETWEEN 0 AND 86400),
    order_fact_id uuid,
    payment_fact_id uuid,
    inventory_fact_id uuid,
    derived_status varchar(32) NOT NULL CHECK (derived_status IN
        ('AWAITING_ORDER','PENDING_INVENTORY','INVENTORY_OBSERVED','CONFLICT')),
    projection_version bigint NOT NULL CHECK (projection_version>0),
    first_observed_at timestamptz NOT NULL,
    last_observed_at timestamptz NOT NULL,
    processed_at timestamptz NOT NULL,
    due_at timestamptz,
    enforce_after timestamptz,
    PRIMARY KEY (tenant_id,business_transaction_id),
    FOREIGN KEY (tenant_id,business_transaction_id,order_fact_id)
        REFERENCES aegisflow.observed_fact(tenant_id,business_transaction_id,fact_id),
    FOREIGN KEY (tenant_id,business_transaction_id,payment_fact_id)
        REFERENCES aegisflow.observed_fact(tenant_id,business_transaction_id,fact_id),
    FOREIGN KEY (tenant_id,business_transaction_id,inventory_fact_id)
        REFERENCES aegisflow.observed_fact(tenant_id,business_transaction_id,fact_id),
    CHECK (first_observed_at<=last_observed_at),
    CHECK ((due_at IS NULL AND enforce_after IS NULL) OR
           (due_at IS NOT NULL AND enforce_after IS NOT NULL AND order_fact_id IS NOT NULL
            AND enforce_after=due_at+lateness_seconds*interval '1 second'))
);
CREATE INDEX transaction_deadline_lookup ON aegisflow.business_transaction(enforce_after,tenant_id,business_transaction_id)
    WHERE enforce_after IS NOT NULL;
GRANT SELECT,INSERT ON aegisflow.inbox_projection_progress,aegisflow.observed_fact,aegisflow.business_transaction TO "${runtimeRole}";
GRANT UPDATE (order_fact_id,payment_fact_id,inventory_fact_id,derived_status,projection_version,
    first_observed_at,last_observed_at,processed_at,due_at,enforce_after)
    ON aegisflow.business_transaction TO "${runtimeRole}";
