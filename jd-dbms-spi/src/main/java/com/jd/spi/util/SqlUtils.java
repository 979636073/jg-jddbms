
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
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            // Iterate through each statement
            for (Statement stmt : statements.getStatements()) {
                list.add(stmt.toString());
            }
        } catch (Exception e) {
            list = SQLParserUtils.splitAndRemoveComment(sql, dbType);
        }
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
