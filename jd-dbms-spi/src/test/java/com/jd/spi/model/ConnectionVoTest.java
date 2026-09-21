package com.jd.spi.model;

import com.jd.spi.sql.ConnectInfo;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionVoTest {

    @Test
    public void shouldMatchUserDataSourceAndConsole() {
        ConnectInfo owner = connectInfo("10", 20L, 30L);
        ConnectionVo session = new ConnectionVo();
        session.bind(owner);

        assertTrue(session.ownedBy(connectInfo("10", 20L, 30L)));
        assertFalse(session.ownedBy(connectInfo("11", 20L, 30L)));
        assertFalse(session.ownedBy(connectInfo("10", 21L, 30L)));
        assertFalse(session.ownedBy(connectInfo("10", 20L, 31L)));
    }

    private ConnectInfo connectInfo(String loginUser, Long dataSourceId, Long consoleId) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setLoginUser(loginUser);
        connectInfo.setDataSourceId(dataSourceId);
        connectInfo.setConsoleId(consoleId);
        return connectInfo;
    }
}
