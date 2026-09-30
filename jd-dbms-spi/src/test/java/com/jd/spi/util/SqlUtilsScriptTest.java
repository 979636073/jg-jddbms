package com.jd.spi.util;

import com.alibaba.druid.DbType;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;

public class SqlUtilsScriptTest {
    @Test
    public void preservesSqlInsteadOfRewritingNativeSyntax() {
        String sql = "WITH t AS (SELECT 1 n FROM dual) SELECT /*+ NO_MERGE */ n, ROW_NUMBER() OVER (ORDER BY n) rn FROM t";
        for (DbType type : Arrays.asList(DbType.oracle, DbType.dm)) {
            assertEquals(Arrays.asList(sql), SqlUtils.parse(sql + ";", type));
        }
    }

    @Test
    public void doesNotSplitLiteralsIdentifiersOrComments() {
        String first = "SELECT 'a;it''s b', q'[a;it''s b]', \"A;B\" FROM dual /* ; */";
        assertEquals(Arrays.asList(first, "-- ;\nSELECT 2 FROM dual"),
                SqlUtils.parse(first + "; -- ;\nSELECT 2 FROM dual; -- trailing", DbType.oracle));
    }

    @Test
    public void preservesNestedBlocksAndSkipsSlashDelimiter() {
        String block = "DECLARE n NUMBER := 1; BEGIN IF n = 1 THEN BEGIN n := n + 1; END; END IF; FOR i IN 1..2 LOOP n := n + i; END LOOP; END;";
        for (DbType type : Arrays.asList(DbType.oracle, DbType.dm)) {
            assertEquals(Arrays.asList(block, "SELECT 2 FROM dual"),
                    SqlUtils.parse(block + "\n/\nSELECT 2 FROM dual;", type));
        }
    }

    @Test
    public void keepsProcedureBodyAndCaseExpression() {
        String procedure = "CREATE OR REPLACE PROCEDURE QA_SQL_PROC AS BEGIN NULL; IF 1 = 1 THEN NULL; END IF; END;";
        assertEquals(Arrays.asList(procedure, "SELECT CASE WHEN 1 = 1 THEN 'x;y' ELSE 'z' END FROM dual"),
                SqlUtils.parse(procedure + "\n/\nSELECT CASE WHEN 1 = 1 THEN 'x;y' ELSE 'z' END FROM dual;", DbType.oracle));
    }

    @Test
    public void ignoresCommentOnlyScripts() {
        assertEquals(0, SqlUtils.parse("-- comment;\n/* no SQL; */", DbType.dm).size());
    }
}
