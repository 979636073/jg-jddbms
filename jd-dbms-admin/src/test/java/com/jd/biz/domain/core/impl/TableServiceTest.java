//package com.jd.biz.domain.core.impl;
//
//import cn.hutool.core.lang.Console;
//import cn.hutool.core.util.StrUtil;
//import cn.hutool.json.JSONUtil;
//import com.alibaba.fastjson2.JSONObject;
//import com.jd.Application;
//import com.jd.biz.aspect.ConnectionInfoHandler;
//import com.jd.biz.controller.operation.log.converter.OperationLogWebConverter;
//import com.jd.biz.controller.operation.log.request.OperationLogQueryRequest;
//import com.jd.biz.controller.rdb.converter.RdbWebConverter;
//import com.jd.biz.controller.rdb.request.DmlTableRequest;
//import com.jd.biz.controller.rdb.request.DmpExportRequest;
//import com.jd.biz.controller.rdb.request.DmpImportRequest;
//import com.jd.biz.controller.rdb.request.TableModifySqlRequest;
//import com.jd.biz.controller.rdb.request.TableRequest;
//import com.jd.biz.domain.api.model.OperationLog;
//import com.jd.biz.domain.api.param.DlExecuteParam;
//import com.jd.biz.domain.api.param.TableQueryParam;
//import com.jd.biz.domain.api.param.TableSelector;
//import com.jd.biz.domain.api.param.operation.OperationLogPageQueryParam;
//import com.jd.biz.domain.api.service.DataSourceService;
//import com.jd.biz.domain.api.service.TableService;
//import com.jd.biz.domain.core.converter.CommandConverter;
//import com.jd.common.config.HzbConfig;
//import com.jd.common.constant.CacheConstants;
//import com.jd.common.core.redis.RedisCache;
//import com.jd.common.tools.base.wrapper.result.ActionResult;
//import com.jd.common.tools.base.wrapper.result.DataResult;
//import com.jd.common.tools.base.wrapper.result.ListResult;
//import com.jd.common.tools.base.wrapper.result.PageResult;
//import com.jd.spi.model.DmpExportVo;
//import com.jd.spi.model.DmpImportVo;
//import com.jd.spi.model.ExecuteResult;
//import com.jd.spi.model.Table;
//import com.jd.spi.model.TableColumn;
//import com.jd.spi.model.TableIndex;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.BeanUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import javax.annotation.Resource;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//public class TableServiceTest {
//
//    @Autowired
//    private DataSourceService dataSourceService;
//    @Autowired
//    private TableService tableService;
//
//    @Autowired
//    private ConnectionInfoHandler connectionInfoHandler;
//
//    /**
//     * 被测对象:HaiFolderReceiveServiceImpl
//     * 被测函数:select();getInfo();
//     * 用例描述:条件查询文件目录;根据文件ID插叙文件夹：查询节点树
//     * 测试类型:逻辑测试;逻辑测试;逻辑测试
//     * 测试输入:{"fileId":116,"parentId":100,"fileName":"文书","orderNum":0,"nodeId":"dd18adcb-cc07-4766-b08d-652ff80aa7ee","children":[]};
//     *        {};
//     *        {"id":157};
//     * 预计输出:{code='000000', message='操作成功', data=1, timeStamp=1733312249396};
//     *        {"code":"000000","message":"操作成功","timeStamp":1733383533904};
//     *        {code='000000', message='操作成功', data=1, timeStamp=1733312249426}
//     *
//     */
//    @Test
//    public void query() {
//        connectionInfoHandler.buildContext(191L, Boolean.FALSE);
//        TableQueryParam queryParam = new TableQueryParam();
//        TableSelector tableSelector = new TableSelector();
//        tableSelector.setColumnList(true);
//        queryParam.setDataSourceId(191L);
//        tableSelector.setIndexList(true);
//        queryParam.setTableName("ACT_HI_BATCH");
//        queryParam.setSchemaName("SYSDBA");
//        DataResult<Table> query = tableService.query(queryParam, tableSelector);
//        Console.log("实际输出: ", JSONUtil.toJsonStr(query));
//    }
//
//
//    @Autowired
//    private TestServiceImpl testService;
//
//    @Autowired
//    private RedisCache redisCache;
//
//    /**
//     * dmp 导出
//     */
//    @Test
//    public void DMP_EXPORT() {
//        DmpExportRequest dmpExportRequest = new DmpExportRequest();
//        List<String> tableNames = new ArrayList<>();
//        tableNames.add("TEST000");
//        dmpExportRequest.setTableList(tableNames);
//        List<String> schemaNames = new ArrayList<>();
//        schemaNames.add("SYSDBA");
//        dmpExportRequest.setSchemaList(schemaNames);
//        dmpExportRequest.setSchemaName("SYSDBA");
//        dmpExportRequest.setDataSourceId(173L);
//        connectionInfoHandler.buildContext(173L, Boolean.FALSE);
//        try {
//            DmpExportVo dmpExportVo = testService.exportDmp(dmpExportRequest);
//            if (Objects.isNull(dmpExportVo) || StrUtil.isEmpty(dmpExportVo.getStatus())) {
//                Console.log("执行失败");
//            }
//            Long userId = null;
//            String key = CacheConstants.EXPORT_DMP_KEY + userId + ":" + dmpExportRequest.getDataSourceId()
//                    + ":" + StrUtil.nullToEmpty(dmpExportRequest.getSchemaName());
//            redisCache.deleteObject(key);
//            System.out.println("实际输出: "+dmpExportVo);
//        } catch (Exception e) {
//            System.out.println("执行失败: "+e.getMessage());
//        }
//    }
//
//
//    @Autowired
//    private TestServiceTWOImpl testServiceTWO;
//
//    /**
//     * dmp 导入
//     */
//    @Test
//    public void DMP_IMPORT() {
//        DmpImportRequest dmpImportRequest = new DmpImportRequest();
//        dmpImportRequest.setFromName("SYSDBA");
//        dmpImportRequest.setToName("SYSDBA");
//        dmpImportRequest.setDataSourceId(173L);
//        dmpImportRequest.setFilePath(HzbConfig.getProfile() + "\\dmp_173__20250219145107.dmp");
//        connectionInfoHandler.buildContext(173L, Boolean.FALSE);
//        try {
//            DmpImportVo dmpImportVo = testServiceTWO.importDmp(dmpImportRequest);
//            if (Objects.isNull(dmpImportVo) || StrUtil.isEmpty(dmpImportVo.getStatus())) {
//                Console.log("执行失败");
//            }
//            Long userId = null;
//            String key = CacheConstants.IMPORT_DMP_KEY + userId + ":" + dmpImportRequest.getDataSourceId()
//                    + ":" + StrUtil.nullToEmpty(dmpImportRequest.getFromName());
//            redisCache.deleteObject(key);
//            System.out.println("实际输出: "+dmpImportVo);
//        } catch (Exception e) {
//            System.out.println("执行失败: "+e.getMessage());
//        }
//    }
//
//    @Autowired
//    private TestThreeImpl testThree;
//
//    @Autowired
//    private TestCreateImpl testCreate;
//    /**
//     * 新增表
//     */
//    @Test
//    public void ADD_TABLE() {
//        String a = "{\n" +
//                "  \"dataSourceId\": 191,\n" +
//                "  \"schemaName\": \"SYSDBA\",\n" +
//                "  \"name\": \"tests\",\n" +
//                "  \"comment\": \"\",\n" +
//                "  \"columnList\": [\n" +
//                "    {\n" +
//                "      \"name\": \"ID\",\n" +
//                "      \"columnType\": \"VARCHAR2\",\n" +
//                "      \"columnSize\": \"50\",\n" +
//                "      \"comment\": \"\",\n" +
//                "      \"nullable\": 1,\n" +
//                "      \"primaryKey\": false,\n" +
//                "      \"isDisable\": false,\n" +
//                "      \"autoIncrement\": false,\n" +
//                "      \"schemaName\": \"SYSDBA\",\n" +
//                "      \"tableName\": \"tests\"\n" +
//                "    }\n" +
//                "  ]\n" +
//                "}";
//        TableRequest tableRequest = JSONObject.parseObject(a, TableRequest.class);
//        connectionInfoHandler.buildContext(191L, Boolean.FALSE);
//        try {
//            ActionResult testCreateTable = testCreate.createTable(tableRequest);
//            System.out.println("实际输出: " + testCreateTable);
//        } catch (Exception e) {
//            System.out.println("执行失败: "+e.getMessage());
//        }
//    }
//
//    @Autowired
//    private TestUpdateImpl testUpdate;
//
//    /**
//     * 修改表
//     */
//    @Test
//    public void UPDATE_TABLE() {
//        String a = "{\n" +
//                "  \"dataSourceId\": 191,\n" +
//                "  \"databaseName\": \"\",\n" +
//                "  \"schemaName\": \"SYSDBA\",\n" +
//                "  \"refresh\": true,\n" +
//                "  \"newTable\": {\n" +
//                "    \"name\": \"TEST\",\n" +
//                "    \"comment\": null,\n" +
//                "    \"schemaName\": \"SYSDBA\",\n" +
//                "    \"columnList\": [\n" +
//                "      {\n" +
//                "        \"oldColumn\": null,\n" +
//                "        \"oldName\": \"ID\",\n" +
//                "        \"name\": \"ID\",\n" +
//                "        \"tableName\": \"TEST\",\n" +
//                "        \"columnType\": \"BIGINT\",\n" +
//                "        \"dataType\": null,\n" +
//                "        \"defaultValue\": null,\n" +
//                "        \"autoIncrement\": false,\n" +
//                "        \"comment\": \"ID\",\n" +
//                "        \"primaryKey\": false,\n" +
//                "        \"primaryKeyName\": null,\n" +
//                "        \"primaryKeyOrder\": 0,\n" +
//                "        \"schemaName\": \"SYSDBA\",\n" +
//                "        \"databaseName\": null,\n" +
//                "        \"columnSize\": 8,\n" +
//                "        \"bufferLength\": null,\n" +
//                "        \"decimalDigits\": 0,\n" +
//                "        \"numPrecRadix\": null,\n" +
//                "        \"sqlDataType\": null,\n" +
//                "        \"sqlDatetimeSub\": null,\n" +
//                "        \"charOctetLength\": null,\n" +
//                "        \"ordinalPosition\": 1,\n" +
//                "        \"nullable\": 1,\n" +
//                "        \"generatedColumn\": null,\n" +
//                "        \"extent\": null,\n" +
//                "        \"editStatus\": \"MODIFY\",\n" +
//                "        \"charSetName\": null,\n" +
//                "        \"collationName\": null,\n" +
//                "        \"value\": null,\n" +
//                "        \"unit\": null,\n" +
//                "        \"sparse\": null,\n" +
//                "        \"defaultConstraintName\": null,\n" +
//                "        \"seed\": null,\n" +
//                "        \"increment\": null,\n" +
//                "        \"key\": \"8051cd4a-0d53-4612-8d9f-89bc596020cc\"\n" +
//                "      }\n" +
//                "    ],\n" +
//                "    \"indexList\": [],\n" +
//                "    \"constraints\": [],\n" +
//                "    \"dbType\": null,\n" +
//                "    \"databaseName\": null,\n" +
//                "    \"type\": \"TABLE\",\n" +
//                "    \"pinned\": false,\n" +
//                "    \"created\": null,\n" +
//                "    \"ddl\": null,\n" +
//                "    \"viewSql\": null,\n" +
//                "    \"engine\": null,\n" +
//                "    \"charset\": null,\n" +
//                "    \"collate\": null,\n" +
//                "    \"incrementValue\": null,\n" +
//                "    \"partition\": null,\n" +
//                "    \"tablespace\": null,\n" +
//                "    \"constraintName\": null,\n" +
//                "    \"tableDetails\": null,\n" +
//                "    \"tableUserRoles\": null,\n" +
//                "    \"querySql\": \"DROP TABLE IF EXISTS \\\"SYSDBA\\\".\\\"TEST\\\";\\nCREATE TABLE \\\"SYSDBA\\\".\\\"TEST\\\"\\r\\n(\\r\\n\\\"ID\\\" BIGINT) STORAGE(ON \\\"MAIN\\\", CLUSTERBTR) ;\"\n" +
//                "  },\n" +
//                "  \"oldTable\": {\n" +
//                "    \"name\": \"TEST\",\n" +
//                "    \"comment\": null,\n" +
//                "    \"schemaName\": \"SYSDBA\",\n" +
//                "    \"columnList\": [\n" +
//                "      {\n" +
//                "        \"oldColumn\": null,\n" +
//                "        \"oldName\": null,\n" +
//                "        \"name\": \"ID\",\n" +
//                "        \"tableName\": \"TEST\",\n" +
//                "        \"columnType\": \"BIGINT\",\n" +
//                "        \"dataType\": null,\n" +
//                "        \"defaultValue\": null,\n" +
//                "        \"autoIncrement\": false,\n" +
//                "        \"comment\": null,\n" +
//                "        \"primaryKey\": false,\n" +
//                "        \"primaryKeyName\": null,\n" +
//                "        \"primaryKeyOrder\": 0,\n" +
//                "        \"schemaName\": \"SYSDBA\",\n" +
//                "        \"databaseName\": null,\n" +
//                "        \"columnSize\": 8,\n" +
//                "        \"bufferLength\": null,\n" +
//                "        \"decimalDigits\": 0,\n" +
//                "        \"numPrecRadix\": null,\n" +
//                "        \"sqlDataType\": null,\n" +
//                "        \"sqlDatetimeSub\": null,\n" +
//                "        \"charOctetLength\": null,\n" +
//                "        \"ordinalPosition\": 1,\n" +
//                "        \"nullable\": 1,\n" +
//                "        \"generatedColumn\": null,\n" +
//                "        \"extent\": null,\n" +
//                "        \"editStatus\": null,\n" +
//                "        \"charSetName\": null,\n" +
//                "        \"collationName\": null,\n" +
//                "        \"value\": null,\n" +
//                "        \"unit\": null,\n" +
//                "        \"sparse\": null,\n" +
//                "        \"defaultConstraintName\": null,\n" +
//                "        \"seed\": null,\n" +
//                "        \"increment\": null\n" +
//                "      }\n" +
//                "    ],\n" +
//                "    \"indexList\": [],\n" +
//                "    \"constraints\": [],\n" +
//                "    \"dbType\": null,\n" +
//                "    \"databaseName\": null,\n" +
//                "    \"type\": \"TABLE\",\n" +
//                "    \"pinned\": false,\n" +
//                "    \"created\": null,\n" +
//                "    \"ddl\": null,\n" +
//                "    \"viewSql\": null,\n" +
//                "    \"engine\": null,\n" +
//                "    \"charset\": null,\n" +
//                "    \"collate\": null,\n" +
//                "    \"incrementValue\": null,\n" +
//                "    \"partition\": null,\n" +
//                "    \"tablespace\": null,\n" +
//                "    \"constraintName\": null,\n" +
//                "    \"tableDetails\": null,\n" +
//                "    \"tableUserRoles\": null,\n" +
//                "    \"querySql\": \"DROP TABLE IF EXISTS \\\"SYSDBA\\\".\\\"TEST\\\";\\nCREATE TABLE \\\"SYSDBA\\\".\\\"TEST\\\"\\r\\n(\\r\\n\\\"ID\\\" BIGINT) STORAGE(ON \\\"MAIN\\\", CLUSTERBTR) ;\"\n" +
//                "  }\n" +
//                "}";
//        TableModifySqlRequest request = JSONObject.parseObject(a, TableModifySqlRequest.class);
//        Table table = rdbWebConverter.tableRequest2param(request.getNewTable());
//        table.setSchemaName(request.getSchemaName());
//        table.setDatabaseName(request.getDatabaseName());
//        for (TableColumn tableColumn : table.getColumnList()) {
//            tableColumn.setSchemaName(request.getSchemaName());
//            tableColumn.setTableName(table.getName());
//            tableColumn.setDatabaseName(request.getDatabaseName());
//        }
//        for (TableIndex tableIndex : table.getIndexList()) {
//            tableIndex.setSchemaName(request.getSchemaName());
//            tableIndex.setTableName(table.getName());
//            tableIndex.setDatabaseName(request.getDatabaseName());
//        }
//        connectionInfoHandler.buildContext(191L, Boolean.FALSE);
//        try {
//            Boolean aBoolean = testUpdate.updateTable(rdbWebConverter.tableRequest2param(request.getOldTable()), table);
//            System.out.println("实际输出: "+aBoolean);
//        } catch (Exception e) {
//            System.out.println("执行失败: "+e.getMessage());
//        }
//    }
//
//
//
//    @Resource
//    private RdbWebConverter rdbWebConverter;
//
//    @Autowired
//    private CommandConverter commandConverter;
//
//    @Autowired
//    private TestFiveImpl table;
//    /**
//     * 查询表
//     */
//    @Test
//    public void SELECT_TABLE() {
//        String a = "{\n" +
//                "  \"dataSourceId\": 191,\n" +
//                "  \"databaseType\": \"DM\",\n" +
//                "  \"hasNextPage\": true,\n" +
//                "  \"pageNo\": 1,\n" +
//                "  \"pageSize\": 100,\n" +
//                "  \"schemaName\": \"SYSDBA\",\n" +
//                "  \"sql\": \"select * from SYSDBA.TEST_FYY\",\n" +
//                "  \"tableName\": \"TEST\",\n" +
//                "  \"total\": 0,\n" +
//                "  \"type\": \"DM\"\n" +
//                "}";
//        DmlTableRequest dmlTableRequest = JSONObject.parseObject(a, DmlTableRequest.class);
//        DlExecuteParam param = rdbWebConverter.request2param(dmlTableRequest);
//        connectionInfoHandler.buildContext(191L, Boolean.FALSE);
//        try {
//            ListResult<ExecuteResult> table = this.table.getTable(param);
//            System.out.println("实际输出: "+JSONUtil.toJsonStr(table));
//        } catch (Exception e) {
//            System.out.println("执行失败: "+e.getMessage());
//        }
//    }
//
//    /**
//     * 删除表
//     */
//    @Test
//    public void DROP_TABLE() {
//        String a = "{\n" +
//                "  \"dataSourceId\": 191,\n" +
//                "  \"schemaName\": \"SYSDBA\",\n" +
//                "  \"tableNames\": [\n" +
//                "    \"TEST\"\n" +
//                "  ]\n" +
//                "}";
//        TableRequest tableRequest = JSONObject.parseObject(a, TableRequest.class);
//        Table table = new Table();
//        BeanUtils.copyProperties(tableRequest, table);
//        table.setName(tableRequest.getTableNames().get(0));
//        connectionInfoHandler.buildContext(191L, Boolean.FALSE);
//        try {
//            Boolean aBoolean = testThree.dropTable(table);
//            System.out.println("实际输出: "+aBoolean);
//        } catch (Exception e) {
//            System.out.println("执行失败: "+e.getMessage());
//        }
//    }
//
//    @Autowired
//    private OperationLogWebConverter operationLogWebConverter;
//
//    @Autowired
//    private TestLogImpl testLog;
//
//    /**
//     * 获取日志
//     */
//    @Test
//    public void GET_INFO_LOG() {
//        String a = "{\n" +
//                "  \"pageNum\": 1,\n" +
//                "  \"pageSize\": 10,\n" +
//                "  \"businessTypes\": [\n" +
//                "    5,6,10\n" +
//                "  ]\n" +
//                "}";
//        OperationLogQueryRequest operationLogQueryRequest = JSONObject.parseObject(a, OperationLogQueryRequest.class);
//        OperationLogPageQueryParam param = operationLogWebConverter.req2param(operationLogQueryRequest);
//        param.setUserId(1L);
//        try {
//            PageResult<OperationLog> result = testLog.getLog(param);
//            System.out.println("实际输出: "+result);
//        } catch (Exception e) {
//            System.out.println("执行失败: "+e.getMessage());
//        }
//    }
//
//}
