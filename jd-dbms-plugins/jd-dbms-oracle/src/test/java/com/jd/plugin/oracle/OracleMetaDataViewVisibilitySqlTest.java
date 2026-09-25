package com.jd.plugin.oracle;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class OracleMetaDataViewVisibilitySqlTest {

    @Test
    public void shouldExcludeMaterializedViewsFromTableLists() throws Exception {
        Assert.assertTrue(sql("SELECT_TABLE_SQL").contains("ALL_MVIEWS"));
        Assert.assertTrue(sql("SELECT_TABLE_DETAILS_SQL").contains("ALL_MVIEWS"));
    }

    @Test
    public void shouldClassifyAccessibleMaterializedViewsOnce() throws Exception {
        String query = sql("SELECT_TABLE_VIEW_SQL");
        Assert.assertTrue(query.contains("ALL_MVIEWS"));
        Assert.assertTrue(query.contains("NOT EXISTS"));
        Assert.assertTrue(query.contains("MATERIALIZED VIEW"));
    }

    private String sql(String name) throws Exception {
        Field field = OracleMetaData.class.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
