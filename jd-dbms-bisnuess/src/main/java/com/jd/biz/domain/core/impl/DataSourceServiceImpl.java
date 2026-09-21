package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.jd.biz.controller.data.source.vo.DataSourceConnectVo;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.datasource.*;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DatabaseService;
import com.jd.biz.domain.core.converter.DataSourceConverter;
import com.jd.biz.domain.core.converter.EnvironmentConverter;
import com.jd.biz.domain.core.util.PermissionUtils;
import com.jd.biz.domain.repository.entity.DataSourceAccessDO;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import com.jd.biz.domain.repository.entity.HistoryUserLogDO;
import com.jd.biz.domain.repository.mapper.DataSourceAccessMapper;
import com.jd.biz.domain.repository.mapper.DataSourceCustomMapper;
import com.jd.biz.domain.repository.mapper.DataSourceMapper;
import com.jd.biz.domain.repository.mapper.HistoryUserLogMapper;
import com.jd.common.core.domain.model.LoginUser;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.exception.DataNotFoundException;
import com.jd.common.tools.common.exception.ParamBusinessException;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import com.jd.common.tools.common.util.EasyEnumUtils;
import com.jd.common.tools.common.util.EasySqlUtils;
import com.jd.common.utils.SecurityUtil;
import com.jd.spi.config.DBConfig;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.DataSourceConnect;
import com.jd.spi.model.Database;
import com.jd.spi.model.KeyValue;
import com.jd.spi.model.SSHInfo;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.IDriverManager;
import com.jd.spi.util.JdbcUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Connection;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Slf4j
@Service
public class DataSourceServiceImpl extends ServiceImpl<DataSourceMapper, DataSourceDO> implements DataSourceService   {

    @Resource
    private DataSourceConverter dataSourceConverter;

    @Autowired
    private DatabaseService databaseService;

    @Resource
    private DataSourceCustomMapper dataSourceCustomMapper;
    @Resource
    private EnvironmentConverter environmentConverter;
    @Resource
    private DataSourceAccessMapper dataSourceAccessMapper;
    @Resource
    private HistoryUserLogMapper historyUserLogMapper;
    @Resource
    private DataSourceAccessBusinessService dataSourceAccessBusinessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataResult<DataSourceConnectVo> createWithPermission(DataSourceCreateParam param) {
        LambdaQueryWrapper<DataSourceDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DataSourceDO::getHost, param.getHost())
                .eq(DataSourceDO::getPort, param.getPort())
                .eq(DataSourceDO::getUserName, param.getUserName())
                .eq(DataSourceDO::getType, param.getType());
        if (DataSourceKindEnum.PRIVATE.getCode().equals(param.getKind())) {
            lambdaQueryWrapper.eq(DataSourceDO::getUserId, ContextUtils.getUserId());
        }
        param.setId(null);
        if (DBTypeEnum.DM.name().equals(param.getType())) {
            List<DataSourceDO> dataSourceDOS = getBaseMapper().selectList(lambdaQueryWrapper);
            if (CollUtil.isNotEmpty(dataSourceDOS)) {
                param.setId(dataSourceDOS.get(0).getId());
            }
        } else if (DBTypeEnum.ORACLE.name().equals(param.getType())) {
            if (StrUtil.isNotEmpty(param.getSid())) {
                lambdaQueryWrapper.eq(DataSourceDO::getSid, param.getSid());
            }
            if (StrUtil.isNotEmpty(param.getServiceName())) {
                lambdaQueryWrapper.eq(DataSourceDO::getServiceName, param.getServiceName());
            }
            List<DataSourceDO> dataSourceDOS = getBaseMapper().selectList(lambdaQueryWrapper);
            if (CollUtil.isNotEmpty(dataSourceDOS)) {
                param.setId(dataSourceDOS.get(0).getId());
            }
        }
        DataSourceKindEnum dataSourceKind = EasyEnumUtils.getEnum(DataSourceKindEnum.class, param.getKind());
        if (dataSourceKind == null) {
            throw new ParamBusinessException("kind");
        }
        if (dataSourceKind == DataSourceKindEnum.SHARED && !ContextUtils.getLoginUser().getAdmin()) {
            throw new PermissionDeniedBusinessException();
        }
        JdbcUtils.removePropertySameAsDefault(param.getDriverConfig());
        DataSourceDO dataSourceDO = dataSourceConverter.param2do(param);
        if (DBTypeEnum.ORACLE.name().equals(param.getType()) && StringUtils.isNotBlank(dataSourceDO.getSid())) {
            dataSourceDO.setServiceType("0");
            dataSourceDO.setServiceName("");
        } else if (DBTypeEnum.ORACLE.name().equals(param.getType()) && StringUtils.isBlank(dataSourceDO.getSid())) {
            dataSourceDO.setServiceType("1");
            dataSourceDO.setSid("");
        }
        dataSourceDO.setPasswordCopy(param.getPassword());
        dataSourceDO.setIsSave(param.getChecked());
        dataSourceDO.setGmtCreate(DateUtil.date());
        dataSourceDO.setGmtModified(DateUtil.date());
        dataSourceDO.setUserId(ContextUtils.getUserId());
        dataSourceDO.setAlias("@" + dataSourceDO.getHost());
        if (Objects.isNull(dataSourceDO.getId())) {
            this.save(dataSourceDO);
        } else {
            this.updateById(dataSourceDO);
        }
    LambdaQueryWrapper<HistoryUserLogDO> logDOLambdaQueryWrapper = new LambdaQueryWrapper<>();
        logDOLambdaQueryWrapper.eq(HistoryUserLogDO::getUserId, dataSourceDO.getUserId());
        logDOLambdaQueryWrapper.eq(HistoryUserLogDO::getDataSourceId, dataSourceDO.getId());
        List<HistoryUserLogDO> historyUserLogDOS = historyUserLogMapper.selectList(logDOLambdaQueryWrapper);
        if (CollUtil.isEmpty(historyUserLogDOS)) {
            HistoryUserLogDO historyUserLogDO = new HistoryUserLogDO();
            Date date = new Date();
            historyUserLogDO.setGmtCreate(date);
            historyUserLogDO.setGmtModified(date);
            historyUserLogDO.setUserId(dataSourceDO.getUserId());
            historyUserLogDO.setDataSourceId(dataSourceDO.getId());
            historyUserLogDO.setStatus(Boolean.TRUE);
            historyUserLogMapper.insert(historyUserLogDO);
        } else {
            historyUserLogMapper.updateByUserIdAndSourceId(dataSourceDO.getId(), dataSourceDO.getUserId(), Boolean.TRUE);
        }
        preWarmingData(dataSourceDO.getId());
        DataSourceConnectVo dataSourceConnectVo = new DataSourceConnectVo();
        dataSourceConnectVo.setDateSourceId(dataSourceDO.getId());
        dataSourceConnectVo.setDateSourceName(dataSourceDO.getAlias());
        dataSourceConnectVo.setUserName(dataSourceDO.getUserName());
        DataSource dataSource = new DataSource();
        dataSource.setStatus(Boolean.TRUE);
        BeanUtils.copyProperties(dataSourceDO, dataSource);
        dataSource.setDriverConfig(JSON.parseObject(dataSourceDO.getDriverConfig(), DriverConfig.class));
        dataSource.setExtendInfo(JSON.parseArray(dataSourceDO.getExtendInfo(), KeyValue.class));
        dataSource.setSsh(JSON.parseObject(dataSourceDO.getSsh(), SSHInfo.class));
        DBConfig config = Chat2DBContext.getDBConfig(dataSourceDO.getType());
        if(config != null) {
            dataSource.setSupportDatabase(config.isSupportDatabase());
            dataSource.setSupportSchema(config.isSupportSchema());
        }
        dataSourceConnectVo.setDataSource(dataSource);
        return DataResult.of(dataSourceConnectVo);
    }

    private void preWarmingData(Long dataSourceId) {
        DataResult<DataSource> dataResult = queryById(dataSourceId);
        if (dataResult.success() && dataResult.getData() != null) {
            DataSource dataSource = dataResult.getData();
            DriverConfig driverConfig = dataSource.getDriverConfig();
            if (driverConfig == null || StringUtils.isBlank(driverConfig.getJdbcDriver())) {
                throw new BusinessException("JDBC连接失败");
            }
            try (Connection connection = IDriverManager.getConnection(dataSource.getUrl(), dataSource.getUserName(),
                    dataSource.getPassword(), dataSource.getDriverConfig(), dataSource.getExtendMap())) {
                DatabaseQueryAllParam databaseQueryAllParam = new DatabaseQueryAllParam();
                databaseQueryAllParam.setDataSourceId(dataSourceId);
                databaseQueryAllParam.setConnection(connection);
                databaseQueryAllParam.setDbType(dataSource.getType());
                databaseQueryAllParam.setRefresh(true);
                databaseService.queryAll(databaseQueryAllParam);
            } catch (Exception e) {
                log.error("preWarmingData error", e);
                if (e.getMessage().contains("用户名") || e.getMessage().toUpperCase(Locale.ROOT).contains("PASSWORD")) {
                    throw new BusinessException("用户名或密码错误");
                } else {
                    throw new BusinessException("数据源连接异常");
                }
            }
        }
    }

    @Override
    public DataResult<Long> updateWithPermission(DataSourceUpdateParam param) {
        DataSource dataSource = queryExistent(param.getId(), null).getData();
        PermissionUtils.checkOperationPermission(dataSource.getUserId());

        // 更新请求可能不携带密码；此时必须沿用已保存的密码，不能用空值覆盖旧配置。
        if (StringUtils.isBlank(param.getPassword())) {
            param.setPassword(dataSource.getPassword());
        }

        JdbcUtils.removePropertySameAsDefault(param.getDriverConfig());
        DataSourcePreConnectParam testParam = new DataSourcePreConnectParam();
        testParam.setUrl(StringUtils.defaultIfBlank(param.getUrl(), dataSource.getUrl()));
        testParam.setHost(StringUtils.defaultIfBlank(param.getHost(), dataSource.getHost()));
        testParam.setPort(StringUtils.defaultIfBlank(param.getPort(), dataSource.getPort()));
        testParam.setUser(StringUtils.defaultIfBlank(param.getUserName(), dataSource.getUserName()));
        testParam.setPassword(param.getPassword());
        testParam.setType(StringUtils.defaultIfBlank(param.getType(), dataSource.getType()));
        testParam.setSid(StringUtils.defaultIfBlank(param.getSid(), dataSource.getSid()));
        testParam.setDriver(StringUtils.defaultIfBlank(param.getDriver(), dataSource.getDriver()));
        testParam.setJdbc(StringUtils.defaultIfBlank(param.getJdbc(), dataSource.getJdbc()));
        testParam.setSsl(param.getSsl() == null ? dataSource.getSsl() : param.getSsl());
        testParam.setExtendInfo(param.getExtendInfo() == null ? dataSource.getExtendInfo() : param.getExtendInfo());
        testParam.setDriverConfig(param.getDriverConfig() == null ? dataSource.getDriverConfig() : param.getDriverConfig());
        SSHInfo ssh = param.getSsh() == null ? dataSource.getSsh() : param.getSsh();
        if (ssh == null) {
            ssh = new SSHInfo();
            ssh.setUse(false);
        }
        testParam.setSsh(ssh);
        ActionResult connectResult = preConnect(testParam);
        if (!connectResult.success()) {
            throw new BusinessException(StringUtils.defaultIfBlank(connectResult.getErrorMessage(), "数据源连接异常"));
        }

        DataSourceDO dataSourceDO = dataSourceConverter.param2do(param);
        dataSourceDO.setGmtModified(DateUtil.date());
        getBaseMapper().updateById(dataSourceDO);
        return DataResult.of(dataSourceDO.getId());
    }

    @Override
    public ActionResult deleteWithPermission(Long id) {
        DataSource dataSource = queryExistent(id, null).getData();
        PermissionUtils.checkOperationPermission(dataSource.getUserId());
        LambdaQueryWrapper<HistoryUserLogDO> historyUserLogDOLambdaQueryWrapper = new LambdaQueryWrapper<>();
        historyUserLogDOLambdaQueryWrapper.eq(HistoryUserLogDO::getDataSourceId, id);
        historyUserLogDOLambdaQueryWrapper.eq(HistoryUserLogDO::getUserId, dataSource.getUserId());
        historyUserLogMapper.delete(historyUserLogDOLambdaQueryWrapper);
        LambdaQueryWrapper<DataSourceAccessDO> dataSourceAccessQueryWrapper = new LambdaQueryWrapper<>();
        dataSourceAccessQueryWrapper.eq(DataSourceAccessDO::getDataSourceId, id);
        dataSourceAccessMapper.delete(dataSourceAccessQueryWrapper);
        getBaseMapper().deleteById(id);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<DataSource> queryById(Long id) {
        DataSourceDO dataSourceDO = getBaseMapper().selectById(id);
        return DataResult.of(dataSourceConverter.do2dto(dataSourceDO));
    }

    @Override
    public DataResult<DataSource> queryExistent(Long id, DataSourceSelector selector) {
        DataResult<DataSource> dataResult = queryById(id);
        if (dataResult.getData() == null) {
            throw new DataNotFoundException();
        }

        dataSourceAccessBusinessService.checkPermission(dataResult.getData());

        fillData(Lists.newArrayList(dataResult.getData()), selector);

        return dataResult;
    }

    @Override
    public DataResult<Long> copyByIdWithPermission(Long id) {
        DataSource dataSource = queryExistent(id, null).getData();
        PermissionUtils.checkOperationPermission(dataSource.getUserId());

        DataSourceDO dataSourceDO = getBaseMapper().selectById(id);
        String alias = dataSourceDO.getAlias() + "Copy";
        dataSourceDO.setId(null);
        dataSourceDO.setAlias(alias);
        dataSourceDO.setGmtCreate(DateUtil.date());
        dataSourceDO.setGmtModified(DateUtil.date());
        getBaseMapper().insert(dataSourceDO);
        return DataResult.of(dataSourceDO.getId());
    }

    @Override
    public PageResult<DataSource> queryPage(DataSourcePageQueryParam param, DataSourceSelector selector) {
        LambdaQueryWrapper<DataSourceDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(DataSourceDO::getAlias, "%" + param.getSearchKey() + "%")
                    .or()
                    .like(DataSourceDO::getUrl, "%" + param.getSearchKey() + "%"));
        }
        Integer start = param.getPageNo();
        Integer offset = param.getPageSize();
        Page<DataSourceDO> page = new Page<>(start, offset);
        IPage<DataSourceDO> iPage = getBaseMapper().selectPage(page, queryWrapper);
        List<DataSource> dataSources = dataSourceConverter.do2dto(iPage.getRecords());

        fillData(dataSources, selector);

        return PageResult.of(dataSources, iPage.getTotal(), param);
    }

    @Override
    public PageResult<DataSource> queryPageWithPermission(DataSourcePageQueryParam param, DataSourceSelector selector) {
        LoginUser loginUser = SecurityUtil.getLoginUser();
//        Page<DataSourceDO> page = this.page(new Page<>(param.getPageNo(), param.getPageSize()), new LambdaQueryWrapper<DataSourceDO>().eq(DataSourceDO::getUserId, loginUser.getUserId()));

        IPage<DataSourceDO> iPage = dataSourceCustomMapper.selectPageWithPermission(
                new Page<>(param.getPageNo(), param.getPageSize()),
                BooleanUtils.isTrue(loginUser.getAdmin()), loginUser.getUserId(), param.getSearchKey(), param.getKind(),
                EasySqlUtils.orderBy(param.getOrderByList()));

        List<DataSource> dataSources = dataSourceConverter.do2dto(iPage.getRecords());

        fillData(dataSources, selector);
        for (DataSource dataSource : dataSources) {
            LambdaQueryWrapper<HistoryUserLogDO> historyUserLogDOLambdaQueryWrapper = new LambdaQueryWrapper<>();
            historyUserLogDOLambdaQueryWrapper.eq(HistoryUserLogDO::getDataSourceId, dataSource.getId());
            historyUserLogDOLambdaQueryWrapper.eq(HistoryUserLogDO::getUserId, dataSource.getUserId());
            List<HistoryUserLogDO> historyUserLogDOs = historyUserLogMapper.selectList(historyUserLogDOLambdaQueryWrapper);
            if (Objects.nonNull(dataSource.getIsSave()) && !dataSource.getIsSave()) {
                dataSource.setPassword(null);
            }
            if (CollUtil.isEmpty(historyUserLogDOs)) {
                dataSource.setStatus(Boolean.FALSE);
            } else if (Boolean.FALSE.equals(historyUserLogDOs.get(0).getStatus())) {
                dataSource.setStatus(Boolean.FALSE);
            } else {
                Date date = new Date();
                Calendar instance = Calendar.getInstance();
                instance.setTime(date);
                instance.add(Calendar.MINUTE, -10);
                HistoryUserLogDO  historyUserLogDO = historyUserLogDOs.get(0);
                if (instance.getTime().after(historyUserLogDO.getGmtModified())) {
                    dataSource.setStatus(historyUserLogDO.getStatus());
                    historyUserLogDO.setStatus(Boolean.FALSE);
                    historyUserLogMapper.updateById(historyUserLogDO);
                    dataSource.setStatus(Boolean.FALSE);
                } else {
                    dataSource.setStatus(Boolean.TRUE);
                }
            }
        }
        // 创建时间倒排
//        dataSources = dataSources.stream().sorted(Comparator.comparing(DataSource::getGmtCreate, Comparator.reverseOrder())).collect(Collectors.toList());
        return PageResult.of(dataSources, iPage.getTotal(), param);

    }

    @Override
    public ListResult<DataSource> queryByIds(List<Long> ids) {
        return listQuery(ids, null);
    }

    @Override
    public ListResult<DataSource> listQuery(List<Long> idList, DataSourceSelector selector) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        List<DataSourceDO> dataList = getBaseMapper().selectBatchIds(idList);
        List<DataSource> list = dataSourceConverter.do2dto(dataList);

        fillData(list, selector);
        return ListResult.of(list);
    }

    @Override
    public ActionResult preConnect(DataSourcePreConnectParam param) {
        try {
            DataSourceTestParam testParam
                    = dataSourceConverter.param2param(param);
            DriverConfig driverConfig = testParam.getDriverConfig();
            if (driverConfig == null || !driverConfig.notEmpty()) {
                driverConfig = Chat2DBContext.getDefaultDriverConfig(param.getType());
            }
            DataSourceConnect dataSourceConnect = JdbcUtils.testConnect(testParam.getUrl(), testParam.getHost(),
                    testParam.getPort(),
                    testParam.getUsername(), testParam.getPassword(), testParam.getDbType(),
                    driverConfig, param.getSsh(), KeyValue.toMap(param.getExtendInfo()));
            if (BooleanUtils.isNotTrue(dataSourceConnect.getSuccess())) {
                return ActionResult.fail(connectionFailureMessage(param.getHost(), param.getPort(),
                        dataSourceConnect.getMessage()));
            }
        } catch (Exception e) {
            log.error("preConnect error", e);
            return ActionResult.fail(connectionFailureMessage(param.getHost(), param.getPort(), e.getMessage()));
        }
        return ActionResult.isSuccess();
    }

    static String connectionFailureMessage(String host, String port, String rawMessage) {
        String message = StringUtils.defaultString(rawMessage);
        String upperMessage = message.toUpperCase(Locale.ROOT);
        if (upperMessage.contains("ORA-01017") || upperMessage.contains("INVALID USERNAME/PASSWORD")
                || upperMessage.contains("PASSWORD") || message.contains("用户名") || message.contains("口令")) {
            return "用户名或密码错误";
        }
        if (upperMessage.contains("ORA-12514")) {
            return "Oracle 服务名不存在或尚未注册到监听器";
        }
        if (upperMessage.contains("ORA-12505")) {
            return "Oracle SID 不存在或尚未注册到监听器";
        }
        if (upperMessage.contains("ORA-12154")) {
            return "Oracle 连接标识无法解析，请检查服务名或 SID";
        }
        if (upperMessage.contains("UNKNOWNHOSTEXCEPTION") || upperMessage.contains("UNKNOWN HOST")
                || upperMessage.contains("NAME OR SERVICE NOT KNOWN")) {
            return "无法解析数据库主机：" + StringUtils.defaultIfBlank(host, "未填写");
        }
        if (upperMessage.contains("TIMED OUT") || upperMessage.contains("TIMEOUT")) {
            return "连接数据库超时，请检查主机、端口和网络";
        }
        if (upperMessage.contains("ORA-12541") || upperMessage.contains("CONNECTION REFUSED")
                || upperMessage.contains("NETWORK ADAPTER COULD NOT ESTABLISH")) {
            String target = StringUtils.defaultIfBlank(host, "未填写") + ":"
                    + StringUtils.defaultIfBlank(port, "未填写");
            if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) {
                return "无法连接到 " + target + "；Docker 部署时请将主机号改为 host.docker.internal";
            }
            return "无法连接到数据库 " + target + "，请检查地址、端口和数据库服务状态";
        }
        if (upperMessage.contains("NO SUITABLE DRIVER") || upperMessage.contains("DRIVER.LOAD")) {
            return "JDBC 驱动不可用，请检查驱动配置";
        }
        if (upperMessage.contains("ORA-01882")) {
            return "Oracle 客户端与数据库时区配置不兼容";
        }
        return "数据库连接失败，请检查连接参数或查看服务端日志";
    }

    @Override
    public ListResult<Database> connect(Long id) {
        queryExistent(id, null);
        DatabaseQueryAllParam queryAllParam = new DatabaseQueryAllParam();
        queryAllParam.setDataSourceId(id);
        List<Database> databases = Chat2DBContext.getMetaData().databases(Chat2DBContext.getConnection());
        return ListResult.of(databases);
    }

    @Override
    public ActionResult close(Long id) {
        DataSourceCloseParam closeParam = new DataSourceCloseParam();
        closeParam.setDataSourceId(id);
        DataSource dataSource = queryExistent(id, null).getData();
        LoginUser loginUser = SecurityUtil.getLoginUser();
        historyUserLogMapper.updateByUserIdAndSourceId(dataSource.getId(), loginUser.getUserId(), Boolean.FALSE);
        return ActionResult.isSuccess();
    }

    private void fillData(List<DataSource> list, DataSourceSelector selector) {
        if (CollectionUtils.isEmpty(list) || selector == null) {
            return;
        }

        fillEnvironment(list, selector);

        fillSupportDatabase(list);
    }

    private void fillSupportDatabase(List<DataSource> list) {

        if(CollectionUtils.isEmpty(list)) {
            return;
        }
        for (DataSource dataSource:list) {
            String type = dataSource.getType();
            if(StringUtils.isNotBlank(type)) {
                DBConfig config = Chat2DBContext.getDBConfig(type);
                if(config != null) {
                    dataSource.setSupportDatabase(config.isSupportDatabase());
                    dataSource.setSupportSchema(config.isSupportSchema());
                }
            }
        }
    }


    private void fillEnvironment(List<DataSource> list, DataSourceSelector selector) {
        if (BooleanUtils.isNotTrue(selector.getEnvironment())) {
            return;
        }
        environmentConverter.fillDetail(EasyCollectionUtils.toList(list, DataSource::getEnvironment));
    }

}
