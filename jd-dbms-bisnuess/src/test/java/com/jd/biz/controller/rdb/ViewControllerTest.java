package com.jd.biz.controller.rdb;

import com.jd.biz.controller.rdb.request.ViewRequest;
import com.jd.biz.domain.api.service.ViewService;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableDetails;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ViewControllerTest {

    @Test
    public void shouldDelegateMaterializedViewRefresh() throws Exception {
        AtomicInteger refreshCount = new AtomicInteger();
        ViewService viewService = (ViewService) Proxy.newProxyInstance(
                ViewService.class.getClassLoader(),
                new Class[]{ViewService.class},
                (proxy, method, args) -> {
                    if ("refreshMaterialized".equals(method.getName())) {
                        refreshCount.incrementAndGet();
                    }
                    return null;
                });
        ViewController controller = controller(viewService);

        ActionResult result = controller.refreshMaterialized(new ViewRequest());

        assertTrue(result.getSuccess());
        assertEquals(1, refreshCount.get());
    }

    @Test
    public void shouldSkipMaterializedViewsWhenCompilingAllViews() throws Exception {
        AtomicInteger executeCount = new AtomicInteger();
        ViewService viewService = (ViewService) Proxy.newProxyInstance(
                ViewService.class.getClassLoader(),
                new Class[]{ViewService.class},
                (proxy, method, args) -> {
                    if ("getViewList".equals(method.getName())) {
                        return Arrays.asList(view("NORMAL_VIEW", "VIEW"),
                                view("MATERIALIZED_VIEW", "MATERIALIZED VIEW"));
                    }
                    if ("allExecute".equals(method.getName())) {
                        executeCount.incrementAndGet();
                        ExecuteResult result = new ExecuteResult();
                        result.setSuccess(true);
                        return result;
                    }
                    return null;
                });

        ViewController controller = controller(viewService);

        ListResult<ExecuteResult> result = controller.allExecute(new ViewRequest());

        assertTrue(result.getSuccess());
        assertEquals(1, executeCount.get());
        assertEquals(1, result.getData().size());
    }

    private ViewController controller(ViewService viewService) throws Exception {
        ViewController controller = new ViewController();
        Field field = ViewController.class.getDeclaredField("viewService");
        field.setAccessible(true);
        field.set(controller, viewService);
        return controller;
    }

    private Table view(String name, String type) {
        TableDetails details = new TableDetails();
        details.setType(type);
        Table table = new Table();
        table.setName(name);
        table.setTableDetails(details);
        return table;
    }
}
