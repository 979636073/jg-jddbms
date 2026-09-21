package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.model.Operation;
import com.jd.biz.domain.api.param.operation.OperationSavedParam;
import com.jd.biz.domain.api.param.operation.OperationUpdateParam;
import com.jd.biz.domain.repository.entity.OperationSavedDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * @author moji
 * @version UserSavedDdlCoreConverter.java, v 0.1 2022年09月25日 15:50 moji Exp $
 * @date 2022/09/25
 */
@Mapper(componentModel = "spring")
public abstract class OperationConverter {

    /**
     * 参数转换
     *
     * @param param
     * @return
     */
    @Mappings({
        @Mapping(source = "schemaName", target = "dbSchemaName")
    })
    public abstract OperationSavedDO param2do(OperationSavedParam param);

    /**
     * 参数转换
     *
     * @param param
     * @return
     */
    @Mappings({
        @Mapping(source = "schemaName", target = "dbSchemaName")
    })
    public abstract OperationSavedDO param2do(OperationUpdateParam param);

    /**
     * 模型转换
     *
     * @param userSavedDdlDO
     * @return
     */
    @Mappings({
        @Mapping(source = "dbSchemaName", target = "schemaName")
    })
    public abstract Operation do2dto(OperationSavedDO userSavedDdlDO);

    /**
     * 模型转换
     *
     * @param userSavedDdlDOS
     * @return
     */
    public abstract List<Operation> do2dto(List<OperationSavedDO> userSavedDdlDOS);
}
