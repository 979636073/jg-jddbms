package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.IDriverManager;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

@Component
public class MigrationConnections {
    @Resource private DataSourceService dataSourceService;

    public DataSource authorized(Long id) {
        if (id == null) throw new BusinessException("请选择数据源");
        DataSource source = dataSourceService.queryExistent(id, null).getData();
        if (!Arrays.asList("DM", "ORACLE").contains(source.getType())) throw new BusinessException("迁移只支持 DM 和 Oracle");
        if (source.getSsh() != null && source.getSsh().isUse()) {
            throw new BusinessException("首版迁移仅支持直连数据源，SSH 隧道尚未验收");
        }
        return source;
    }

    public Connection open(DataSource source) throws SQLException {
        DriverConfig driver = source.getDriverConfig();
        if (driver == null || !driver.notEmpty()) driver = Chat2DBContext.getDefaultDriverConfig(source.getType());
        return IDriverManager.getConnection(source.getUrl(), source.getUserName(), source.getPassword(), driver, source.getExtendMap());
    }

    public static String safeError(Exception error) {
        if (error instanceof BusinessException) return error.getMessage();
        if (error instanceof SQLException) {
            SQLException sql = (SQLException) error;
            String state = sql.getSQLState();
            return "数据库操作失败（代码 " + sql.getErrorCode() + "，状态 "
                    + (state != null && state.matches("[A-Za-z0-9]{1,8}") ? state : "未知") + "）；未记录业务字段或连接口令";
        }
        return "迁移操作失败，请根据对象和阶段核对；异常内容已脱敏";
    }
}
