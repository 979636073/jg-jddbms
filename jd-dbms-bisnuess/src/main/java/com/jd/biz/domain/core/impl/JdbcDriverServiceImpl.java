package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import com.jd.biz.domain.api.service.JdbcDriverService;
import com.jd.biz.domain.core.converter.DriverConfigConverter;
import com.jd.biz.domain.repository.entity.JdbcDriverDO;
import com.jd.biz.domain.repository.mapper.JdbcDriverMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.config.DBConfig;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.IDriverManager;
import com.jd.spi.util.JdbcJarUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class JdbcDriverServiceImpl implements JdbcDriverService {

    @Autowired
    private DriverConfigConverter driverConfigConverter;
    @Resource
    private JdbcDriverMapper jdbcDriverMapper;

    @Override
    public DataResult<DBConfig> getDrivers(String dbType) {
        Map<String, DriverConfig> driverConfigMap = new LinkedHashMap<>();
        LambdaQueryWrapper<JdbcDriverDO> query = new LambdaQueryWrapper<JdbcDriverDO>();
        query.eq(JdbcDriverDO::getDbType, dbType);
        List<JdbcDriverDO> driverDOS = jdbcDriverMapper.selectList(query);
        List<DriverConfig> driverConfigs = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(driverDOS)) {
            driverConfigs = driverDOS.stream().map(driverConfigConverter::do2Config).collect(Collectors.toList());
        }

        DBConfig dbConfig = Chat2DBContext.PLUGIN_MAP.get(dbType).getDBConfig();
        List<DriverConfig> driverConfigList = dbConfig.getDriverConfigList();
        if (CollectionUtils.isNotEmpty(driverConfigList)) {
            driverConfigs.addAll(driverConfigList);
        }

        for (DriverConfig driverConfig : driverConfigs) {
            boolean flag = driverExists(driverConfig);
            if (flag && driverConfigMap.get(driverConfig.getJdbcDriver()) == null) {
                driverConfigMap.put(driverConfig.getJdbcDriver(), driverConfig);
            } else {
                log.warn("Driver file not found: {}", driverConfig.getJdbcDriver());
            }
        }
        dbConfig.setDriverConfigList(driverConfigMap.isEmpty() ? null : Lists.newArrayList(driverConfigMap.values()));
        return DataResult.of(dbConfig);
    }


    private boolean driverExists(DriverConfig driverConfig) {
        boolean flag = true;
        String[] jarPaths = driverConfig.getJdbcDriver().split(",");
        for (String jarPath : jarPaths) {
            File file = new File(JdbcJarUtils.PATH + jarPath);
            if (!file.exists()) {
                flag = false;
                break;
            }
        }
        return flag;
    }

    @Override
    public ActionResult upload(String dbType, String jdbcDriverClass, String localPath) {
        JdbcDriverDO driverDO = new JdbcDriverDO();
        driverDO.setJdbcDriverClass(jdbcDriverClass);
        driverDO.setDbType(dbType);
        driverDO.setJdbcDriver(localPath);
        DriverConfig driverConfig = driverConfigConverter.do2Config(driverDO);
        try {
            IDriverManager.getClassLoader(driverConfig);
        } catch (Exception e) {
            throw new RuntimeException("Driver error,please check the driver file", e);
        }
        jdbcDriverMapper.insert(driverDO);
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult download(String dbType) {
        DBConfig dbConfig = Chat2DBContext.PLUGIN_MAP.get(dbType).getDBConfig();
        List<DriverConfig> driverConfigList = dbConfig.getDriverConfigList();
        for (DriverConfig driverConfig : driverConfigList) {
            List<String> downloadJdbcDriverUrls = driverConfig.getDownloadJdbcDriverUrls();
            for (String downloadJdbcDriverUrl : downloadJdbcDriverUrls) {
                try {
                    JdbcJarUtils.download(downloadJdbcDriverUrl);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return ActionResult.isSuccess();
    }
}
