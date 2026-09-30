
package com.jd.spi.util;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.model.ExecuteResult;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.*;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jipengfei
 * @version : SqlUtils.java
 */
public class SqlUtils {

    public static final String DEFAULT_TABLE_NAME = "table1";

    public static void buildCanEditResult(String sql, DbType dbType, ExecuteResult executeResult) {
        try {
            Statement statement;
            if (DbType.sqlserver.equals(dbType)) {
                statement = CCJSqlParserUtil.parse(sql, ccjSqlParser -> ccjSqlParser.withSquareBracketQuotation(true));
            } else {
                statement = CCJSqlParserUtil.parse(sql);
            }
            if (statement instanceof Select) {
                Select select = (Select) statement;
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                if (plainSelect.getJoins() == null && plainSelect.getFromItem() != null) {
                    for (SelectItem item : plainSelect.getSelectItems()) {
                        if (item instanceof SelectExpressionItem) {
                            SelectExpressionItem expressionItem = (SelectExpressionItem) item;
                            if (expressionItem.getAlias() != null) {
                                //canEdit = false; // 找到了一个别名
                                executeResult.setCanEdit(false);
                                return;
                            }
                            if (item instanceof SelectExpressionItem) {
                                SelectExpressionItem selectExpressionItem = (SelectExpressionItem) item;
                                // if the expression is a function
                                if (selectExpressionItem.getExpression() instanceof Function) {
                                    Function function = (Function) selectExpressionItem.getExpression();
                                    // Check if the function is "COUNT"
                                    if ("COUNT".equalsIgnoreCase(function.getName())) {
                                        executeResult.setCanEdit(false);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    executeResult.setCanEdit(true);
                    SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
                    if ((sqlStatement instanceof SQLSelectStatement)) {
                        SQLSelectStatement sqlSelectStatement = (SQLSelectStatement) sqlStatement;
                        SQLExprTableSource sqlExprTableSource = (SQLExprTableSource) getSQLExprTableSource(
                                sqlSelectStatement.getSelect().getFirstQueryBlock().getFrom());
                        executeResult.setTableName(getMetaDataTableName(sqlExprTableSource.getCatalog(), sqlExprTableSource.getSchema(), sqlExprTableSource.getTableName()));
                    }
                } else {
                    executeResult.setCanEdit(false);
                }
            }
        } catch (Exception e) {
            executeResult.setCanEdit(false);
        }
    }

    private static String getMetaDataTableName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> name).collect(Collectors.joining("."));
    }

    public static String formatSQLString(Object para) {
        return para != null ? " '" + para + "' " : null;
    }

    public static String getTableName(String sql, DbType dbType) {
        SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
        if (!(sqlStatement instanceof SQLSelectStatement)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        SQLSelectStatement sqlSelectStatement = (SQLSelectStatement) sqlStatement;
        SQLExprTableSource sqlExprTableSource = (SQLExprTableSource) getSQLExprTableSource(
                sqlSelectStatement.getSelect().getFirstQueryBlock().getFrom());
        if (sqlExprTableSource == null) {
            return DEFAULT_TABLE_NAME;
        }
        return sqlExprTableSource.getTableName();
    }

    private static SQLTableSource getSQLExprTableSource(SQLTableSource sqlTableSource) {
        if (sqlTableSource instanceof SQLExprTableSource) {
            SQLExprTableSource sqlExprTableSource = (SQLExprTableSource) sqlTableSource;
            return sqlExprTableSource;
        } else if (sqlTableSource instanceof SQLJoinTableSource) {
            SQLJoinTableSource sqlJoinTableSource = (SQLJoinTableSource) sqlTableSource;
            return getSQLExprTableSource(sqlJoinTableSource.getLeft());
        }
        return null;
    }

    public static List<String> parse(String sql, DbType dbType) {
        List<String> list = new ArrayList<>();
        if (dbType != DbType.oracle && dbType != DbType.dm) {
            try {
                Statements statements = CCJSqlParserUtil.parseStatements(sql);
                for (Statement statement : statements.getStatements()) list.add(statement.toString());
                return list;
            } catch (Exception e) {
                return SQLParserUtils.splitAndRemoveComment(sql, dbType);
            }
        }
        int start = 0;
        char quote = 0, qEnd = 0;
        boolean lineComment = false, blockComment = false, procedural = false, afterEnd = false, ended = false;
        int depth = 0;
        List<String> words = new ArrayList<>();
        boolean oracleDialect = dbType == DbType.oracle || dbType == DbType.dm;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i), next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (lineComment) { if (ch == '\n') lineComment = false; continue; }
            if (blockComment) { if (ch == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (qEnd != 0) { if (ch == qEnd && next == '\'') { qEnd = 0; i++; } continue; }
            if (quote != 0) {
                if (ch == quote) { if (next == quote) i++; else quote = 0; }
                else if (!oracleDialect && ch == '\\') i++;
                continue;
            }
            if (ch == '-' && next == '-') { lineComment = true; i++; continue; }
            if (ch == '/' && next == '*') { blockComment = true; i++; continue; }
            if (oracleDialect && (ch == 'q' || ch == 'Q') && next == '\'' && i + 2 < sql.length()) {
                char delimiter = sql.charAt(i + 2);
                qEnd = delimiter == '[' ? ']' : delimiter == '(' ? ')' : delimiter == '{' ? '}' : delimiter == '<' ? '>' : delimiter;
                i += 2; continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') { quote = ch; continue; }
            if (Character.isLetter(ch) || ch == '_') {
                int wordStart = i;
                while (i + 1 < sql.length() && (Character.isLetterOrDigit(sql.charAt(i + 1)) || "_$#".indexOf(sql.charAt(i + 1)) >= 0)) i++;
                String word = sql.substring(wordStart, i + 1).toUpperCase(java.util.Locale.ROOT);
                if (words.size() < 8) words.add(word);
                if ("BEGIN".equals(words.get(0)) || "DECLARE".equals(words.get(0)) ||
                        ("CREATE".equals(words.get(0)) && words.size() <= 6 && Arrays.asList("PROCEDURE", "FUNCTION", "TRIGGER", "PACKAGE", "BODY").contains(word))) {
                    if (!procedural && ("PACKAGE".equals(word) || "BODY".equals(word))) depth++;
                    procedural = true;
                }
                if (procedural) {
                    if ("END".equals(word)) { depth = Math.max(0, depth - 1); afterEnd = true; ended = depth == 0; }
                    else if (afterEnd && Arrays.asList("IF", "LOOP", "CASE").contains(word)) afterEnd = false;
                    else {
                        afterEnd = false;
                        if (Arrays.asList("BEGIN", "IF", "LOOP", "CASE").contains(word)) { depth++; ended = false; }
                    }
                }
                continue;
            }
            // A slash on its own line is a client delimiter, not JDBC SQL.
            if (oracleDialect && ch == '/' && sql.substring(sql.lastIndexOf('\n', i - 1) + 1, i).trim().isEmpty()
                    && (i + 1 == sql.length() || sql.substring(i + 1).matches("^[ \\t\\r]*(?:\\n[\\s\\S]*|$)"))) {
                if (!words.isEmpty()) list.add(sql.substring(start, i).trim());
                start = i + 1; words.clear(); depth = 0; afterEnd = false; procedural = false; ended = false;
                continue;
            }
            if (ch == ';' && (!procedural || (depth == 0 && ended))) {
                String statement = sql.substring(start, procedural ? i + 1 : i).trim();
                if (!words.isEmpty()) list.add(statement);
                start = i + 1; words.clear(); depth = 0; afterEnd = false; procedural = false; ended = false;
            }
        }
        if (!words.isEmpty() && StringUtils.isNotBlank(sql.substring(start))) list.add(sql.substring(start).trim());
        return list;
    }

    private static final String DEFAULT_VALUE = "CHAT2DB_DEFAULT_VALUE";

    public static String getSqlValue(String value, String dataType) {
        if (value == null) {
            return null;
        }
        if (DEFAULT_VALUE.equals(value)) {
            return "DEFAULT";
        }
        DataTypeEnum dataTypeEnum = DataTypeEnum.getByCode(dataType);
        if (value.isEmpty() && dataTypeEnum != DataTypeEnum.STRING
                && dataTypeEnum != DataTypeEnum.CONTENT
                && dataTypeEnum != DataTypeEnum.DOCUMENT) {
            return null;
        }
        return dataTypeEnum.getSqlValue(value);
    }

    public static boolean hasPageLimit(String sql, DbType dbType) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                SelectBody selectBody = selectStatement.getSelectBody();
                // 检查常见的分页方法
                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    // 检查 LIMIT
                    if (plainSelect.getLimit() != null || plainSelect.getOffset() != null || plainSelect.getTop() != null || plainSelect.getFetch() != null) {
                        return true;
                    }
                    if (DbType.oracle.equals(dbType)) {
                        return sql.contains("ROWNUM") || sql.contains("rownum");
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * 校验SQL是否正确
     *
     * @param sql
     * @return
     */
    public static boolean isSQLValid(String sql) {
        try {
            // 尝试解析SQL语句
            Statement statement = CCJSqlParserUtil.parse(sql);
            // 如果没有异常抛出，说明SQL语句是正确的
            return true;
        } catch (Exception e) {
            // 如果抛出异常，说明SQL语句有错误
            return false;
        }
    }

}
