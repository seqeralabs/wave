-- =============================================================================
-- Wave PostgreSQL schema migration: public -> dedicated schema
-- =============================================================================
--
-- PURPOSE
--   Move Wave's legacy public.wave_* tables into a dedicated schema.
--   ALTER TABLE ... SET SCHEMA is metadata-only; table data is not rewritten.
--
-- USAGE
--   psql "postgresql://<admin-user>@<host>:5432/wave" \
--     -v target_schema=wave \
--     -v owner_role=wave \
--     -f migrations/2026-07-move-wave-schema.sql
--
-- REQUIRED PRIVILEGES
--   Run as a role that can create schemas and alter the legacy public.wave_*
--   tables. On managed PostgreSQL, this is typically the DB owner/admin.
--
-- IDEMPOTENCY
--   Re-running is safe after the tables have moved: ALTER TABLE IF EXISTS
--   public.<table> becomes a no-op once the public table is gone.
--   If both public.<table> and <target_schema>.<table> exist, the script fails
--   before moving anything. Do not merge by hand; inspect which table has real
--   data and remove only confirmed empty shadow tables.
--
-- DOWNTIME / SEARCH PATH
--   Moving tables into a schema that does not match the currently running app
--   role can break old app connections whose search_path is "$user", public.
--   For live compatibility, use a target_schema matching the app role. Otherwise
--   run this during the deploy window and start the new build with WAVE_DB_SCHEMA
--   set to the same target_schema.
--
-- ROLLBACK
--   BEGIN;
--   ALTER TABLE IF EXISTS :"target_schema".wave_build SET SCHEMA public;
--   ALTER TABLE IF EXISTS :"target_schema".wave_request SET SCHEMA public;
--   ALTER TABLE IF EXISTS :"target_schema".wave_mirror SET SCHEMA public;
--   ALTER TABLE IF EXISTS :"target_schema".wave_scan SET SCHEMA public;
--   COMMIT;
--
-- =============================================================================

\if :{?target_schema}
\else
\set target_schema wave
\endif

\if :{?owner_role}
\else
\set owner_role wave
\endif

\set ON_ERROR_STOP on

SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM pg_tables public_tables
    JOIN pg_tables target_tables
      ON target_tables.tablename = public_tables.tablename
    WHERE public_tables.schemaname = 'public'
      AND target_tables.schemaname = :'target_schema'
      AND public_tables.tablename IN ('wave_build', 'wave_request', 'wave_mirror', 'wave_scan')
) THEN 'true' ELSE 'false' END AS has_target_collision
\gset

\if :has_target_collision
\echo 'Refusing to migrate: at least one wave_* table exists in both public and target schema.'
\echo 'Inspect target tables before retrying; drop only confirmed empty shadow tables.'
\quit 1
\endif

BEGIN;

CREATE SCHEMA IF NOT EXISTS :"target_schema" AUTHORIZATION :"owner_role";
ALTER SCHEMA :"target_schema" OWNER TO :"owner_role";

ALTER TABLE IF EXISTS public.wave_build SET SCHEMA :"target_schema";
ALTER TABLE IF EXISTS public.wave_request SET SCHEMA :"target_schema";
ALTER TABLE IF EXISTS public.wave_mirror SET SCHEMA :"target_schema";
ALTER TABLE IF EXISTS public.wave_scan SET SCHEMA :"target_schema";

ALTER TABLE IF EXISTS :"target_schema".wave_build OWNER TO :"owner_role";
ALTER TABLE IF EXISTS :"target_schema".wave_request OWNER TO :"owner_role";
ALTER TABLE IF EXISTS :"target_schema".wave_mirror OWNER TO :"owner_role";
ALTER TABLE IF EXISTS :"target_schema".wave_scan OWNER TO :"owner_role";

SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename IN ('wave_build', 'wave_request', 'wave_mirror', 'wave_scan')
) THEN 'true' ELSE 'false' END AS has_public_leftovers
\gset

\if :has_public_leftovers
\echo 'Migration incomplete: one or more wave_* tables remain in public.'
\quit 1
\endif

COMMIT;
