package com.jd.biz.migration;

import org.junit.Test;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.Assert.*;

public class SchemaSqlTransferTest {
    @Test public void previewReadsLengthFramedSectionsWithUnicodeAndMarkerTextInSql() throws Exception {
        StringWriter writer = new StringWriter();
        writer.write("-- JDDBMS-SCHEMA-SQL 1\n-- SCHEMA U1lT\n-- SOURCE DM\n");
        SchemaSqlTransfer.section(writer, "TABLE", "中文表", "中文表", "CREATE TABLE \"中文表\" (\"NOTE\" VARCHAR2(60));");
        SchemaSqlTransfer.section(writer, "DATA", "中文表", "中文表", "INSERT INTO \"中文表\" (\"NOTE\") VALUES ('line\n-- JDDBMS-END\ntext');");
        SchemaSqlTransfer service = new SchemaSqlTransfer(null, null, null);
        SchemaSqlTransfer.Preview preview = service.preview(writer.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals("SYS", preview.schema); assertEquals("DM", preview.sourceType);
        assertEquals(Collections.singletonList("中文表"), preview.tables);
    }
}
