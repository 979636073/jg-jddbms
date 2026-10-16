package com.jd.plugin.dm;

import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.model.Procedure;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class DMMetaDataProcedureDefinitionTest {
    @Test
    public void shouldPreserveSourceOrderWithoutRequestingDdl() {
        Queries queries = new Queries(Arrays.asList("PROCEDURE P AS", "BEGIN NULL; END;"), null);
        Procedure result = new DMMetaData().procedure(queries.connection(), "DM", "APP", "P");
        assertEquals("PROCEDURE P AS\nBEGIN NULL; END;\n", result.getProcedureBody());
        assertEquals(1, queries.sql.size());
    }

    @Test
    public void shouldReadDdlWhenSourceViewReturnsNoDefinition() {
        String ddl = "CREATE PROCEDURE \"APP\".\"P\" AS BEGIN NULL; END;";
        Queries queries = new Queries(new ArrayList<>(), ddl);
        Procedure result = new DMMetaData().procedure(queries.connection(), "DM", "APP", "P");
        assertEquals(ddl, result.getProcedureBody());
        assertEquals(2, queries.sql.size());
        assertTrue(queries.sql.get(1).contains("GET_DDL('PROCEDURE', 'P', 'APP')"));
        assertEquals("APP", result.getSchemaName());
        assertEquals("P", result.getProcedureName());
    }

    @Test
    public void shouldRejectEmptyDefinitionInsteadOfReturningSuccess() {
        Queries queries = new Queries(Arrays.asList((String) null), "  ");
        try {
            new DMMetaData().procedure(queries.connection(), "DM", "APP", "P");
            fail("Empty source must produce an actionable error");
        } catch (BusinessException expected) {
            assertTrue(expected.getMessage().contains("存储过程定义"));
        }
    }

    @Test
    public void shouldEscapeNamesInDdlLookup() {
        assertEquals("SELECT DBMS_METADATA.GET_DDL('PROCEDURE', 'P''1', 'APP''QA') AS DDL FROM DUAL",
                DMMetaData.buildProcedureDdlSql("APP'QA", "P'1"));
    }

    private static class Queries {
        final List<String> source;
        final String ddl;
        final List<String> sql = new ArrayList<>();

        Queries(List<String> source, String ddl) { this.source = source; this.ddl = ddl; }

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        if (!"createStatement".equals(method.getName())) return null;
                        ResultSet[] rows = new ResultSet[1];
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Statement.class},
                                (statement, call, parameters) -> {
                                    if ("execute".equals(call.getName())) {
                                        sql.add((String) parameters[0]);
                                        rows[0] = rows(sql.size() == 1 ? source : Arrays.asList(ddl));
                                        return true;
                                    }
                                    return "getResultSet".equals(call.getName()) ? rows[0] : null;
                                });
                    });
        }

        ResultSet rows(List<String> values) {
            int[] index = {-1};
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResultSet.class},
                    (proxy, method, args) -> {
                        if ("next".equals(method.getName())) return ++index[0] < values.size();
                        return "getString".equals(method.getName()) ? values.get(index[0]) : null;
                    });
        }
    }
}
