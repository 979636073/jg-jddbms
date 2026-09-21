package com.jd.biz.domain.core.util;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropStatement;
import com.alibaba.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Identifies SQL statements that require explicit confirmation in the SQL console.
 */
public final class SqlSafetyChecker {

    private SqlSafetyChecker() {
    }

    public static List<String> findRisks(String sql, DbType dbType) {
        if (sql == null || sql.trim().isEmpty()) {
            return Collections.emptyList();
        }
        final List<SQLStatement> statements;
        try {
            statements = SQLUtils.parseStatements(sql, dbType);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }

        List<String> risks = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            SQLStatement statement = statements.get(i);
            String prefix = "第" + (i + 1) + "条 SQL：";
            if (statement instanceof SQLDropStatement) {
                risks.add(prefix + "DROP 会删除数据库对象");
            } else if (statement instanceof SQLTruncateStatement) {
                risks.add(prefix + "TRUNCATE 会清空表数据");
            } else if (statement instanceof SQLDeleteStatement
                    && ((SQLDeleteStatement) statement).getWhere() == null) {
                risks.add(prefix + "DELETE 未包含 WHERE 条件");
            } else if (statement instanceof SQLUpdateStatement
                    && ((SQLUpdateStatement) statement).getWhere() == null) {
                risks.add(prefix + "UPDATE 未包含 WHERE 条件");
            }
        }
        return risks;
    }
}
