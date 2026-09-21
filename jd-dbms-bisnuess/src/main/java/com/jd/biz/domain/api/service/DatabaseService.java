package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.DatabaseExportRequest;
import com.jd.biz.controller.rdb.request.DatabaseImportRequest;
import com.jd.biz.controller.rdb.request.DmpExportRequest;
import com.jd.biz.controller.rdb.request.DmpImportRequest;
import com.jd.biz.controller.rdb.vo.DatabaseExportVo;
import com.jd.biz.controller.rdb.vo.DatabaseImportVo;
import com.jd.spi.model.DmpExportVo;
import com.jd.spi.model.DmpImportVo;
import com.jd.biz.domain.api.param.*;
import com.jd.biz.domain.api.param.datasource.DatabaseCreateParam;
import com.jd.biz.domain.api.param.datasource.DatabaseExportParam;
import com.jd.biz.domain.api.param.datasource.DatabaseQueryAllParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.Database;
import com.jd.spi.model.MetaSchema;
import com.jd.spi.model.Schema;
import com.jd.spi.model.Sql;

import java.sql.SQLException;
import java.util.List;

/**
 * 数据源管理服务
 *
 * @author moji
 * @version DataSourceCoreService.java, v 0.1 2022年09月23日 15:22 moji Exp $
 * @date 2022/09/23
 */
public interface DatabaseService {

    /**
     * 查询数据源下的所有database
     *
     * @param param
     * @return
     */
    ListResult<Database> queryAll(DatabaseQueryAllParam param);

    /**
     * 查询某个database下的schema
     *
     * @param param
     * @return
     */
    ListResult<Schema> querySchema(SchemaQueryParam param);

    /**
     * query Database and Schema
     *
     * @param param
     * @return
     */
    DataResult<MetaSchema> queryDatabaseSchema(MetaDataQueryParam param);


    /**
     * 删除数据库
     *
     * @param param
     * @return
     */
    ActionResult deleteDatabase(DatabaseCreateParam param);

    /**
     * 创建database
     *
     * @param param
     * @return
     */
    DataResult<Sql> createDatabase(Database param);

    /**
     * 修改database
     *
     * @return
     */
    ActionResult modifyDatabase(DatabaseCreateParam param);

    /**
     * 删除schema
     *
     * @param param
     * @return
     */
    ActionResult deleteSchema(SchemaOperationParam param);

    /**
     * 创建schema
     *
     * @param schema
     * @return
     */
    DataResult<Sql> createSchema(Schema schema);

    /**
     * 修改schema
     *
     * @param request
     * @return
     */
    ActionResult modifySchema(SchemaOperationParam request);


    String exportDatabase(DatabaseExportParam param) throws SQLException;

    /**
     * 导出表
     *
     * @param request
     * @return
     */
    DatabaseExportVo export2(DatabaseExportRequest request) throws SQLException;

    /**
     * 导入表
     *
     * @param request
     * @return
     */
    DatabaseImportVo importDatabase(DatabaseImportRequest request) throws Exception;


    /**
     * 导出dmp文件
     * @param dmpExportRequest
     */
    DmpExportVo exportDmp(DmpExportRequest dmpExportRequest);



    /**
     * 导入dmp文件
     * @param dmpImportRequest
     */
    DmpImportVo importDmp(DmpImportRequest dmpImportRequest);

    /**
     * 批量导入模式数据
     * @param dmpImportRequest
     * @return
     */
    DmpImportVo importDmpList(DmpImportRequest dmpImportRequest) throws InterruptedException;

    /**
     * 取消正在执行的 DMP 导入。
     */
    ActionResult cancelDmpImport(Long taskId);

}
