package com.jd.biz.controller.rdb.converter;

import com.jd.biz.domain.api.param.*;
import com.jd.biz.controller.data.source.vo.DatabaseVO;
import com.jd.biz.controller.rdb.request.*;
import com.jd.biz.controller.rdb.vo.*;
import com.jd.biz.http.request.EsTableSchemaRequest;
import com.jd.spi.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * @author moji
 * @version MysqlDataConverter.java, v 0.1 2022年10月14日 14:04 moji Exp $
 * @date 2022/10/14
 */
@Mapper(componentModel = "spring")
public abstract class RdbWebConverter {

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DlExecuteParam request2param(DmlRequest request);


    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract OrderByParam request2param(OrderByRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DlExecuteParam request2param(DmlTableRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DlExecuteParam tableManageRequest2param(DdlRequest request);


    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DlCountParam request2param(DdlCountRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract TableQueryParam tableRequest2param(TableDetailQueryRequest request);


    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract Table tableRequest2param(TableRequest request);

    /**
     * 参数转换
     *
     * @param dto
     * @return
     */
    public abstract SqlVO dto2vo(Sql dto);
    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract TablePageQueryParam tablePageRequest2param(TableBriefQueryRequest request);
    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract TablePageQueryParam tablePageRequest2param(DataExportRequest request);
    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract TableQueryParam tableRequest2param(DataExportRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract ShowCreateTableParam ddlExport2showCreate(DdlExportRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    @Mappings({
            @Mapping(source = "schemaName", target = "tableSchema")
    })
    public abstract DropParam tableDelete2dropParam(TableDeleteRequest request);


    /**
     * 模型转换
     *
     * @param dto
     * @return
     */
    public abstract ExecuteResultVO dto2vo(ExecuteResult dto);

    /**
     * 模型转换
     *
     * @param dtos
     * @return
     */
    public abstract List<ExecuteResultVO> dto2vo(List<ExecuteResult> dtos);

    /**
     * 模型转换
     *
     * @param dto
     * @return
     */
    public abstract ColumnVO columnDto2vo(TableColumn dto);

    /**
     * 模型转换
     *
     * @param dtos
     * @return
     */
    public abstract List<ColumnVO> columnDto2vo(List<TableColumn> dtos);

    /**
     * 模型转换
     *
     * @param dto
     * @return
     */
    @Mappings({
        @Mapping(source = "columnList", target = "columnList")
    })
    public abstract IndexVO indexDto2vo(TableIndex dto);

    /**
     * 模型转换
     *
     * @param dtos
     * @return
     */
    public abstract List<IndexVO> indexDto2vo(List<TableIndex> dtos);

    /**
     * 模型转换
     *
     * @param dto
     * @return
     */
    @Mappings({
        @Mapping(source = "columnList", target = "columnList"),
        @Mapping(source = "indexList", target = "indexList"),
        @Mapping(source = "tableUserRoles", target = "tableUserRoles"),
    })
    public abstract TableVO tableDto2vo(Table dto);

    /**
     * 模型转换
     *
     * @param dtos
     * @return
     */
    public abstract List<TableVO> tableDto2vo(List<Table> dtos);

    /**
     * 模型转换
     * @param tableColumns
     * @return
     */
    public abstract List<SchemaVO> schemaDto2vo(List<Schema> tableColumns);

    /**
     * 模型转换
     * @param dto
     * @return
     */
    public abstract SchemaVO schemaDto2vo(Schema dto);

    /**
     * 模型转换
     * @param dto
     * @return
     */
    public abstract DatabaseVO databaseDto2vo(Database dto);


    /**
     * 模型转换
     * @param dto
     * @return
     */
    public abstract List<DatabaseVO> databaseDto2vo(List<Database> dto);

    public abstract MetaSchemaVO metaSchemaDto2vo(MetaSchema data);


    public abstract TableMilvusQueryRequest request2request(TableBriefQueryRequest request);

    @Mappings({
            @Mapping(source = "isView", target = "isView"),
    })
    public abstract UpdateSelectResultParam request2param(SelectResultUpdateRequest request);

    @Mappings({
            @Mapping(source = "databaseName", target = "database"),
            @Mapping(source = "schemaName", target = "schema"),
    })
    public abstract TableVectorParam param2param(TableBriefQueryRequest request);

    public abstract EsTableSchemaRequest req2req(TableBriefQueryRequest request);

    public abstract TablePageQueryParam schemaReq2page(EsTableSchemaRequest request);

    public abstract DmlSqlCopyParam dmlRequest2param(DmlSqlCopyRequest request) ;
}
