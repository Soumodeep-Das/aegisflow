REVOKE ALL ON DATABASE warehouse_p02 FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE ROLE aegisflow_warehouse_migrator
    LOGIN PASSWORD 'synthetic-migrator-test-only';
CREATE ROLE aegisflow_warehouse_runtime
    LOGIN PASSWORD 'synthetic-runtime-test-only';

GRANT CONNECT ON DATABASE warehouse_p02 TO aegisflow_warehouse_migrator;
GRANT CONNECT ON DATABASE warehouse_p02 TO aegisflow_warehouse_runtime;

CREATE SCHEMA aegisflow AUTHORIZATION aegisflow_warehouse_migrator;

ALTER ROLE aegisflow_warehouse_migrator IN DATABASE warehouse_p02
    SET search_path = aegisflow;
ALTER ROLE aegisflow_warehouse_runtime IN DATABASE warehouse_p02
    SET search_path = aegisflow;
