package com.jd.spi.model;

import lombok.Data;
import com.jd.spi.sql.ConnectInfo;

import java.sql.Connection;
import java.util.Objects;

@Data
public class ConnectionVo {

    /**
     * 数据库会话
     */
    private Connection connection;
    /**
     * 提交回滚标识
     */
    private Boolean sign;
    /**
     * sessionId
     */
    private String sessionId;

    private String loginUser;

    private Long dataSourceId;

    private Long consoleId;

    private long lastAccessTime;

    public void bind(ConnectInfo connectInfo) {
        this.loginUser = connectInfo.getLoginUser();
        this.dataSourceId = connectInfo.getDataSourceId();
        this.consoleId = connectInfo.getConsoleId();
        touch();
    }

    public boolean ownedBy(ConnectInfo connectInfo) {
        return connectInfo != null
                && Objects.equals(loginUser, connectInfo.getLoginUser())
                && Objects.equals(dataSourceId, connectInfo.getDataSourceId())
                && Objects.equals(consoleId, connectInfo.getConsoleId());
    }

    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }
}
