package com.jd.biz.controller.rdb;

import com.jd.biz.controller.rdb.vo.UserOperationResult;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.domain.api.service.TableUserService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import org.junit.Assert;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

public class TableUserControllerTest {

    @Test
    public void shouldReportEachDeletedUserWithoutStoppingAtFailure() {
        TableUserController controller = new TableUserController();
        UserOperationResult result = controller.dropUsers(Arrays.asList("TEST_A", "TEST_B", "TEST_C"), userName -> {
            if ("TEST_B".equals(userName)) throw new SQLException("insufficient privilege");
        });

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(3, result.getItems().size());
        Assert.assertEquals("SUCCESS", result.getItems().get(0).getStatus());
        Assert.assertEquals("FAILED", result.getItems().get(1).getStatus());
        Assert.assertTrue(result.getItems().get(1).getMessage().contains("insufficient privilege"));
        Assert.assertEquals("SUCCESS", result.getItems().get(2).getStatus());
    }

    @Test
    public void shouldExposePrivilegeFailureWithoutLosingItemDetails() throws Exception {
        TableUserController controller = new TableUserController();
        UserOperationResult report = new UserOperationResult();
        report.add("SELECT", "GRANT", "FAILED", "permission denied");
        TableUserService service = (TableUserService) Proxy.newProxyInstance(
                TableUserService.class.getClassLoader(), new Class[]{TableUserService.class},
                (proxy, method, args) -> report);
        Field field = TableUserController.class.getDeclaredField("tableUserService");
        field.setAccessible(true);
        field.set(controller, service);

        TableBriefQueryRequest request = new TableBriefQueryRequest();
        request.setUserName("TEST_USER");
        request.setOldRoles(Collections.emptyList());
        request.setNewRoles(Collections.emptyList());
        DataResult<UserOperationResult> roleResponse = controller.addOrDelUserRole(request);
        Assert.assertFalse(roleResponse.getSuccess());
        Assert.assertSame(report, roleResponse.getData());

        DataResult<UserOperationResult> objectResponse = controller.addObjectRole(request);
        Assert.assertFalse(objectResponse.getSuccess());
        Assert.assertSame(report, objectResponse.getData());
    }
}
