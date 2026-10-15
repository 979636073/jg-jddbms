
package com.jd.biz.aspect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import com.jd.biz.controller.data.source.request.DataSourceConsoleRequestInfo;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.biz.domain.core.cache.CacheManage;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.exception.ParamBusinessException;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author jipengfei
 * @version : ConnectionInfoHandler.java
 */
@Component
@Aspect
@Slf4j
public class ConnectionInfoHandler {

    @Autowired
    private DataSourceService dataSourceService;
    @Resource
    private DataSourceAccessBusinessService dataSourceAccessBusinessService;


    /**
     * 设置数据源连接
     *
     * @param dataSourceId
     */
    public void buildContext(Long dataSourceId, Boolean isXxlJob) {
        // 设置数据源连接
        ConnectInfo connectInfo = toInfo(dataSourceId, null, isXxlJob);
        if (Objects.isNull(connectInfo)) {
            throw new BusinessException("数据源构建失败");
        }
        Chat2DBContext.putContext(connectInfo);
    }

    public ConnectInfo toInfo(Long dataSourceId, String database, Long consoleId, String schemaName, Boolean ref) {
        DataResult<DataSource> result = dataSourceService.queryById(dataSourceId);
        DataSource dataSource = result.getData();
        if (!result.success() || dataSource == null) {
            throw new ParamBusinessException("dataSourceId");
        }
        if (Boolean.TRUE.equals(ref)) {
            // Verify permissions
            dataSourceAccessBusinessService.checkPermission(dataSource);
        }
        ConnectInfo  connectInfo = new ConnectInfo();
        connectInfo.setAlias(dataSource.getAlias());
        connectInfo.setUser(dataSource.getUserName());
        connectInfo.setConsoleId(consoleId);
        connectInfo.setDataSourceId(dataSourceId);
        connectInfo.setPassword(dataSource.getPassword());
        connectInfo.setDbType(dataSource.getType());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setDatabase(database);
        connectInfo.setSchemaName(schemaName);
        connectInfo.setConsoleOwn(false);
        connectInfo.setDriver(dataSource.getDriver());
        connectInfo.setSsh(dataSource.getSsh());
        connectInfo.setSsl(dataSource.getSsl());
        connectInfo.setJdbc(dataSource.getJdbc());
        connectInfo.setExtendInfo(dataSource.getExtendInfo());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setPort(StringUtils.isNotBlank(dataSource.getPort()) ? Integer.parseInt(dataSource.getPort()) : null);
        connectInfo.setHost(dataSource.getHost());
        if (Boolean.TRUE.equals(ref)) {
            connectInfo.setLoginUser(ContextUtils.getLoginUser().getId() + "");
        } else {
            connectInfo.setLoginUser(1+"");
        }
        DriverConfig driverConfig = dataSource.getDriverConfig();
        if (driverConfig != null && driverConfig.notEmpty()) {
            connectInfo.setDriverConfig(driverConfig);
        }
        return connectInfo;
    }

    public ConnectInfo toInfo(Long dataSourceId, String database, Boolean ref) {
        return toInfo(dataSourceId, database, null, null, ref);
    }

    @Around("within(@com.jd.biz.aspect.ConnectionInfoAspect *)")
    public Object connectionInfoHandler(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        boolean isClose = true;
        Long changedMetadataSourceId = null;
        try {
            Object[] params = proceedingJoinPoint.getArgs();
            Long activeDataSourceId = null;
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param instanceof DataSourceBaseRequest) {
                        Long dataSourceId = ((DataSourceBaseRequest)param).getDataSourceId();
                        activeDataSourceId = dataSourceId;
                        String schemaName = ((DataSourceBaseRequest)param).getSchemaName();
                        String database = ((DataSourceBaseRequest)param).getDatabaseName();
                        Chat2DBContext.putContext(toInfo(dataSourceId, database, null, schemaName));
                    } else if (param instanceof DataSourceConsoleRequestInfo) {
                        Long dataSourceId = ((DataSourceConsoleRequestInfo)param).getDataSourceId();
                        activeDataSourceId = dataSourceId;
                        Long consoleId = ((DataSourceConsoleRequestInfo)param).getConsoleId();
                        String database = ((DataSourceConsoleRequestInfo)param).getDatabaseName();
                        Chat2DBContext.putContext(toInfo(dataSourceId, database, consoleId, null));
                    } else if (param instanceof DataSourceBaseRequestInfo) {
                        Long dataSourceId = ((DataSourceBaseRequestInfo)param).getDataSourceId();
                        activeDataSourceId = dataSourceId;
                        String database = ((DataSourceBaseRequestInfo)param).getDatabaseName();
                        Chat2DBContext.putContext(toInfo(dataSourceId, database));
                    }
                }
            }
            if (activeDataSourceId != null) {
                DataSource source = dataSourceService.queryById(activeDataSourceId).getData();
                if (source == null) throw new ParamBusinessException("dataSourceId");
                dataSourceAccessBusinessService.checkActionPermission(source, requestAction());
                if (requestAction() == DataSourceAction.SCHEMA_CHANGE) changedMetadataSourceId = activeDataSourceId;
            }
            Object proceed = proceedingJoinPoint.proceed();
            if (proceed instanceof ListResult) {
                ListResult<?> result = (ListResult<?>) proceed;
                if (CollUtil.isNotEmpty(result.getData())) {
                    isClose = result.getData().stream().noneMatch(item -> item instanceof ExecuteResultVO
                            && StrUtil.isNotBlank(((ExecuteResultVO) item).getSessionId()));
                }
            }
            return proceed;
        } finally {
            // DDL may commit part of a batch even when a later statement fails.
            if (changedMetadataSourceId != null) {
                CacheManage.invalidateColumns(changedMetadataSourceId);
                CacheManage.invalidateRelations(changedMetadataSourceId);
            }
            if (isClose) {
                Chat2DBContext.removeContext();
            } else {
                // 连接交给事务会话管理；线程池的下一个请求不能继承或关闭它。
                Chat2DBContext.detachContext();
            }
        }
    }

    private DataSourceAction requestAction() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return DataSourceAction.METADATA_READ;
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String path = request.getRequestURI().substring(request.getContextPath().length()).toLowerCase(java.util.Locale.ROOT);
        if (path.startsWith("/api/rdb/monitor/")) return DataSourceAction.MONITOR_VIEW;
        if (path.startsWith("/api/rdb/dml/")) {
            if (path.endsWith("/execute") || path.endsWith("/jdbcexecute") ||
                    path.endsWith("/execute_ddl")) return DataSourceAction.SQL_EXECUTE;
            if (path.contains("/blob/") || path.endsWith("/execute_table") || path.endsWith("/getblobdata") ||
                    path.endsWith("/count")) return DataSourceAction.DATA_READ;
            if (path.endsWith("/execute_update") || path.endsWith("/execute_blob_sql") ||
                    path.endsWith("/commitsession")) return DataSourceAction.DATA_WRITE;
            return "GET".equals(request.getMethod()) ? DataSourceAction.DATA_READ : DataSourceAction.DATA_WRITE;
        }
        if ("GET".equals(request.getMethod()) &&
                (path.endsWith("/createtablesql") || path.endsWith("/create/example") ||
                 path.endsWith("/update/example") || path.endsWith("/modify/sql") ||
                 path.endsWith("/gettemplatesql"))) {
            return DataSourceAction.METADATA_READ;
        }
        if (path.startsWith("/api/rdb/") && path.matches(".*(/copy|/drop|/delete|/create|/update|/alter|/lock|/grant|/revoke|/import).*")) {
            return DataSourceAction.SCHEMA_CHANGE;
        }
        if (path.startsWith("/api/rdb/")) {
            return "GET".equals(request.getMethod()) ? DataSourceAction.METADATA_READ : DataSourceAction.SCHEMA_CHANGE;
        }
        return "GET".equals(request.getMethod()) ? DataSourceAction.METADATA_READ : DataSourceAction.DATA_WRITE;
    }

    public ConnectInfo toInfo(Long dataSourceId, String database, Long consoleId, String schemaName) {
        DataResult<DataSource> result = dataSourceService.queryById(dataSourceId);
        DataSource dataSource = result.getData();
        if (!result.success() || dataSource == null) {
            throw new ParamBusinessException("dataSourceId");
        }

        // Verify permissions
        dataSourceAccessBusinessService.checkPermission(dataSource);

        ConnectInfo  connectInfo = new ConnectInfo();
        connectInfo.setAlias(dataSource.getAlias());
        connectInfo.setUser(dataSource.getUserName());
        connectInfo.setConsoleId(consoleId);
        connectInfo.setDataSourceId(dataSourceId);
        connectInfo.setPassword(dataSource.getPassword());
        connectInfo.setDbType(dataSource.getType());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setDatabase(database);
        connectInfo.setSchemaName(schemaName);
        connectInfo.setConsoleOwn(false);
        connectInfo.setDriver(dataSource.getDriver());
        connectInfo.setSsh(dataSource.getSsh());
        connectInfo.setSsl(dataSource.getSsl());
        connectInfo.setJdbc(dataSource.getJdbc());
        connectInfo.setExtendInfo(dataSource.getExtendInfo());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setPort(StringUtils.isNotBlank(dataSource.getPort()) ? Integer.parseInt(dataSource.getPort()) : null);
        connectInfo.setHost(dataSource.getHost());
        connectInfo.setLoginUser(ContextUtils.getLoginUser().getId() + "");
        DriverConfig driverConfig = dataSource.getDriverConfig();
        if (driverConfig != null && driverConfig.notEmpty()) {
            connectInfo.setDriverConfig(driverConfig);
        }
        return connectInfo;
    }

    public ConnectInfo toInfo(Long dataSourceId, String database) {
        return toInfo(dataSourceId, database, null, null);
    }

}
