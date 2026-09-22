package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.TableSpaceCreateRequest;
import com.jd.biz.controller.rdb.request.TableSpaceUpdateRequest;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TableSpaceServiceImplTest {

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
    }

    @Test
    public void shouldBringTablespaceOnlineWhenDatafileRenameFails() {
        putContext();
        RecordingTableSpaceService service = new RecordingTableSpaceService();

        try {
            service.updateTablespaceSql(updateRequest("/data/old.dbf", "/data/new.dbf"));
            fail("Expected tablespace update failure");
        } catch (BusinessException expected) {
            assertEquals(Arrays.asList(
                    "ALTER TABLESPACE \"TS_TEST\" offline;",
                    "ALTER TABLESPACE \"TS_TEST\" rename datafile '/data/old.dbf' to '/data/new.dbf';",
                    "ALTER TABLESPACE \"TS_TEST\" online;"), service.executedSql);
        }
    }

    @Test
    public void shouldRejectIncompleteTablespaceBeforeExecutingSql() {
        putContext();
        RecordingTableSpaceService service = new RecordingTableSpaceService();
        TableSpaceUpdateRequest request = updateRequest("/data/old.dbf", "/data/new.dbf");
        request.getNewTableSpace().setPath(null);

        try {
            service.updateTablespaceSql(request);
            fail("Expected validation failure");
        } catch (BusinessException expected) {
            assertEquals(0, service.executedSql.size());
        }
    }

    private TableSpaceUpdateRequest updateRequest(String oldPath, String newPath) {
        TableSpaceUpdateRequest request = new TableSpaceUpdateRequest();
        request.setDataSourceId(1L);
        request.setOldTableSpace(tableSpace(oldPath));
        request.setNewTableSpace(tableSpace(newPath));
        return request;
    }

    private TableSpaceCreateRequest tableSpace(String path) {
        TableSpaceCreateRequest request = new TableSpaceCreateRequest();
        request.setTableSpace("TS_TEST");
        request.setPath(path);
        request.setTotalSize("128");
        request.setSizeUnit("MB");
        request.setAutoSize("1");
        request.setAutoSizeUnit("MB");
        return request;
    }

    private void putContext() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType("DM");
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);
    }

    private static class RecordingTableSpaceService extends TableSpaceServiceImpl {
        private final List<String> executedSql = new ArrayList<>();

        @Override
        void executeTablespaceSql(String sql) throws SQLException {
            executedSql.add(sql);
            if (sql.contains("rename datafile")) {
                throw new SQLException("rename failed");
            }
        }
    }
}
