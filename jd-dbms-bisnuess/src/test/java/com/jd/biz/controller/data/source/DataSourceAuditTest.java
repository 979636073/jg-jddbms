package com.jd.biz.controller.data.source;

import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class DataSourceAuditTest {
    @Test
    public void datasourceChangesHaveOperationLogsWithoutCredentials() {
        assertAudit("create", BusinessType.INSERT, false);
        assertAudit("update", BusinessType.UPDATE, false);
        assertAudit("copy", BusinessType.INSERT, true);
        assertAudit("delete", BusinessType.DELETE, true);
    }

    @Test
    public void readingDatasourceDoesNotCreateAuditTraffic() {
        for (Method method : DataSourceController.class.getDeclaredMethods()) {
            if ("list".equals(method.getName()) || "queryById".equals(method.getName())) {
                assertNull(method.getAnnotation(Log.class));
            }
        }
    }

    private void assertAudit(String name, BusinessType type, boolean saveRequest) {
        Method method = java.util.Arrays.stream(DataSourceController.class.getDeclaredMethods())
                .filter(item -> name.equals(item.getName())).findFirst().orElseThrow(AssertionError::new);
        Log log = method.getAnnotation(Log.class);
        assertNotNull(name + " 必须记录操作日志", log);
        assertEquals(type, log.businessType());
        assertEquals(saveRequest, log.isSaveRequestData());
        assertFalse("不保存包含连接配置的响应", log.isSaveResponseData());
    }
}
