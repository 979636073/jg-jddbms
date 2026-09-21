package com.jd.biz.controller.data.source.converter;

import com.jd.biz.controller.data.source.request.*;
import com.jd.biz.controller.data.source.vo.DataSourceVO;
import com.jd.biz.controller.data.source.vo.DatabaseVO;
import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.ConsoleCloseParam;
import com.jd.biz.domain.api.param.ConsoleConnectParam;
import com.jd.biz.domain.api.param.datasource.DataSourceCreateParam;
import com.jd.biz.domain.api.param.datasource.DataSourcePageQueryParam;
import com.jd.biz.domain.api.param.datasource.DataSourcePreConnectParam;
import com.jd.biz.domain.api.param.datasource.DataSourceUpdateParam;
import com.jd.spi.model.Database;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * @author moji
 * @version DataSourceWebConverter.java, v 0.1 2022年09月23日 16:45 moji Exp $
 * @date 2022/09/23
 */
@Mapper(componentModel = "spring", imports = {DataSourceKindEnum.class})
public abstract class DataSourceWebConverter {

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    @Mappings({
        @Mapping(source = "user", target = "userName"),
        @Mapping(target = "kind", expression = "java(DataSourceKindEnum.PRIVATE.getCode())"),
        @Mapping(target = "id", source = "id")
    })
    public abstract DataSourceCreateParam createReq2param(DataSourceCreateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    @Mappings({
        @Mapping(source = "user", target = "userName")
    })
    public abstract DataSourceUpdateParam updateReq2param(DataSourceUpdateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DataSourcePageQueryParam queryReq2param(DataSourceQueryRequest request);

    /**
     * 模型转换
     *
     * @param dataSource
     * @return
     */
    @Mappings({
        @Mapping(target = "user", source = "userName")
    })
    public abstract DataSourceVO dto2vo(DataSource dataSource);

    /**
     * 模型转换
     *
     * @param dataSources
     * @return
     */
    public abstract List<DataSourceVO> dto2vo(List<DataSource> dataSources);

    /**
     * 模型转换
     *
     * @param databaseDTO
     * @return
     */
    public abstract DatabaseVO databaseDto2vo(Database databaseDTO);

    /**
     * 模型转换
     *
     * @param databaseDTOS
     * @return
     */
    public abstract List<DatabaseVO> databaseDto2vo(List<Database> databaseDTOS);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DataSourcePreConnectParam testRequest2param(DataSourceTestRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract ConsoleConnectParam request2connectParam(ConsoleConnectRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract ConsoleCloseParam request2closeParam(ConsoleCloseRequest request);
}
