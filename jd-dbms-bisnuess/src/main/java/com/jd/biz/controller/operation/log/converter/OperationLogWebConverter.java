package com.jd.biz.controller.operation.log.converter;

import com.jd.biz.domain.api.model.OperationLog;
import com.jd.biz.domain.api.param.operation.OperationLogCreateParam;
import com.jd.biz.domain.api.param.operation.OperationLogPageQueryParam;
import com.jd.biz.controller.operation.log.request.OperationLogCreateRequest;
import com.jd.biz.controller.operation.log.request.OperationLogQueryRequest;
import com.jd.biz.controller.operation.log.vo.OperationLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * @author moji
 * @version HistoryWebConverter.java, v 0.1 2022年09月25日 16:53 moji Exp $
 * @date 2022/09/25
 */
@Mapper(componentModel = "spring")
public abstract class OperationLogWebConverter {

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract OperationLogCreateParam createReq2param(OperationLogCreateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract OperationLogPageQueryParam req2param(OperationLogQueryRequest request);

    /**
     * 模型转换
     *
     * @param ddlDTO
     * @return
     */
    @Mappings({
        @Mapping(source = "ddl", target = "name"),
        @Mapping(target = "connectable", expression = "java(ddlDTO.getDataSourceName() != null)"),
    })
    public abstract OperationLogVO dto2vo(OperationLog ddlDTO);

    /**
     * 模型转换
     *
     * @param ddlDTOS
     * @return
     */
    public abstract List<OperationLogVO> dto2vo(List<OperationLog> ddlDTOS);
}
