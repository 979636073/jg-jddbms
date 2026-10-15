-- DM / Oracle 复杂查询与批量结果验收（不修改业务数据）
WITH items AS (
  SELECT 1 id, 'A' label FROM dual UNION ALL SELECT 2, 'B' FROM dual
)
SELECT i.id, i.label, ROW_NUMBER() OVER (ORDER BY i.id) rn,
       CASE WHEN i.id = 1 THEN 'a;b' ELSE 'it''s b' END txt
FROM items i LEFT JOIN (SELECT 1 id FROM dual) j ON i.id = j.id
WHERE i.id >= 1 ORDER BY i.id;

SELECT /*+ NO_MERGE */ q'[text;it's fine]' txt, NVL(NULL, 'fallback') value FROM dual;

SELECT department, SUM(amount) amount FROM (
  SELECT 'A' department, 1 amount FROM dual
  UNION ALL SELECT 'A', 2 FROM dual
  UNION ALL SELECT 'B', 3 FROM dual
) t GROUP BY department HAVING SUM(amount) >= 3 ORDER BY department;

SELECT LEVEL n FROM dual CONNECT BY LEVEL <= 405;

DECLARE n NUMBER := 1;
BEGIN
  IF n = 1 THEN
    BEGIN n := n + 1; END;
  END IF;
  FOR i IN 1..2 LOOP n := n + i; END LOOP;
END;
/
SELECT 99 after_block FROM dual;
