package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.TablePageQueryParam;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.spi.model.Table;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TableServiceImplPaginationTest {

    @Test
    public void shouldSearchAllCachedTablesBeforePaging() {
        TableServiceImpl service = new TableServiceImpl();
        TablePageQueryParam param = TablePageQueryParam.builder()
                .pageNo(1)
                .pageSize(1)
                .searchKey("customer")
                .build();

        PageResult<Table> result = service.pageTables(Arrays.asList(
                Table.builder().name("ACCOUNT").build(),
                Table.builder().name("CUSTOMER_ARCHIVE").build(),
                Table.builder().name("CUSTOMER_ORDER").build()), param);

        assertEquals(Long.valueOf(2), result.getTotal());
        assertEquals(1, result.getData().size());
        assertEquals("CUSTOMER_ARCHIVE", result.getData().get(0).getName());
    }

    @Test
    public void shouldReturnRequestedPageWithStableTotal() {
        TableServiceImpl service = new TableServiceImpl();
        TablePageQueryParam param = TablePageQueryParam.builder()
                .pageNo(2)
                .pageSize(2)
                .build();

        PageResult<Table> result = service.pageTables(Arrays.asList(
                Table.builder().name("T1").build(),
                Table.builder().name("T2").build(),
                Table.builder().name("T3").build(),
                Table.builder().name("T4").build()), param);

        assertEquals(Long.valueOf(4), result.getTotal());
        assertEquals(Arrays.asList("T3", "T4"), Arrays.asList(
                result.getData().get(0).getName(), result.getData().get(1).getName()));
    }
}
