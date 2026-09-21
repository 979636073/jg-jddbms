package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.ConstraintInfoRequest;
import com.jd.biz.controller.rdb.request.TableColumnMappingRequest;
import com.jd.biz.controller.rdb.request.TableImportRequest;
import com.jd.biz.controller.rdb.request.TypeQueryRequest;
import com.jd.biz.controller.rdb.vo.ColumnWHVO;
import com.jd.biz.controller.rdb.vo.TableImportVo;
import com.jd.biz.domain.api.param.*;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.spi.model.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 数据源管理服务
 *
 * @author moji
 * @version DataSourceCoreService.java, v 0.1 2022年09月23日 15:22 moji Exp $
 * @date 2022/09/23
 */
public interface TableService {

    /**
     * 查询表信息
     *
     * @param param
     * @return
     */
    DataResult<String> showCreateTable(ShowCreateTableParam param);

    /**
     * 删除表
     *
     * @param param
     * @return
     */
    ActionResult drop(DropParam param);

    /**
     * 创建表结构的样例
     *
     * @param dbType
     * @return
     */
    DataResult<String> createTableExample(String dbType);

    /**
     * 修改表结构的样例
     *
     * @param dbType
     * @return
     */
    DataResult<String> alterTableExample(String dbType);

    /**
     * 查询表信息
     *
     * @param param
     * @return
     */
    DataResult<Table> query(TableQueryParam param, TableSelector selector);

    /**
     * 构建sql
     *
     * @param oldTable
     * @param newTable
     * @return
     */
    ListResult<Sql> buildSql(Table oldTable, Table newTable);

    /**
     * 分页查询表信息
     *
     * @param param
     * @return
     */
    PageResult<Table> pageQuery(TablePageQueryParam param, TableSelector selector);


    /**
     * 查询表信息
     * @param param
     * @return
     */
    ListResult<SimpleTable> queryTables(TablePageQueryParam param);

    /**
     * 查询表包含的字段
     *
     * @param param
     * @return
     */
    List<TableColumn> queryColumns(TableQueryParam param);

    /**
     * 查询表索引
     *
     * @param param
     * @return
     */
    List<TableIndex> queryIndexes(TableQueryParam param);

    /** 只读比较两张表的字段结构 */
    DataResult<Map<String, Object>> compareTables(TableQueryParam source, TableQueryParam target);

    /**
     * 表权限
     * @param request
     * @return
     */
    List<TableRole> queryRoles(TypeQueryRequest request);

    /**
     *
     * @param param
     * @return
     */
    TableMeta queryTableMeta(TypeQueryParam param);

    /**
     * save table vector
     *
     * @param param
     * @return
     */
    ActionResult saveTableVector(TableVectorParam param);

    /**
     * check if table vector saved status
     *
     * @param param
     * @return
     */
    DataResult<Boolean> checkTableVector(TableVectorParam param);

    /**
     * Get dml template sql
     * @param param table query param
     * @return sql
     */
    DataResult<String> copyDmlSql(DmlSqlCopyParam param);

    /**
     * 导入表数据   原始版
     *
     * @param request 表数据导入请求对象
     * @return 表数据导入视图对象
     */
    DataResult<TableImportVo> importTable(TableImportRequest request);


    /**
     * 导入表数据  武汉版
     *
     * @param request 表数据导入请求对象
     * @return 表数据导入视图对象
     */
    DataResult<TableImportVo> importTable1(TableImportRequest request);

    /**
     *
     * @param param
     *
     * @return
     */
    List<Type> queryTypes(TypeQueryParam param);


    AssociationTree queryReferenced(TypeQueryRequest request);
    /**
     * 查看表被引用情况
     * @param request
     * @return
     */
    AssociationTree queryReferencedList(TypeQueryRequest request);

    /**
     * 查看表被引用情况
     * @param request
     * @return
     */
    AssociationTree queryViewReferenced(TypeQueryRequest request);

    /**
     * 删除约束
     * @param request
     * @throws SQLException
     */
    void dropConstraint(TypeQueryRequest request, Connection connection) throws SQLException;

    /**
     * 创建约束
     * @param request
     */
    void createTableConstraint(ConstraintInfoRequest request) throws SQLException;

    List<TableColumn> getTableKeyDataList(TableQueryParam tableQueryParam);

     boolean excelDataSave(Table table,String insertSql);

    String columnDefault(TypeQueryRequest request);

    List<ColumnWHVO> columnMapping(TableColumnMappingRequest request);
}
