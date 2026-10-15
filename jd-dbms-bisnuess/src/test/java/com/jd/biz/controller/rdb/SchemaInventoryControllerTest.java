package com.jd.biz.controller.rdb;

import org.junit.Test;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.Assert.*;

public class SchemaInventoryControllerTest {
    private final Map<String, String> definitions = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> attributes = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final List<String> truncated = new ArrayList<>();

    private void collect(String sql, String table) throws Exception {
        collect("INDEX", sql, table);
    }

    private void collect(String kind, String sql, String table) throws Exception {
        SchemaInventoryController.Query request = new SchemaInventoryController.Query();
        request.setSchemaName("QA");
        request.setTableName(table);
        Method method = SchemaInventoryController.class.getDeclaredMethod("collect", Connection.class, String.class,
                String.class, SchemaInventoryController.Query.class, Map.class, Map.class, Map.class, List.class, int.class);
        method.setAccessible(true);
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:inventory", "sa", "")) {
            method.invoke(new SchemaInventoryController(), connection, kind, sql, request,
                    definitions, attributes, errors, truncated, 2);
        }
    }

    @Test
    public void compositeColumnsRetainOrderAndNamedAttributes() throws Exception {
        collect("SELECT 'T' TABLE_NAME,'IX' INDEX_NAME, V COLUMN_NAME FROM (VALUES ('A'),('B')) X(V) WHERE ?='QA' ORDER BY V", null);
        assertTrue(errors.isEmpty());
        assertEquals(1, definitions.size());
        assertEquals("A", attributes.get("INDEX|T|IX").get("COLUMN_NAME[1]"));
        assertEquals("B", attributes.get("INDEX|T|IX").get("COLUMN_NAME[2]"));
    }

    @Test
    public void nullAndLiteralNullHaveDifferentFingerprints() throws Exception {
        collect("SELECT 'T','NULL_VALUE',CAST(NULL AS VARCHAR) V WHERE ?='QA' UNION ALL SELECT 'T','TEXT_VALUE','<NULL>'", null);
        assertNotEquals(definitions.get("INDEX|T|NULL_VALUE"), definitions.get("INDEX|T|TEXT_VALUE"));
        assertNull(attributes.get("INDEX|T|NULL_VALUE").get("V[1]"));
    }

    @Test
    public void separatorsAndWhitespaceDoNotCollide() throws Exception {
        collect("SELECT 'T','IX1','a|b' A,'c' B WHERE ?='QA' UNION ALL SELECT 'T','IX2','a','b|c' UNION ALL SELECT 'T','IX3',' a|b','c'", null);
        assertNotEquals(definitions.get("INDEX|T|IX1"), definitions.get("INDEX|T|IX2"));
        assertNotEquals(definitions.get("INDEX|T|IX1"), definitions.get("INDEX|T|IX3"));
    }

    @Test
    public void tableFilterIsBoundAsData() throws Exception {
        String table = "T' OR 1=1 --";
        collect("SELECT ? TABLE_NAME,'IX' INDEX_NAME,? COLUMN_NAME", table);
        assertTrue(errors.isEmpty());
        assertEquals(table, attributes.get("INDEX|QA|IX").get("COLUMN_NAME[1]"));
    }

    @Test
    public void failedCategoryDoesNotReturnEmptySuccess() throws Exception {
        collect("SELECT * FROM MISSING_TABLE WHERE OWNER=?", null);
        assertTrue(errors.containsKey("INDEX"));
        assertTrue(definitions.isEmpty());
        assertTrue(attributes.isEmpty());
    }

    @Test
    public void functionIndexIgnoresGeneratedColumnNameButKeepsExpression() throws Exception {
        collect("SELECT 'T','IX','SYS_COL_1' COLUMN_NAME,'UPPER(VAL)' COLUMN_EXPRESSION WHERE ?='QA'", null);
        assertNull(attributes.get("INDEX|T|IX").get("COLUMN_NAME[1]"));
        assertEquals("UPPER(VAL)", attributes.get("INDEX|T|IX").get("COLUMN_EXPRESSION[1]"));
    }

    @Test
    public void parsesRealDmFunctionIndexDdl() {
        assertEquals(Arrays.asList("UPPER(VAL)", "A"), SchemaInventoryController.indexExpressions(
                "CREATE OR REPLACE INDEX \"S\".\"IX\" ON \"S\".\"T\"(UPPER(VAL), A DESC) STORAGE(ON \"MAIN\", CLUSTERBTR) WITHOUT CLU_REC_ADDR ;"));
    }

    @Test
    public void invisibleForeignTargetCannotBeReportedAsEqual() throws Exception {
        collect("CONSTRAINT", "SELECT 'T','FK','R' CONSTRAINT_TYPE,CAST(NULL AS VARCHAR) REFERENCE_TABLE,CAST(NULL AS VARCHAR) REFERENCE_COLUMN WHERE ?='QA'", null);
        assertTrue(errors.containsKey("CONSTRAINT"));
    }

    @Test
    public void oversizedCategoryReportsIncomplete() throws Exception {
        collect("SELECT 'T','IX',X COLUMN_NAME FROM SYSTEM_RANGE(1,20001) WHERE ?='QA' ORDER BY X", null);
        assertTrue(truncated.contains("INDEX"));
        assertEquals(20000, attributes.get("INDEX|T|IX").size());
    }
}
