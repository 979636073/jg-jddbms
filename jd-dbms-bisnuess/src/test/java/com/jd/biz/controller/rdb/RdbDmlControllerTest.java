package com.jd.biz.controller.rdb;

import org.junit.Test;
import com.alibaba.fastjson2.JSON;
import com.jd.biz.controller.rdb.request.GetBlobRequest;
import com.jd.biz.controller.rdb.request.DmlRequest;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.filter.XssFilter;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RdbDmlControllerTest {

    @Test
    public void blobLookupPreservesComparisonOperatorsThroughHttpFilter() throws Exception {
        AtomicReference<String> receivedSql = new AtomicReference<>();
        DlTemplateService service = (DlTemplateService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DlTemplateService.class}, (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) return "BLOB service test stub";
                    receivedSql.set(((GetBlobRequest) args[0]).getSql());
                    return "AQID";
                });
        RdbDmlController controller = new RdbDmlController();
        ReflectionTestUtils.setField(controller, "dlTemplateService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new XssFilter()).build();
        for (String operator : new String[]{">=", "<=", "<>", ">", "<"}) {
            String sql = "SELECT PAYLOAD FROM T WHERE ID " + operator + " 10 ORDER BY ID";
            GetBlobRequest request = new GetBlobRequest();
            request.setDataSourceId(42L);
            request.setBlobColumn("PAYLOAD");
            request.setSql(sql);
            request.setRowIndex(0);
            request.setColumnIndex(1);
            String response = mvc.perform(post("/api/rdb/dml/getBlobData").servletPath("/api/rdb/dml/getBlobData")
                    .contentType(MediaType.APPLICATION_JSON).content(JSON.toJSONString(request)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(response, response.contains("AQID"));
            assertEquals(sql, receivedSql.get());
        }
    }

    @Test
    public void sqlFilterKeepsQueryLiteralsAndStillCleansOrdinaryRequests() throws Exception {
        String body = "{\"sql\":\"SELECT '<tag>&gt;</tag>' FROM T WHERE ID <> 10\"}";
        for (String path : new String[]{"/api/rdb/dml/execute", "/api/rdb/dml/getBlobData", "/system/notice"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setServletPath(path);
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            AtomicReference<String> received = new AtomicReference<>();
            new XssFilter().doFilter(request, new MockHttpServletResponse(),
                    (filtered, response) -> received.set(IOUtils.toString(filtered.getInputStream(), StandardCharsets.UTF_8)));
            if (path.startsWith("/api/rdb/dml/")) {
                assertEquals(body, received.get());
            } else {
                assertTrue(received.get(), !received.get().contains("<tag>"));
            }
        }
    }

    @Test
    public void executeKeepsHtmlEntityLiteralsUnchanged() {
        AtomicReference<String> receivedSql = new AtomicReference<>();
        DlTemplateService service = (DlTemplateService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DlTemplateService.class}, (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) return "SQL service test stub";
                    receivedSql.set(((DlExecuteParam) args[0]).getSql());
                    return ListResult.empty();
                });
        RdbDmlController controller = new RdbDmlController();
        ReflectionTestUtils.setField(controller, "dlTemplateService", service);
        ReflectionTestUtils.setField(controller, "rdbWebConverter", Mappers.getMapper(RdbWebConverter.class));
        DmlRequest request = new DmlRequest();
        request.setSql("SELECT '&gt;' AS TXT FROM T WHERE ID <> 10");
        request.setConfirmDangerousSql(true);
        assertTrue(controller.manage(request).getSuccess());
        assertEquals("SELECT '&gt;' AS TXT FROM T WHERE ID <> 10", receivedSql.get());
    }

    @Test
    public void keepsDmDataViewSqlWhenRowIdIsAlreadySelected() {
        String sql = "select t.*, t.rowid from \"JDDBMS\".\"AA_FOREGN_TABLE\" t";

        assertEquals(sql, RdbDmlController.prepareDataViewSql(sql));
    }

    @Test
    public void keepsOracleDataViewSqlWhenAliasedRowIdIsAlreadySelected() {
        String sql = "select t.*, t.rowid as \"ROWID\" from \"APP\".\"T_USER\" t";

        assertEquals(sql, RdbDmlController.prepareDataViewSql(sql));
    }

    @Test
    public void addsRowIdWhenDataViewSqlDoesNotSelectIt() {
        String sql = "select * from \"APP\".\"T_USER\"";

        assertEquals("SELECT T.*, ROWID FROM (" + sql + ") T",
                RdbDmlController.prepareDataViewSql(sql));
    }
}
