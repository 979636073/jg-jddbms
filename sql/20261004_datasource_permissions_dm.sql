-- DM metadata database: run once before deploying the permission-aware server.
-- Existing shared connections receive read-only access by default.
ALTER TABLE JDDBMS.DATA_SOURCE_ACCESS ADD PERMISSION_CODES VARCHAR(255) DEFAULT 'METADATA_READ,DATA_READ';
UPDATE JDDBMS.DATA_SOURCE_ACCESS
SET PERMISSION_CODES = 'METADATA_READ,DATA_READ'
WHERE PERMISSION_CODES IS NULL;
COMMIT;
