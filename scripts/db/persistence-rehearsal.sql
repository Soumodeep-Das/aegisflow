\set ON_ERROR_STOP on
\getenv runtime_user RUNTIME_DB_USER
\getenv migration_user MIGRATION_DB_USER
\getenv admin_user POSTGRES_USER
\getenv fixture_marker REHEARSAL_MARKER

-- This is NOT a Flyway migration or business fixture. It exists only in a
-- fresh, explicitly named disposable Compose project to prove retained storage.
\if :setup_fixture
CREATE SCHEMA p03_rehearsal AUTHORIZATION CURRENT_USER;
CREATE TABLE p03_rehearsal.sentinel (
    fixture_id integer PRIMARY KEY CHECK (fixture_id = 1),
    marker text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE FUNCTION p03_rehearsal.keep_sentinel_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' OR NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'The rehearsal sentinel is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER sentinel_immutable BEFORE UPDATE OR DELETE ON p03_rehearsal.sentinel
    FOR EACH ROW EXECUTE FUNCTION p03_rehearsal.keep_sentinel_immutable();
REVOKE ALL ON SCHEMA p03_rehearsal FROM PUBLIC;
GRANT USAGE ON SCHEMA p03_rehearsal TO :"runtime_user";
GRANT SELECT,INSERT,UPDATE ON p03_rehearsal.sentinel TO :"runtime_user";
\endif

\if :runtime_checks
BEGIN;
INSERT INTO p03_rehearsal.sentinel (fixture_id,marker) VALUES (1, :'fixture_marker') ON CONFLICT DO NOTHING;
UPDATE p03_rehearsal.sentinel SET marker = marker WHERE fixture_id = 1;
SELECT 1 / CASE WHEN (SELECT marker = :'fixture_marker' FROM p03_rehearsal.sentinel WHERE fixture_id = 1) THEN 1 ELSE 0 END;
DO $$
BEGIN
    -- Verify the actual V1 grants too; fixture DML alone would not prove them.
    IF current_database() = 'aegisflow_order' THEN
        IF NOT (has_table_privilege(current_user,'aegisflow.orders','SELECT')
            AND has_table_privilege(current_user,'aegisflow.orders','INSERT')
            AND has_table_privilege(current_user,'aegisflow.orders','UPDATE')
            AND has_table_privilege(current_user,'aegisflow.order_lines','INSERT')) THEN
            RAISE EXCEPTION 'Order runtime lacks its expected business privileges';
        END IF;
    ELSIF current_database() = 'aegisflow_payment' THEN
        IF NOT (has_table_privilege(current_user,'aegisflow.payments','SELECT')
            AND has_table_privilege(current_user,'aegisflow.payments','INSERT')
            AND has_table_privilege(current_user,'aegisflow.payments','UPDATE')) THEN
            RAISE EXCEPTION 'Payment runtime lacks its expected business privileges';
        END IF;
    ELSIF current_database() = 'aegisflow_warehouse' THEN
        IF NOT (has_table_privilege(current_user,'aegisflow.reservations','SELECT')
            AND has_table_privilege(current_user,'aegisflow.reservations','INSERT')
            AND has_table_privilege(current_user,'aegisflow.reservations','UPDATE')
            AND has_table_privilege(current_user,'aegisflow.reservation_lines','INSERT')) THEN
            RAISE EXCEPTION 'Warehouse runtime lacks its expected business privileges';
        END IF;
    ELSIF current_database() <> 'aegisflow_core' THEN
        RAISE EXCEPTION 'Unexpected rehearsal database';
    END IF;
    IF current_database() <> 'aegisflow_core' THEN
        IF NOT (has_table_privilege(current_user,'aegisflow.outbox_event','SELECT')
            AND has_table_privilege(current_user,'aegisflow.outbox_event','INSERT')
            AND has_table_privilege(current_user,'aegisflow.idempotency_record','SELECT')
            AND has_table_privilege(current_user,'aegisflow.idempotency_record','INSERT')
            AND has_column_privilege(current_user,'aegisflow.outbox_event','status','UPDATE'))
            OR has_column_privilege(current_user,'aegisflow.outbox_event','envelope','UPDATE')
            OR has_table_privilege(current_user,'aegisflow.outbox_event','DELETE') THEN
            RAISE EXCEPTION 'Runtime outbox/idempotency privileges do not match the immutable-intent boundary';
        END IF;
    END IF;
    BEGIN
        EXECUTE 'CREATE TABLE aegisflow.p03_forbidden_ddl (id integer)';
        RAISE EXCEPTION 'Runtime unexpectedly created a business-schema table';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        EXECUTE 'CREATE TABLE public.p03_forbidden_ddl (id integer)';
        RAISE EXCEPTION 'Runtime unexpectedly created a public-schema table';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        EXECUTE 'CREATE SCHEMA p03_forbidden_schema';
        RAISE EXCEPTION 'Runtime unexpectedly created a schema';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        EXECUTE 'UPDATE aegisflow.flyway_schema_history SET description = description WHERE false';
        RAISE EXCEPTION 'Runtime unexpectedly has migration-history UPDATE';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        EXECUTE 'DELETE FROM aegisflow.flyway_schema_history WHERE false';
        RAISE EXCEPTION 'Runtime unexpectedly has migration-history DELETE';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        EXECUTE 'INSERT INTO aegisflow.flyway_schema_history SELECT * FROM aegisflow.flyway_schema_history WHERE false';
        RAISE EXCEPTION 'Runtime unexpectedly has migration-history INSERT';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
COMMIT;
\endif

\if :role_checks
-- All checks query real PostgreSQL catalog privileges, not guessed role names.
SELECT 1 / CASE WHEN :'admin_user' <> :'migration_user' AND :'admin_user' <> :'runtime_user'
    AND :'migration_user' <> :'runtime_user' THEN 1 ELSE 0 END;
SELECT 1 / CASE WHEN count(*) = 2 AND bool_and(rolcanlogin AND NOT rolsuper AND NOT rolcreatedb
    AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls)
    THEN 1 ELSE 0 END
    FROM pg_roles WHERE rolname IN (:'migration_user', :'runtime_user');
SELECT 1 / CASE WHEN pg_get_userbyid(datdba) = :'admin_user' THEN 1 ELSE 0 END
    FROM pg_database WHERE datname = current_database();
SELECT 1 / CASE WHEN pg_get_userbyid(nspowner) = :'migration_user' THEN 1 ELSE 0 END
    FROM pg_namespace WHERE nspname = 'aegisflow';
SELECT 1 / CASE WHEN NOT EXISTS (
    SELECT FROM pg_auth_members WHERE member IN
        (SELECT oid FROM pg_roles WHERE rolname IN (:'migration_user', :'runtime_user'))
) THEN 1 ELSE 0 END;
SELECT 1 / CASE WHEN NOT has_schema_privilege(:'runtime_user','aegisflow','CREATE')
    AND NOT has_schema_privilege(:'runtime_user','public','CREATE')
    AND NOT has_table_privilege(:'runtime_user','aegisflow.flyway_schema_history','INSERT,UPDATE,DELETE')
    THEN 1 ELSE 0 END;
SELECT 1 / CASE WHEN count(*) > 0 AND bool_and(success) THEN 1 ELSE 0 END FROM aegisflow.flyway_schema_history;
\endif

\if :snapshot
SELECT jsonb_build_object(
    'sentinel', (SELECT jsonb_agg(to_jsonb(s) ORDER BY fixture_id) FROM p03_rehearsal.sentinel s),
    'flyway', (SELECT jsonb_agg(to_jsonb(h) ORDER BY installed_rank) FROM aegisflow.flyway_schema_history h)
)::text;
\endif
