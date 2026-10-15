package com.jd.biz.controller.data.source;

import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.core.converter.DataSourceConverter;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import com.jd.biz.domain.repository.mapper.DataSourceMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DataSourceSharingControllerTest {
    private DataSourceSharingController controller;
    private boolean denied;
    private int queries;

    @Before
    public void setup() {
        controller = new DataSourceSharingController();
        DataSourceDO source = new DataSourceDO();
        source.setId(42L);
        source.setUserId(1L);
        source.setKind("PRIVATE");
        source.setPassword("v2:old-key-password");
        DataSourceMapper mapper = (DataSourceMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DataSourceMapper.class}, (proxy, method, args) ->
                    "toString".equals(method.getName()) ? "DataSourceMapper test stub" : source);
        DataSourceAccessBusinessService access = (DataSourceAccessBusinessService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{DataSourceAccessBusinessService.class}, (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) return "Access service test stub";
                    assertEquals("checkActionPermission", method.getName());
                    assertEquals(DataSourceAction.DATASOURCE_MANAGE, args[1]);
                    if (denied) throw new PermissionDeniedBusinessException();
                    return ActionResult.isSuccess();
                });
        ReflectionTestUtils.setField(controller, "dataSourceMapper", mapper);
        ReflectionTestUtils.setField(controller, "dataSourceConverter", Mappers.getMapper(DataSourceConverter.class));
        ReflectionTestUtils.setField(controller, "accessService", access);
        ReflectionTestUtils.setField(controller, "jdbc", new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                queries++;
                return Collections.emptyList();
            }
        });
    }

    @Test
    public void sharingRouteWorksWithoutDecryptingConnectionPassword() throws Exception {
        String json = MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/connection/datasource/sharing/42"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json, json.contains("\"success\":true"));
        assertTrue(json, json.contains("\"grants\":[]"));
        assertFalse(json, json.contains("old-key-password"));
        assertEquals(1, queries);
    }

    @Test
    public void allSharingOperationsRequireManagementPermission() {
        denied = true;
        DataSourceSharingController.GrantRequest request = new DataSourceSharingController.GrantRequest();
        Runnable[] operations = {
                () -> controller.list(42L), () -> controller.users(42L, "admin"),
                () -> controller.teams(42L, "team"), () -> controller.grantUser(42L, request),
                () -> controller.grantTeam(42L, request), () -> controller.revokeUser(42L, 2L),
                () -> controller.revokeTeam(42L, 2L),
                () -> controller.effective(42L, 2L), () -> controller.audit(42L)
        };
        for (Runnable operation : operations) {
            try {
                operation.run();
                fail("Sharing operation must reject users without management permission");
            } catch (PermissionDeniedBusinessException expected) { }
        }
        assertEquals(0, queries);
    }

    @Test
    public void auditIsRestrictedToTheExactConnectionAndBounded() {
        ReflectionTestUtils.setField(controller, "jdbc", new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                assertTrue(sql.contains("FETCH FIRST 100 ROWS ONLY"));
                assertEquals("/api/connection/datasource/sharing/42/%", args[0]);
                return Collections.emptyList();
            }
        });
        assertTrue(controller.audit(42L).getSuccess());
    }
}
