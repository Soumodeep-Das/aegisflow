\set ON_ERROR_STOP on
\getenv database_name POSTGRES_DB
\getenv migration_user MIGRATION_DB_USER
\getenv migration_password MIGRATION_DB_PASSWORD
\getenv runtime_user RUNTIME_DB_USER
\getenv runtime_password RUNTIME_DB_PASSWORD

-- Repeatable administration for both fresh and existing volumes. No data is deleted.
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION', :'migration_user')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'migration_user') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION', :'runtime_user')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'runtime_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'migration_user', :'migration_password') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'runtime_user', :'runtime_password') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'database_name') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I, %I', :'database_name', :'migration_user', :'runtime_user') \gexec
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT format('CREATE SCHEMA IF NOT EXISTS aegisflow AUTHORIZATION %I', :'migration_user') \gexec
SELECT format('ALTER SCHEMA aegisflow OWNER TO %I', :'migration_user') \gexec
REVOKE ALL ON SCHEMA aegisflow FROM PUBLIC;
SELECT format('GRANT USAGE ON SCHEMA aegisflow TO %I', :'runtime_user') \gexec
SELECT format('ALTER ROLE %I IN DATABASE %I SET search_path TO aegisflow', :'migration_user', :'database_name') \gexec
SELECT format('ALTER ROLE %I IN DATABASE %I SET search_path TO aegisflow', :'runtime_user', :'database_name') \gexec
