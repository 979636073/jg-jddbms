-- Restore database monitoring and log management under their existing parent.
-- Preserve menu IDs, child visibility and role assignments. Safe to run again.
UPDATE JDDBMS.SYS_MENU
SET STATUS = '0', VISIBLE = '0'
WHERE MENU_ID = 2 AND PATH = 'operations';
COMMIT;
