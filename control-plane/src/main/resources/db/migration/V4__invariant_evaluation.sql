-- Detection-owned append-only decisions; no changes to intake/facts or their privileges.
CREATE TABLE aegisflow.invariant_evaluation (
    tenant_id varchar(128) NOT NULL,
    evaluation_id uuid NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    invariant_id varchar(32) NOT NULL CHECK (invariant_id='INV-002'),
    invariant_version integer NOT NULL CHECK (invariant_version=1),
    projection_version bigint NOT NULL CHECK (projection_version>0),
    phase varchar(16) NOT NULL CHECK (phase IN ('PENDING','ENFORCED')),
    unsettled boolean NOT NULL,
    result varchar(16) NOT NULL CHECK (result IN ('SATISFIED','VIOLATED','UNKNOWN')),
    reason_code varchar(40) NOT NULL CHECK (reason_code IN
        ('PROJECTION_UNSETTLED','ORDER_NOT_OBSERVED','ORDER_AMBIGUOUS','CONTRADICTORY_OUTCOMES',
         'MISSING_OBSERVED_OUTCOME','ORDER_MISMATCH','OUTCOME_PRECEDES_ORDER','QUANTITY_MISMATCH',
         'UNSUPPORTED_REJECTION','UNSUPPORTED_OUTCOME','OUTCOME_AFTER_DEADLINE',
         'AWAITING_ENFORCEMENT','VALID_OBSERVED_OUTCOME')),
    evaluated_at timestamptz NOT NULL,
    contract_id varchar(32) NOT NULL CHECK (contract_id='ECOM_ORDER_V1'),
    contract_version integer NOT NULL CHECK (contract_version=1),
    configuration_version varchar(64) NOT NULL,
    inventory_duration_seconds integer NOT NULL CHECK (inventory_duration_seconds BETWEEN 1 AND 86400),
    lateness_seconds integer NOT NULL CHECK (lateness_seconds BETWEEN 0 AND 86400),
    due_at timestamptz,
    enforce_after timestamptz,
    observation jsonb NOT NULL CHECK (jsonb_typeof(observation)='object'),
    PRIMARY KEY (tenant_id,evaluation_id),
    UNIQUE (tenant_id,business_transaction_id,invariant_id,invariant_version,projection_version,phase,unsettled),
    FOREIGN KEY (tenant_id,business_transaction_id)
        REFERENCES aegisflow.business_transaction(tenant_id,business_transaction_id),
    CHECK (NOT unsettled OR result='UNKNOWN'),
    CHECK (result<>'VIOLATED' OR (phase='ENFORCED' AND enforce_after IS NOT NULL AND evaluated_at>=enforce_after)),
    CHECK ((due_at IS NULL AND enforce_after IS NULL) OR
        (due_at IS NOT NULL AND enforce_after IS NOT NULL AND enforce_after=due_at+lateness_seconds*interval '1 second')),
    CHECK (observation ?& ARRAY['tenant','transaction']),
    CHECK (observation->>'tenant' IS NOT NULL AND observation->>'transaction' IS NOT NULL
        AND observation->>'tenant'=tenant_id AND observation->>'transaction'=business_transaction_id)
);
CREATE INDEX evaluation_transaction_history ON aegisflow.invariant_evaluation
    (tenant_id,business_transaction_id,evaluated_at,evaluation_id);
-- A cyclic key scan eventually revisits unchanged deadlines and earlier/late-visible keys.
-- Cursor update and evaluations share one transaction; losing a callback cannot lose work.
CREATE TABLE aegisflow.detection_sweep (
    singleton integer PRIMARY KEY CHECK (singleton=1),
    tenant_cursor varchar(128) NOT NULL,
    transaction_cursor varchar(128) NOT NULL
);
INSERT INTO aegisflow.detection_sweep VALUES (1,'','');
GRANT SELECT,INSERT ON aegisflow.invariant_evaluation TO "${runtimeRole}";
GRANT SELECT ON aegisflow.detection_sweep TO "${runtimeRole}";
GRANT UPDATE (tenant_cursor,transaction_cursor) ON aegisflow.detection_sweep TO "${runtimeRole}";
