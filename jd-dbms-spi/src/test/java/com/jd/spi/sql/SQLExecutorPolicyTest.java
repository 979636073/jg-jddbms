package com.jd.spi.sql;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SQLExecutorPolicyTest {

    @Test
    public void shouldBoundPageNumberAndPageSize() {
        assertEquals(1, SQLExecutor.normalizePageNo(null));
        assertEquals(1, SQLExecutor.normalizePageNo(0));
        assertEquals(3, SQLExecutor.normalizePageNo(3));
        assertEquals(1, SQLExecutor.normalizePageSize(0));
        assertEquals(200, SQLExecutor.normalizePageSize(200));
        assertEquals(1000, SQLExecutor.normalizePageSize(5000));
    }

    @Test
    public void shouldBoundQueryTimeout() {
        assertEquals(60, SQLExecutor.normalizeQueryTimeout(null));
        assertEquals(1, SQLExecutor.normalizeQueryTimeout(0));
        assertEquals(5, SQLExecutor.normalizeQueryTimeout(5));
        assertEquals(300, SQLExecutor.normalizeQueryTimeout(600));
    }
}
