-- Core's business inbox/projection/incident tables belong to later milestones.
-- This history proves an independently managed database boundary now.
COMMENT ON SCHEMA aegisflow IS 'AegisFlow Core only; no simulator authority';
GRANT USAGE ON SCHEMA aegisflow TO "${runtimeRole}";
-- Flyway history is not runtime-writable, and no default broad table grant is made.
