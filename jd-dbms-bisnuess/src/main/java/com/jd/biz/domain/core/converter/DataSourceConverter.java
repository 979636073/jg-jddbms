package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.ConsoleConnectParam;
import com.jd.biz.domain.api.param.ConsoleCreateParam;
import com.jd.biz.domain.api.param.datasource.*;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.biz.domain.core.util.DataSourceCipher;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @author moji
 * @version DataSourceCoreConverter.java, v 0.1 2022年09月23日 15:53 moji Exp $
 * @date 2022/09/23
 */
@Mapper(componentModel = "spring")
public abstract class DataSourceConverter {

    @Resource
    @Lazy
    private DataSourceService dataSourceService;

    @Resource
    private DataSourceCipher dataSourceCipher;

    /**
     * 参数转换
     *
     * @param param
     * @return
     */

    @Mapping(target = "password", expression = "java(encryptString(param))")
    @Mapping(target = "ssh",
        expression = "java(param.getSsh()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param.getSsh()))")
    @Mapping(target = "ssl",
        expression = "java(param.getSsl()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param.getSsl()))")
    @Mapping(target = "extendInfo",
        expression = "java(param.getExtendInfo()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param"
            + ".getExtendInfo()))")
    @Mapping(target = "driverConfig",
        expression = "java(param.getDriverConfig()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param"
            + ".getDriverConfig()))")
    @Mapping(target = "id", source = "id")
    public abstract DataSourceDO param2do(DataSourceCreateParam param);

    /**
     * encrypt
     *
     * @param param
     * @return
     */
    protected String encryptString(DataSourceCreateParam param) {
        return dataSourceCipher.encrypt(param.getPassword());
    }

    /**
     * encrypt
     *
     * @param param
     * @return
     */
    protected String encryptString(DataSourceUpdateParam param) {
        return StringUtils.isBlank(param.getPassword()) ? null : dataSourceCipher.encrypt(param.getPassword());
    }

    /**
     * decrypt
     *
     * @param param
     * @return
     */
    protected String decryptString(DataSourceDO param) {
        return dataSourceCipher.decrypt(param.getPassword());
    }

    /**
     * 参数转换
     *
     * @param param
     * @return
     */
    @Mappings({
        @Mapping(target = "password", expression = "java(encryptString(param))"),
        @Mapping(target = "ssh",
            expression = "java(param.getSsh()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param.getSsh()))"),
        @Mapping(target = "ssl",
            expression = "java(param.getSsl()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param.getSsl()))"),
        @Mapping(target = "extendInfo",
            expression = "java(param.getExtendInfo()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param"
                + ".getExtendInfo()))"),
        @Mapping(target = "driverConfig",
            expression = "java(param.getDriverConfig()==null?null: com.alibaba.fastjson2.JSON.toJSONString(param"
                + ".getDriverConfig()))")
    })
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract DataSourceDO param2do(DataSourceUpdateParam param);

    /**
     * 参数转换
     *
     * @param param
     * @return
     */
    public abstract ConsoleCreateParam param2consoleParam(ConsoleConnectParam param);

    /**
     * 参数转换
     *
     * @param dataSourcePreConnectParam
     * @return
     */
    @Mappings({
        @Mapping(source = "type", target = "dbType"),
        @Mapping(source = "user", target = "username")
    })
    public abstract DataSourceTestParam param2param(
        DataSourcePreConnectParam dataSourcePreConnectParam);

    /**
     * 模型转换
     *
     * @param dataSourceDO
     * @return
     */

    @Mapping(target = "password", expression = "java(decryptString(dataSourceDO))")
    @Mapping(target = "ssh",
        expression = "java(com.alibaba.fastjson2.JSON.parseObject(dataSourceDO.getSsh(),com.jd.spi"
            + ".model.SSHInfo.class))")
    @Mapping(target = "ssl",
        expression =
            "java(com.alibaba.fastjson2.JSON.parseObject(dataSourceDO.getSsl(),com.jd.spi"
                + ".model.SSLInfo"
                + ".class))")
    @Mapping(target = "driverConfig",
        expression =
            "java(com.alibaba.fastjson2.JSON.parseObject(dataSourceDO.getDriverConfig(),com.jd.spi.config"
                + ".DriverConfig"
                + ".class))")
    @Mapping(target = "extendInfo",
        expression = "java(com.alibaba.fastjson2.JSON.parseArray(dataSourceDO.getExtendInfo(),com.jd.spi.model"
            + ".KeyValue.class))")
    @Mapping(target = "environment.id", source = "environmentId")
    public abstract DataSource do2dto(DataSourceDO dataSourceDO);

    /**
     * 模型转换
     *
     * @param dataSourceDOList
     * @return
     */
    public abstract List<DataSource> do2dto(List<DataSourceDO> dataSourceDOList);

    /**
     * Fill in detailed information
     *
     * @param list
     */
    public void fillDetail(List<DataSource> list) {
        fillDetail(list, null);
    }

    /**
     * Fill in detailed information
     *
     * @param list
     */
    public void fillDetail(List<DataSource> list, DataSourceSelector selector) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        List<Long> idList = EasyCollectionUtils.toList(list, DataSource::getId);
        List<DataSource> queryList = dataSourceService.listQuery(idList, selector).getData();
        Map<Long, DataSource> queryMap = EasyCollectionUtils.toIdentityMap(queryList, DataSource::getId);
        for (DataSource data : list) {
            if (data == null || data.getId() == null) {
                continue;
            }
            DataSource query = queryMap.get(data.getId());
            add(data, query);
        }
    }

    @Mappings({
        @Mapping(target = "id", ignore = true),
    })
    public abstract void add(@MappingTarget DataSource target, DataSource source);
}
