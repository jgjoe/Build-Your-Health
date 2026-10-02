-- Runs once as SYS on the CDB (FREE) during the first container initialization.
-- The measurement harness reads V$SQL and DBMS_XPLAN.DISPLAY_CURSOR as BYH_PERF (next unit).
ALTER SESSION SET CONTAINER = FREEPDB1;

GRANT SELECT_CATALOG_ROLE TO BYH_PERF;
