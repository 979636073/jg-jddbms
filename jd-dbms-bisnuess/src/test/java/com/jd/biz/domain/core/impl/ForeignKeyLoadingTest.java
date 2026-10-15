package com.jd.biz.domain.core.impl;

import com.jd.plugin.dm.DMMetaData;
import com.jd.plugin.oracle.OracleMetaData;
import com.jd.spi.MetaData;
import com.jd.spi.model.ForeignData;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ForeignKeyLoadingTest {
    @Test public void dmIncomingForeignKeysBatchColumnsAndMatchCompositePositions() {
        java.util.ArrayList<String> queries = new java.util.ArrayList<>();
        String[][] keys = {{"OTHER", "CHILD", "FK1", "ENABLED", "PK"}, {"OTHER", "CHILD", "FK2", "DISABLED", "PK"}};
        String[][] columns = {{"OTHER", "CHILD", "FK1", "B", "2"}, {"APP", "PARENT", "PK", "ID1", "1"},
                {"OTHER", "CHILD", "FK2", "C", "1"}, {"APP", "PARENT", "PK", "ID2", "2"}, {"OTHER", "CHILD", "FK1", "A", "1"},
                {"APP", "UNRELATED", "PK", "WRONG", "1"}};
        int[] row = {-1}; boolean[] columnQuery = {false};
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (p, m, a) -> {
            String[][] data = columnQuery[0] ? columns : keys;
            if (m.getName().equals("next")) return ++row[0] < data.length;
            if (m.getName().equals("getInt")) return Integer.parseInt(data[row[0]][4]);
            if (m.getName().equals("getString")) {
                List<String> names = columnQuery[0] ? java.util.Arrays.asList("OWNER", "TABLE_NAME", "CONSTRAINT_NAME", "COLUMN_NAME", "POSITION")
                        : java.util.Arrays.asList("F_OWNER", "F_TABLE", "F_KEY", "STATUS", "P_KEY");
                return data[row[0]][names.indexOf(a[0])];
            }
            return null;
        });
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class}, (p, m, a) -> {
            if (m.getName().equals("execute")) {
                queries.add((String) a[0]); columnQuery[0] = ((String) a[0]).contains("ALL_CONS_COLUMNS"); row[0] = -1; return true;
            }
            return m.getName().equals("getResultSet") ? rows : null;
        });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> m.getName().equals("createStatement") ? statement : null);
        List<ForeignData> result = new DMMetaData().beiForeGnKey(connection, "APP", "PARENT");
        assertEquals(2, queries.size()); assertEquals(3, result.size());
        assertTrue(queries.get(1).contains("OWNER = 'OTHER'")); assertTrue(queries.get(1).contains("OWNER = 'APP'"));
        assertEquals("ID1", result.get(0).getColumn()); assertEquals("A", result.get(0).getForeignColumnName());
        assertEquals("ID2", result.get(1).getColumn()); assertEquals("B", result.get(1).getForeignColumnName());
        assertEquals("FK2", result.get(2).getConstraintName()); assertEquals("DISABLED", result.get(2).getStatus());
        assertEquals("OTHER", result.get(2).getForeignSchemaName());
    }
    @Test public void viewDependencyJoinsAreScopedToOwnerAndTypeInBothDirections() {
        java.util.ArrayList<String> queries = new java.util.ArrayList<>();
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (p, m, a) -> m.getName().equals("next") ? false : null);
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class}, (p, m, a) -> {
            if (m.getName().equals("execute")) { queries.add((String) a[0]); return true; }
            return m.getName().equals("getResultSet") ? rows : null;
        });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> m.getName().equals("createStatement") ? statement : null);
        DMMetaData meta = new DMMetaData();
        meta.getViewReferenced(connection, "QA'SCHEMA", "QA'VIEW");
        meta.getViewUponReferenced(connection, "QA'SCHEMA", "QA'VIEW");
        assertEquals(2, queries.size());
        for (String query : queries) {
            assertTrue(query.contains("O.OWNER = A.OWNER")); assertTrue(query.contains("O.OBJECT_TYPE = A.TYPE"));
            assertTrue(query.contains("QA''SCHEMA")); assertTrue(query.contains("QA''VIEW"));
        }
    }
    @Test public void dmForeignKeyStatusesComeFromTheSameQuery() {
        verify(new DMMetaData() {
            @Override public String getConStatus(Connection c, String t, String s, String n) {
                throw new AssertionError("Foreign keys must not issue a status query per column");
            }
        });
    }
    @Test public void oracleForeignKeyStatusesComeFromTheSameQuery() {
        verify(new OracleMetaData() {
            @Override public String getConStatus(Connection c, String t, String s, String n) {
                throw new AssertionError("Foreign keys must not issue a status query per column");
            }
        });
    }
    @Test public void oracleOutgoingForeignKeyKeepsChildAndParentSides() {
        AtomicInteger row = new AtomicInteger();
        String[] sql = new String[1];
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (p, m, a) -> {
            if (m.getName().equals("next")) return row.getAndIncrement() == 0;
            if (m.getName().equals("getString")) {
                switch ((String) a[0]) {
                    case "schema": return "QA_CHILD";
                    case "forSchema": return "QA_PARENT";
                    case "forTableName": return "CHILD";
                    case "table": return "PARENT";
                    case "forTableColumn": return "PARENT_ID";
                    case "tableColumn": return "ID";
                    case "CONSTRAINT_NAME": return "FK_CHILD_PARENT";
                    default: return "ENABLED";
                }
            }
            return null;
        });
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class}, (p, m, a) -> {
            if (m.getName().equals("execute")) { sql[0] = (String) a[0]; return true; }
            if (m.getName().equals("getResultSet")) return rows;
            return null;
        });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
            if (m.getName().equals("createStatement")) return statement;
            return null;
        });
        List<ForeignData> result = new OracleMetaData().getUnForeignKey(connection, "QA_CHILD", "CHILD");
        assertTrue(sql[0].contains("CONS.OWNER = 'QA_CHILD'"));
        assertTrue(sql[0].contains("CONS.TABLE_NAME = 'CHILD'"));
        assertTrue(sql[0].contains("CONS.OWNER AS \"schema\""));
        assertEquals(1, result.size());
        assertEquals("QA_CHILD", result.get(0).getSchemaName());
        assertEquals("CHILD", result.get(0).getTableName());
        assertEquals("PARENT_ID", result.get(0).getColumn());
        assertEquals("QA_PARENT", result.get(0).getForeignSchemaName());
        assertEquals("PARENT", result.get(0).getForeignTableName());
        assertEquals("ID", result.get(0).getForeignColumnName());
    }
    private void verify(MetaData meta) {
        AtomicInteger row = new AtomicInteger(-1), queries = new AtomicInteger();
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (p, m, a) -> {
            if (m.getName().equals("next")) return row.incrementAndGet() < 3;
            if (m.getName().equals("getString")) {
                if (a[0].equals("status")) return row.get() < 2 ? "ENABLED" : "DISABLED";
                if (a[0].equals("CONSTRAINT_NAME")) return row.get() < 2 ? "FK_COMPOSITE" : "FK_DISABLED";
                if (a[0].equals("forTableColumn")) return "REF" + row.get();
                if (a[0].equals("tableColumn")) return "ID" + row.get();
                return "QA";
            }
            return null;
        });
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class}, (p, m, a) -> {
            if (m.getName().equals("execute")) { queries.incrementAndGet(); return true; }
            if (m.getName().equals("getResultSet")) return rows;
            return null;
        });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
            if (m.getName().equals("createStatement")) return statement;
            return null;
        });
        List<ForeignData> result = meta.getForeignKey(connection, "CHILD", "QA");
        assertEquals(1, queries.get()); assertEquals(3, result.size());
        assertEquals("VALID", result.get(0).getStatus()); assertEquals("VALID", result.get(1).getStatus());
        assertEquals("INVALID", result.get(2).getStatus());
        assertEquals("FK_COMPOSITE", result.get(1).getConstraintName());
        assertEquals("REF1", result.get(1).getColumn()); assertEquals("ID1", result.get(1).getForeignColumnName());
    }
}
