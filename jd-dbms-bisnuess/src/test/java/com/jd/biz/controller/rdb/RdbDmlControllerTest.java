package com.jd.biz.controller.rdb;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RdbDmlControllerTest {

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
