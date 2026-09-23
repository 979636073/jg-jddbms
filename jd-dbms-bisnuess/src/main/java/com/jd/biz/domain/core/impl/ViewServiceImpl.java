package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.parser.ParserException;
import com.jd.biz.controller.rdb.enums.ResultType;
import com.jd.biz.controller.rdb.request.ViewNewRequest;
import com.jd.biz.controller.rdb.request.ViewQueryRequest;
import com.jd.biz.controller.rdb.request.ViewRequest;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.ViewService;
import com.jd.biz.domain.core.util.MetaNameUtils;
import com.jd.biz.util.KFSqlParser;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.utils.StringUtils;
import com.jd.spi.DBManage;
import com.jd.spi.MetaData;
import com.jd.spi.ValueHandler;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Header;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.JdbcUtils;
import com.jd.spi.util.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class ViewServiceImpl implements ViewService {

    @Autowired
    private DlTemplateService service;

    @Override
    public ListResult<Table> views(String databaseName, String schemaName, Integer requestType) {
        List<Table> views = Chat2DBContext.getMetaData().views(Chat2DBContext.getConnection(), databaseName, schemaName);
        if (ResultType.TWO.getType().equals(requestType)) {
            views = Chat2DBContext.getMetaData().tableViews(Chat2DBContext.getConnection(), databaseName, schemaName);
        }
        return ListResult.of(views);
    }

    @Override
    public DataResult<Table> detail(String databaseName, String schemaName, String tableName) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        Table table = metaSchema.view(Chat2DBContext.getConnection(), databaseName, schemaName, tableName);
        return DataResult.of(table);
    }

    @Override
    public DataResult<Sql> showSql(ViewRequest request) {
        if (StrUtil.isEmpty(request.getTableName())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "请先输入视图名");
        }
        if (StrUtil.isEmpty(request.getViewSql())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "请先输入SQL");
        }
        MetaData metaSchema = Chat2DBContext.getMetaData();
        String sql = metaSchema.showViewSql(request.getDatabaseName(), request.getSchemaName(), request.getTableName(), request.getViewSql(), request.getColumnList());
        return DataResult.of(new Sql(sql));
    }

    /**
     * 获取指定视图的创建SQL
     *
     * @param request 视图请求对象
     * @return
     */
    @Override
    public DataResult<Sql> getViewSql(ViewRequest request) {
        Table table = detail(request.getDatabaseName(), request.getSchemaName(), request.getTableName()).getData();
        if (table == null || StrUtil.isBlank(table.getDdl())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "未获取到视图 DDL");
        }
        return DataResult.of(new Sql(table.getDdl()));
    }

    @Override
    public ListResult<TableColumn> queryColumns(TableQueryParam param) {
        if (StrUtil.isEmpty(param.getTableName())) {
            return ListResult.error(EasyToolsConstant.ERROR_CODE, "请先输入视图名");
        }
        if (StrUtil.isEmpty(param.getViewSql())) {
            return ListResult.error(EasyToolsConstant.ERROR_CODE, "请先输入SQL");
        }
        String type = Chat2DBContext.getConnectInfo().getDbType();
        DbType dbType = JdbcUtils.parse2DruidDbType(type);
        List<String> sqlList = SqlUtils.parse(param.getViewSql(), dbType);
        if (CollectionUtils.isEmpty(sqlList) || sqlList.size() > 1) {
            return ListResult.error(EasyToolsConstant.ERROR_CODE, "sql解析错误");
        }
        SQLStatement sqlStatement = null;
        try {
            sqlStatement = SQLUtils.parseSingleStatement(sqlList.get(0), dbType);
        } catch (ParserException e) {
            return ListResult.error(EasyToolsConstant.ERROR_CODE, "sql解析错误");
        }
        if (!(sqlStatement instanceof SQLSelectStatement)) {
            return ListResult.error(EasyToolsConstant.ERROR_CODE, "sql不是Select语句");
        }
        int pageNo = 1;
        int pageSize = 1;
        int offset = (pageNo - 1) * pageSize;
        int count = pageSize;
        String pageLimit = Chat2DBContext.getSqlBuilder().pageLimit(sqlList.get(0), offset, pageNo, pageSize);
        ExecuteResult executeResult;
        try {
            ValueHandler valueHandler = Chat2DBContext.getMetaData().getValueHandler();
            executeResult = SQLExecutor.getInstance().execute(pageLimit, Chat2DBContext.getConnection(), true, offset, count,
                    valueHandler);
        } catch (SQLException e) {
            log.warn("Execute sql: {} exception", pageLimit, e);
            return ListResult.error(EasyToolsConstant.ERROR_CODE, "sql解析错误" + e.getMessage());
        }
        final List<Header> headerList = executeResult.getHeaderList();
        MetaData metaSchema = Chat2DBContext.getMetaData();
        final List<TableColumn> columns = metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        List<TableColumn> columnList = Lists.newArrayList();
        for (Header header : headerList) {
            TableColumn tableColumn = new TableColumn();
            tableColumn.setName(header.getName());
            if (CollUtil.isNotEmpty(columns)) {
                Optional<TableColumn> column = columns.stream().filter(c -> c.getName().equals(header.getName())).findFirst();
                if (column.isPresent()) {
                    tableColumn.setComment(column.get().getComment());
                }
            }
            columnList.add(tableColumn);
        }
        return ListResult.of(columnList);
    }

    @Override
    public ListResult<ExecuteResult> execute(ViewRequest request) {
//        if (StrUtil.isEmpty(request.getSchemaName())) {
//            return ListResult.error(EasyToolsConstant.ERROR_CODE, "请先输入模式");
//        }
//        if (StrUtil.isEmpty(request.getTableName())) {
//            return ListResult.error(EasyToolsConstant.ERROR_CODE, "请先输入视图名");
//        }
        if (StrUtil.isEmpty(request.getViewSql())) {
            return ListResult.error(EasyToolsConstant.ERROR_CODE, "请输入ViewSql");
        }
        String replace = request.getViewSql().replace("&gt;", ">");
        replace = replace.replace("&lt;", "<");
        request.setViewSql(replace);
        ListResult<ExecuteResult> listResult = new ListResult<>();
        listResult.setSuccess(true);
        List<ExecuteResult> executeResults = new ArrayList<>();

        String createViewSql = request.getViewSql();
//        String createViewSql = getCreateViewSql(request.getSchemaName(), request.getTableName(), request.getViewSql());
//        if (StringUtils.isNotEmpty(createViewSql)) {
            ExecuteResult executeResult = new ExecuteResult();
//            ExecuteResult executeResult = ExecuteResult.builder().sql(createViewSql).success(Boolean.TRUE).build();//编译视图
            try (Statement statement = Chat2DBContext.getConnection().createStatement()) {
                statement.execute(createViewSql);
                executeResult.setSuccess(Boolean.TRUE);
                executeResults.add(executeResult);
            } catch (SQLException e) {
                String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                executeResult.setSuccess(false);
                executeResult.setMessage(message);
                executeResults.add(executeResult);
                log.warn("Execute sql: {} exception", createViewSql, e);
                listResult.setSuccess(false);
                listResult.setErrorMessage(message);
            }

//        }
        listResult.setData(executeResults);
        return listResult;
    }

    @Override
    public void dropSelect(ViewNewRequest request) {
        try{
            List<String> viewNames = request.getViewNames();
            Map<String, String> viewTypes = new HashMap<>();
            getViewList(request.getDatabaseName(), request.getSchemaName()).forEach(view -> {
                if (view.getTableDetails() != null) {
                    viewTypes.put(view.getName(), view.getTableDetails().getType());
                }
            });
            for (String viewName : viewNames) {
                ViewRequest viewRequest = new ViewRequest();
                viewRequest.setDataSourceId(request.getDataSourceId());
                viewRequest.setTableName(viewName);
                viewRequest.setSchemaName(request.getSchemaName());
                viewRequest.setDatabaseName(request.getDatabaseName());
                viewRequest.setViewType(viewTypes.get(viewName));
                dropByType(viewRequest, viewRequest.getViewType());
            }
        }catch (Exception e){
            throw new RuntimeException("视图删除出错: " + e.getMessage(), e);
        }
    }

    @Override
    public void drop(ViewRequest request) {
        try{
            dropByType(request, resolveViewType(request));
        }catch (Exception e){
            throw new RuntimeException("视图删除出错: " + e.getMessage(), e);
        }

    }

    private void dropByType(ViewRequest request, String viewType) throws Exception {
        if (isMaterializedView(viewType)) {
            executeStatement(buildDropMaterializedViewSql(request.getSchemaName(), request.getTableName()));
            return;
        }
        DBManage metaSchema = Chat2DBContext.getDBManage();
        metaSchema.dropView(Chat2DBContext.getConnection(), request.getDatabaseName(), request.getSchemaName(), request.getTableName());
    }

    @Override
    public void refreshMaterialized(ViewRequest request) {
        String viewType = resolveViewType(request);
        if (!isMaterializedView(viewType)) {
            throw new BusinessException("指定对象不是物化视图");
        }
        executeStatement(buildRefreshMaterializedViewSql(
                Chat2DBContext.getConnectInfo().getDbType(), request.getSchemaName(), request.getTableName()));
    }

    private String resolveViewType(ViewRequest request) {
        String metadataType = getViewList(request.getDatabaseName(), request.getSchemaName()).stream()
                .filter(view -> request.getTableName().equals(view.getName()))
                .filter(view -> view.getTableDetails() != null)
                .map(view -> view.getTableDetails().getType())
                .findFirst()
                .orElse(null);
        return StrUtil.isNotBlank(metadataType) ? metadataType : request.getViewType();
    }

    private void executeStatement(String sql) {
        try (Statement statement = Chat2DBContext.getConnection().createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private static boolean isMaterializedView(String viewType) {
        return "MATERIALIZED VIEW".equalsIgnoreCase(viewType);
    }

    static String buildDropMaterializedViewSql(String schemaName, String viewName) {
        return "DROP MATERIALIZED VIEW " + qualifiedName(schemaName, viewName);
    }

    static String buildRefreshMaterializedViewSql(String dbType, String schemaName, String viewName) {
        String qualifiedName = qualifiedName(schemaName, viewName);
        if ("DM".equalsIgnoreCase(dbType)) {
            return "REFRESH MATERIALIZED VIEW " + qualifiedName;
        }
        if ("ORACLE".equalsIgnoreCase(dbType)) {
            return "BEGIN DBMS_MVIEW.REFRESH('" + qualifiedName.replace("'", "''") + "', 'C'); END;";
        }
        throw new BusinessException("当前数据库不支持物化视图刷新");
    }

    private static String qualifiedName(String schemaName, String viewName) {
        return MetaNameUtils.quoteIdentifier(schemaName) + "." + MetaNameUtils.quoteIdentifier(viewName);
    }

    /**
     * 获取模式下的所有视图信息
     *
     * @param databaseName
     * @param schemaName
     * @return
     */
    public List<Table> getViewList(String databaseName, String schemaName) {
        List<Table> views = Chat2DBContext.getMetaData().tableViews(Chat2DBContext.getConnection(), databaseName, schemaName);
        return views;
    }

    @Override
    public ExecuteResult allExecute(ViewRequest request) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        Table table = metaSchema.view(Chat2DBContext.getConnection(), request.getDatabaseName(), request.getSchemaName(), request.getTableName());
        String type = Chat2DBContext.getConnectInfo().getDbType();
        DbType dbType = JdbcUtils.parse2DruidDbType(type);

        String sql = getViewSql(table);

        ExecuteResult executeResult = new ExecuteResult();
        if (StrUtil.isEmpty(sql)) {
            executeResult.setSuccess(false);
            executeResult.setTableName(request.getTableName());
            executeResult.setMessage(request.getTableName() + "编译失败:" + sql);
        } else {
            List<String> sqlList = SqlUtils.parse(sql, dbType);
            Connection connection = Chat2DBContext.getConnection();
            for (String originalSql : sqlList) {
                executeResult = ExecuteResult.builder().sql(originalSql).success(Boolean.TRUE).build();
                try (Statement statement = connection.createStatement()) {
                    statement.execute(originalSql);
                    executeResult.setSuccess(true);
                    executeResult.setTableName(request.getTableName());
                    executeResult.setMessage(request.getTableName() + "：编译成功");
                } catch (SQLException e) {
                    String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    executeResult.setSuccess(false);
                    executeResult.setTableName(request.getTableName());
                    executeResult.setMessage(request.getTableName() + "编译失败:" + message);
                }
            }
        }
        return executeResult;
    }

    /**
     * 重新获取视图创建sql[增加指定的例名]
     *
     * @param table
     * @return
     */
    private String getViewSql(Table table) {
        if (StringUtils.isNotEmpty(table.getName())) {
            StringBuffer stringBuffer = new StringBuffer(" CREATE OR REPLACE VIEW ");
            stringBuffer.append(MetaNameUtils.quoteIdentifier(table.getSchemaName())).append(".");
            stringBuffer.append(MetaNameUtils.quoteIdentifier(table.getName()));
            if (CollUtil.isNotEmpty(table.getColumnList())) {
                stringBuffer.append(getColumnString(table.getColumnList()));
            }
            stringBuffer.append(" AS ");
            stringBuffer.append(table.getViewSql());
            return stringBuffer.toString();
        } else {
            return "";
        }
    }

    /**
     * 循环列名【封装视图表的列名】
     *
     * @param tableColumnList
     * @return
     */
    private String getColumnString(List<TableColumn> tableColumnList) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(" ( ");
        for (TableColumn tableColumn : tableColumnList) {
            stringBuffer.append(MetaNameUtils.quoteIdentifier(tableColumn.getName())).append(",");
        }
        String sqlString = stringBuffer.toString();
        sqlString = sqlString.substring(0, sqlString.length() - 1);
        stringBuffer = new StringBuffer(sqlString).append(" ) ");
        return stringBuffer.toString();
    }

    /**
     * 重新获取视图创建sql[增加指定的例名]
     *
     * @param schemaName,tableName,viewSql
     * @return
     */
    private String getCreateViewSql(String schemaName, String tableName, String viewSql) {
        StringBuffer stringBuffer = new StringBuffer(" CREATE OR REPLACE VIEW ");
        stringBuffer.append(MetaNameUtils.quoteIdentifier(schemaName)).append(".");
        stringBuffer.append(MetaNameUtils.quoteIdentifier(tableName));
        stringBuffer.append(" AS ");
        stringBuffer.append(viewSql);
        return stringBuffer.toString();
    }

    @Override
    public ExecuteResult getExecuteSQL(ViewRequest request) {
        net.sf.jsqlparser.statement.Statement statement = null;
        DataResult<Sql> dataResult = new DataResult<>();
        ExecuteResult executeResult = new ExecuteResult();
        //定义返回的数据
        StringBuilder sqlText = new StringBuilder();
        try {
            Select parse = (Select) CCJSqlParserUtil.parse(request.getViewSql());
            SelectBody selectBody = parse.getSelectBody();
            PlainSelect select = (PlainSelect) selectBody;
            //定义sql语句的查询字段变量
            String sqlZd = "";
            List<SelectItem> selectItem = select.getSelectItems();
            if (selectItem.size() > 0) {
                for (int i = 0; i < selectItem.size(); i++) {
                    if (selectItem.size() > 1 && i < selectItem.size() - 1) {
                        if (i == 0) {
                            sqlZd = selectItem.get(i).toString() + ',';
                        } else {
                            sqlZd += selectItem.get(i).toString() + ',';
                        }
                    } else {
                        sqlZd += selectItem.get(i).toString();
                    }
                }
                ;
            }
            //定义sql语句的查询表名变量
            String tableName = null;
            try {
                tableName = select.getFromItem().toString();
            } catch (Exception e) {
                executeResult.setSuccess(false);
                executeResult.setMessage("Sql语句不规范，未发现表名称");
                return executeResult;
            }

            Expression wheres = select.getWhere();
            Expression leftExpression = null;
            Expression rightExpression = null;
            if (wheres != null) {
                Class<? extends Expression> aClass = wheres.getClass();

                if (aClass.toString().indexOf("AndExpression") > 0) {
                    AndExpression where = (AndExpression) select.getWhere();
                    if (where != null) {
                        leftExpression = where.getLeftExpression();
                        rightExpression = where.getRightExpression();
                    }
                }
                if (aClass.toString().indexOf("EqualsTo") > 0) {
                    EqualsTo where = (EqualsTo) select.getWhere();
                    if (where != null) {
                        leftExpression = where.getLeftExpression();
                        rightExpression = where.getRightExpression();
                    }
                }
            }


            sqlText.append("SQL Text\n" +
                    "       └─语句类型: Select\n" +
                    "           ├─selectItems -> Collection\n" +
                    "           │  └─查询字段:" + sqlZd + "\n"
            );
            if (wheres == null) {
                sqlText.append("           └─Table:" + tableName + " \n");
            } else {
                sqlText.append("           ├─Table:" + tableName + " \n");
            }
            //判断关联条件拼接
            List<Join> joins = select.getJoins();
            if (StringUtils.isNotEmpty(joins)) {
                for (int i = 0; i < joins.size(); i++) {
                    if (i < joins.size() - 1) {
                        sqlText.append("           ├─LEFT JOIN:" + joins.get(i).toString() + " \n");
                    } else if (i == joins.size() - 1) {
                        if (wheres == null) {
                            sqlText.append("           └─LEFT JOIN:" + joins.get(i).toString() + " \n");
                        } else {
                            sqlText.append("           ├─LEFT JOIN:" + joins.get(i).toString() + " \n");
                        }
                    }
                }
                ;
            }
            //判断where条件拼接
            if (wheres != null) {
                if (StringUtils.isNoneBlank(leftExpression.toString())) {
                    sqlText.append("           └─where: net.sf.jsqlparser.expression.operators.relational.EqualsTo\n");
                    sqlText.append("              ├─Column:" + leftExpression.toString() + "\n");
                }
                if (StringUtils.isNoneBlank(rightExpression.toString())) {
                    sqlText.append("              └─Column:" + rightExpression.toString() + "\n");
                }
            }
        } catch (JSQLParserException e) {
            dataResult.setSuccess(false);
            e.printStackTrace();
        }
        executeResult.setSuccess(true);
        executeResult.setMessage(String.valueOf(sqlText));
        return executeResult;
    }

    @Override
    public Boolean updateViewTableName(ViewQueryRequest request) {
        if (StrUtil.isBlank(request.getNewViewName()) || StrUtil.isBlank(request.getOldViewName()) || StrUtil.isBlank(request.getSchemaName())) {
            throw new BusinessException("参数缺失");
        }
        if (request.getOldViewName().equals(request.getNewViewName())) {
            return Boolean.TRUE;
        }
        ViewRequest viewRequest = new ViewRequest();
        viewRequest.setDataSourceId(request.getDataSourceId());
        viewRequest.setTableName(request.getOldViewName());
        viewRequest.setSchemaName(request.getSchemaName());
        viewRequest.setDatabaseName("");
        try {
            DataResult<Table> detail = detail("", request.getSchemaName(), request.getOldViewName());
            if (!DataResult.hasData(detail) || StrUtil.isBlank(detail.getData().getViewSql())) {
                throw new BusinessException("未获取到原视图定义");
            }
            Table original = detail.getData();
            StringBuilder createSql = new StringBuilder("CREATE VIEW ")
                    .append(qualifiedName(request.getSchemaName(), request.getNewViewName()));
            if (CollUtil.isNotEmpty(original.getColumnList())) {
                createSql.append(getColumnString(original.getColumnList()));
            }
            createSql.append(" AS ").append(original.getViewSql());
            viewRequest.setViewSql(createSql.toString());
            ListResult<ExecuteResult> execute = execute(viewRequest);
            if (execute == null || !execute.getSuccess()) {
                throw new BusinessException("创建新视图失败: " + (execute == null ? "无执行结果" : execute.getErrorMessage()));
            }
            viewRequest.setViewSql(null);
            try {
                drop(viewRequest);
            } catch (RuntimeException e) {
                viewRequest.setTableName(request.getNewViewName());
                try {
                    drop(viewRequest);
                } catch (RuntimeException cleanupError) {
                    log.error("清理新视图失败: {}", request.getNewViewName(), cleanupError);
                }
                throw e;
            }
            return Boolean.TRUE;
        } catch (BusinessException e) {
            log.error("修改视图名失败,{}", e.getMessage());
            return Boolean.FALSE;
        } catch (RuntimeException e) {
            log.error("修改视图名失败", e);
            return Boolean.FALSE;
        }
    }


    /**
     * 代码不要轻易改动
     * @param request
     * @return
     */
    @Override
    public Boolean updateViewColumnName(ViewQueryRequest request) {
        ViewRequest viewRequest = new ViewRequest();
        if (StrUtil.isBlank(request.getOldViewName()) || StrUtil.isBlank(request.getSchemaName())
                || CollUtil.isEmpty(request.getNewColumns()) || CollUtil.isEmpty(request.getOldColumns())) {
            throw new BusinessException("参数缺失");
        }
        List<String> oldColumns = request.getOldColumns();
        List<String> newColumns = request.getNewColumns();
        if (oldColumns.size() != newColumns.size()) {
            throw new BusinessException("新旧类字段信息数量不一致");
        }
        String ddl = "";
        try {
            viewRequest.setDataSourceId(request.getDataSourceId());
            viewRequest.setTableName(request.getOldViewName());
            viewRequest.setSchemaName(request.getSchemaName());
            viewRequest.setDatabaseName("");
            DataResult<Table> detail = detail("", request.getSchemaName(), request.getOldViewName());
            if (!DataResult.hasData(detail) || StrUtil.isBlank(detail.getData().getDdl())) {
                throw new BusinessException("未获取到原视图定义");
            }
            ddl = detail.getData().getDdl();
            String sql = ddl;
            String s = KFSqlParser.extractBetweenSelectAndFrom(sql);
            if (StrUtil.isEmpty(s)) {
                throw new BusinessException("视图表字段信息解析异常");
            }
            String where = "";
            // 截取 WHERE之前的SQL
            if (sql.toUpperCase(Locale.ROOT).contains("WHERE")) {
                int i = sql.toUpperCase(Locale.ROOT).indexOf("WHERE");
                where = sql.substring(i);
                sql = sql.substring(0, i);
            }
            String[] arrays = s.split(",");
            for (int i = 0; i < oldColumns.size(); i++) {
                if (!oldColumns.get(i).equals(newColumns.get(i))) {
                    StringBuilder oldValue = new StringBuilder();
                    StringBuilder value = new StringBuilder("AS \"" + newColumns.get(i) + "\"");
                    for (String array : arrays) {
                        if (array.contains(oldColumns.get(i))) {
                            if (array.contains("AS")) {
                                oldValue.append("AS  ").append("\"").append(oldColumns.get(i)).append("\"");
                            } else {
                                value.insert(0, array + " ");
                                oldValue.append(array);
                            }
                        }
                    }
                    sql = sql.replace(oldValue.toString(), value.toString());
                }
            }
            if (StrUtil.isNotBlank(where)) {
                sql = sql + " " + where;
            }
            viewRequest.setViewSql(sql);
            ListResult<ExecuteResult> execute = execute(viewRequest);
            if (execute == null || !execute.getSuccess()) {
                throw new BusinessException("修改视图列名失败");
            }
            return Boolean.TRUE;
        } catch (BusinessException e) {
            if (StrUtil.isNotBlank(ddl)) {
                viewRequest.setViewSql(ddl);
                execute(viewRequest);
            }
            log.error("修改视图列信息失败,{}", e.getMessage());
            return Boolean.FALSE;
        } catch (RuntimeException e) {
            log.error("修改视图列信息失败", e);
            return Boolean.FALSE;
        }
    }

}
