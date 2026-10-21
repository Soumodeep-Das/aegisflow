-- Composite identities make every incident/evaluation/fact reference tenant AND transaction scoped.
ALTER TABLE aegisflow.invariant_evaluation ADD CONSTRAINT evaluation_incident_reference
    UNIQUE (tenant_id,business_transaction_id,evaluation_id);
CREATE TABLE aegisflow.incident (
    tenant_id varchar(128) NOT NULL,
    incident_id uuid NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    invariant_id varchar(32) NOT NULL CHECK (invariant_id='INV-002'),
    invariant_version integer NOT NULL CHECK (invariant_version=1),
    contract_id varchar(32) NOT NULL CHECK (contract_id='ECOM_ORDER_V1'),
    contract_version integer NOT NULL CHECK (contract_version=1),
    configuration_version varchar(64) NOT NULL,
    episode_key varchar(256) NOT NULL,
    severity varchar(16) NOT NULL CHECK (severity IN ('MEDIUM','HIGH')),
    reason_code varchar(40) NOT NULL,
    state varchar(24) NOT NULL CHECK (state='DETECTED'),
    state_version bigint NOT NULL CHECK (state_version=1),
    incident_revision bigint NOT NULL CHECK (incident_revision>0),
    opened_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at>=opened_at),
    initial_evaluation_id uuid NOT NULL,
    PRIMARY KEY (tenant_id,incident_id),
    UNIQUE (tenant_id,business_transaction_id,incident_id),
    UNIQUE (tenant_id,business_transaction_id,invariant_id,invariant_version,contract_id,contract_version,configuration_version),
    UNIQUE (tenant_id,business_transaction_id,episode_key),
    FOREIGN KEY (tenant_id,business_transaction_id,initial_evaluation_id)
        REFERENCES aegisflow.invariant_evaluation(tenant_id,business_transaction_id,evaluation_id)
);
CREATE TABLE aegisflow.incident_timeline (
    tenant_id varchar(128) NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    incident_id uuid NOT NULL,
    evaluation_id uuid NOT NULL,
    entry_kind varchar(32) NOT NULL CHECK (entry_kind IN ('DETECTED','VIOLATION_REOBSERVED','EVIDENCE_CHANGED')),
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id,incident_id,evaluation_id),
    UNIQUE (tenant_id,business_transaction_id,incident_id,evaluation_id),
    FOREIGN KEY (tenant_id,business_transaction_id,incident_id)
        REFERENCES aegisflow.incident(tenant_id,business_transaction_id,incident_id),
    FOREIGN KEY (tenant_id,business_transaction_id,evaluation_id)
        REFERENCES aegisflow.invariant_evaluation(tenant_id,business_transaction_id,evaluation_id)
);
CREATE TABLE aegisflow.incident_fact_reference (
    tenant_id varchar(128) NOT NULL,
    business_transaction_id varchar(128) NOT NULL,
    incident_id uuid NOT NULL,
    evaluation_id uuid NOT NULL,
    fact_id uuid NOT NULL,
    PRIMARY KEY (tenant_id,incident_id,evaluation_id,fact_id),
    FOREIGN KEY (tenant_id,business_transaction_id,incident_id,evaluation_id)
        REFERENCES aegisflow.incident_timeline(tenant_id,business_transaction_id,incident_id,evaluation_id),
    FOREIGN KEY (tenant_id,business_transaction_id,fact_id)
        REFERENCES aegisflow.observed_fact(tenant_id,business_transaction_id,fact_id)
);
CREATE INDEX incident_pending_evaluation ON aegisflow.invariant_evaluation
    (evaluated_at,tenant_id,business_transaction_id,evaluation_id);
GRANT SELECT,INSERT ON aegisflow.incident,aegisflow.incident_timeline,aegisflow.incident_fact_reference TO "${runtimeRole}";
GRANT UPDATE (incident_revision,updated_at) ON aegisflow.incident TO "${runtimeRole}";
