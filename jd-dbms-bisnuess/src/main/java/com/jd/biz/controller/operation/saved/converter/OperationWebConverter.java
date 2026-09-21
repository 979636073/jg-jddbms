package com.jd.biz.controller.operation.saved.converter;

import com.jd.biz.domain.api.model.Operation;
import com.jd.biz.domain.api.param.operation.OperationPageQueryParam;
import com.jd.biz.domain.api.param.operation.OperationSavedParam;
import com.jd.biz.domain.api.param.operation.OperationUpdateParam;
import com.jd.biz.controller.operation.saved.request.OperationCreateRequest;
import com.jd.biz.controller.operation.saved.request.OperationQueryRequest;
import com.jd.biz.controller.operation.saved.request.OperationUpdateRequest;
import com.jd.biz.controller.operation.saved.vo.OperationVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * @author moji
 * @version DdlManageWebConverter.java, v 0.1 2022年09月26日 10:08 moji Exp $
 * @date 2022/09/26
 */
@Mapper(componentModel = "spring")
public abstract class OperationWebConverter {

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract OperationSavedParam req2param(OperationCreateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract OperationUpdateParam updateReq2param(OperationUpdateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract OperationPageQueryParam queryReq2param(OperationQueryRequest request, Long userId);

    /**
     * 模型转换
     *
     * @param ddlDTO
     * @return
     */
    @Mappings({
        @Mapping(target = "connectable", expression = "java(ddlDTO.getDataSourceName() != null)"),
    })
    public abstract OperationVO dto2vo(Operation ddlDTO);

    /**
     * 模型转换
     *
     * @param ddlDTOS
     * @return
     */
    public abstract List<OperationVO> dto2vo(List<Operation> ddlDTOS);
}
