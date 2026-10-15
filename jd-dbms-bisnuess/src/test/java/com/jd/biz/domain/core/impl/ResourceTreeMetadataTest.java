package com.jd.biz.domain.core.impl;

import com.jd.plugin.dm.DMMetaData;
import com.jd.plugin.oracle.OracleMetaData;
import com.jd.spi.model.Table;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ResourceTreeMetadataTest {
    @Test
    public void dmTablesShouldUseOneLightweightPreparedQuery() {
        Query query = new Query();
        List<Table> tables = new DMMetaData().tables(query.connection(), "DM", "APP'QA", null);
        assertEquals(1, query.executions);
        assertEquals(1, tables.size());
        assertEquals("OBJECT_1", tables.get(0).getName());
        assertEquals("comment", tables.get(0).getComment());
        assertEquals("APP'QA", tables.get(0).getSchemaName());
        assertEquals("APP'QA", query.parameters.get(0));
        assertTrue(query.sql.contains("WHERE t.OWNER = ?"));
        assertTrue(query.sql.contains("NOT LIKE 'MTAB$%'"));
        assertFalse(query.sql.contains("ALL_OBJECTS"));
        assertFalse(query.sql.contains("COUNT("));
        assertNull(tables.get(0).getTableDetails());
    }

    @Test
    public void dmTableNameShouldRemainExactAndBound() {
        Query query = new Query();
        new DMMetaData().tables(query.connection(), "DM", "APP", "T'1");
        assertTrue(query.sql.contains("AND t.TABLE_NAME = ?"));
        assertEquals("T'1", query.parameters.get(1));
    }

    @Test
    public void dmMaterializedViewsShouldResolveSchemaBySchid() {
        Query query = new Query();
        List<Table> views = new DMMetaData().tableViews(query.connection(), "DM", "APP'QA");
        assertEquals(1, query.executions);
        assertTrue(query.sql.contains("s.id = m.schid"));
        assertTrue(query.sql.contains("s.name = 'APP''QA'"));
        assertFalse(query.sql.contains("m.owner"));
        assertTrue(query.sql.contains("left join sys.all_objects"));
        assertEquals("MATERIALIZED VIEW", views.get(0).getTableDetails().getType());
    }

    @Test
    public void oracleMaterializedViewsShouldUseCatalogAndOneQuery() {
        Query query = new Query();
        List<Table> views = new OracleMetaData().tableViews(query.connection(), "ORCL", "APP'QA");
        assertEquals(1, query.executions);
        assertTrue(query.sql.contains("ALL_MVIEWS m left join ALL_OBJECTS"));
        assertTrue(query.sql.contains("m.owner = 'APP''QA'"));
        assertTrue(query.sql.contains("union all"));
        assertEquals("MATERIALIZED VIEW", views.get(0).getTableDetails().getType());
    }

    private static class Query {
        String sql;
        int executions;
        List<String> parameters = new ArrayList<>();

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            sql = (String) args[0];
                            return statement(PreparedStatement.class);
                        }
                        if ("createStatement".equals(method.getName())) return statement(Statement.class);
                        return null;
                    });
        }

        Object statement(Class<?> type) {
            ResultSet rows = rows();
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{type}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setString": parameters.add((String) args[1]); return null;
                    case "execute": sql = (String) args[0]; executions++; return true;
                    case "executeQuery": executions++; return rows;
                    case "getResultSet": return rows;
                    default: return null;
                }
            });
        }

        ResultSet rows() {
            int[] index = {0};
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResultSet.class},
                    (proxy, method, args) -> {
                        if ("next".equals(method.getName())) return index[0]++ == 0;
                        if ("getString".equals(method.getName())) {
                            String column = args[0].toString();
                            if ("COMMENTS".equals(column)) return "comment";
                            if ("object_type".equals(column)) return "MATERIALIZED VIEW";
                            return "OBJECT_1";
                        }
                        return null;
                    });
        }
    }
}
