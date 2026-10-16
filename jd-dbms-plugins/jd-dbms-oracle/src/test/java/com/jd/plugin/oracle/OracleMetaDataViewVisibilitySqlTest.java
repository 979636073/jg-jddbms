package com.jd.plugin.oracle;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class OracleMetaDataViewVisibilitySqlTest {

    @Test
    public void shouldExcludeMaterializedViewsFromTableLists() throws Exception {
        for (String name : new String[]{"SELECT_TABLE_SQL", "SELECT_TABLE_DETAILS_SQL"}) {
            Assert.assertTrue(sql(name).contains("SYS.ALL_OBJECTS"));
            Assert.assertTrue(sql(name).contains("OBJECT_TYPE = 'MATERIALIZED VIEW'"));
            Assert.assertFalse(sql(name).contains("ALL_MVIEWS"));
        }
    }

    @Test
    public void shouldClassifyAccessibleMaterializedViewsOnce() throws Exception {
        String query = sql("SELECT_TABLE_VIEW_SQL");
        Assert.assertTrue(query.contains("SYS.ALL_OBJECTS"));
        Assert.assertTrue(query.contains("object_type IN ('VIEW', 'MATERIALIZED VIEW')"));
        Assert.assertFalse(query.contains("ALL_MVIEWS"));
        Assert.assertFalse(query.toLowerCase().contains("union"));
    }

    private String sql(String name) throws Exception {
        Field field = OracleMetaData.class.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
