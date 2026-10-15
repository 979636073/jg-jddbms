-- 仅在 JQDMO_260926 / JQORO_260926 专用验收模式执行，不涉及业务表。
-- 第一组：建立专用表并提交基础记录。
CREATE TABLE JD_QA_SQL_0928 (ID NUMBER PRIMARY KEY, NOTE VARCHAR2(50));
INSERT INTO JD_QA_SQL_0928 VALUES (1, 'baseline');
-- 使用控制台“提交”按钮。

-- 第二组：错误不应回滚已有事务，随后使用“回滚”按钮。
UPDATE JD_QA_SQL_0928 SET NOTE = 'pending' WHERE ID = 1;
SELECT * FROM JD_QA_SQL_MISSING_0928;
SELECT NOTE FROM JD_QA_SQL_0928 WHERE ID = 1;
-- 回滚后再次查询应为 baseline。

-- 第三组：使用“提交”按钮并在新会话核对。
UPDATE JD_QA_SQL_0928 SET NOTE = 'committed' WHERE ID = 1;
-- 提交后再次查询应为 committed。

-- 完成后仅删除本轮专用测试表。
DROP TABLE JD_QA_SQL_0928;
