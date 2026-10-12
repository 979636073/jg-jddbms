package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class TableFileTransferTest {
    @Test public void csvAndTsvHandleQuotedNewlinesAndColumns() throws Exception {
        String csv = "ID,NOTE\r\n1,\"a,b\r\nc\"\r\n2,\"x\"\"y\"\r\n";
        List<Map<String, String>> rows = TableFileTransfer.readFile(csv.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.CSV, "T");
        assertEquals(2, rows.size()); assertEquals("a,b\r\nc", rows.get(0).get("NOTE")); assertEquals("x\"y", rows.get(1).get("NOTE"));
        String tsv = "ID\tNOTE\n1\talpha\n";
        assertEquals("alpha", TableFileTransfer.readFile(tsv.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.TSV, "T").get(0).get("NOTE"));
    }

    @Test public void sqlRoundTripOnlyAcceptsLiteralInsertsForSelectedTable() throws Exception {
        String sql = "CREATE TABLE \"T\" (\"ID\" NUMBER, \"NOTE\" VARCHAR2(80));\n"
                + TableFileTransfer.insertSql("T", Arrays.asList("ID", "NOTE"), Arrays.asList("1", "a'; DELETE FROM T; --"));
        List<Map<String, String>> rows = TableFileTransfer.readFile(sql.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.SQL, "T");
        assertEquals(1, rows.size()); assertEquals("a'; DELETE FROM T; --", rows.get(0).get("NOTE"));
        for (String invalid : Arrays.asList("DELETE FROM T;", "INSERT INTO \"OTHER\" (\"ID\") VALUES (1);", "INSERT INTO \"T\" (\"ID\") VALUES (SEQ.NEXTVAL);")) {
            try { TableFileTransfer.readFile(invalid.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.SQL, "T"); fail(invalid); }
            catch (BusinessException expected) { }
        }
        String qualified = "CREATE TABLE \"BUSINESS\".\"T\" (\"ID\" NUMBER);\nINSERT INTO \"BUSINESS\".\"T\" (\"ID\") VALUES (12);";
        assertEquals("12", TableFileTransfer.readFile(qualified.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.SQL, "T").get(0).get("ID"));
        String textPrefix = TableFileTransfer.insertSql("T", Collections.singletonList("NOTE"), Collections.singletonList("BASE64:ordinary text"));
        assertTrue(textPrefix.contains("'BASE64:ordinary text'"));
        String binary = TableFileTransfer.insertSql("T", Collections.singletonList("PAYLOAD"), Collections.singletonList("BASE64:AQID"), Collections.singleton(0));
        assertTrue(binary.contains("HEXTORAW('010203')"));
        String commented = "-- exported by a tool\n/* table data */ INSERT INTO \"T\" (\"NOTE\") VALUES ('-- text /* preserved */'); -- trailing\n";
        assertEquals("-- text /* preserved */", TableFileTransfer.readFile(commented.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.SQL, "T").get(0).get("NOTE"));
    }

    @Test public void jsonAndXmlKeepNullAndUnicodeAndRejectXmlEntities() throws Exception {
        String json = "[{\"ID\":1,\"NOTE\":\"中文\"},{\"ID\":2,\"NOTE\":null}]";
        List<Map<String, String>> rows = TableFileTransfer.readFile(json.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.JSON, "T");
        assertEquals("中文", rows.get(0).get("NOTE")); assertNull(rows.get(1).get("NOTE"));
        String xml = "<?xml version=\"1.0\"?><table name=\"T\"><row><column name=\"ID\">1</column><column name=\"NOTE\" null=\"true\"/></row></table>";
        assertNull(TableFileTransfer.readFile(xml.getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.XML, "T").get(0).get("NOTE"));
        try { TableFileTransfer.readFile("<!DOCTYPE table [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><table/>".getBytes(StandardCharsets.UTF_8), TableFileTransfer.Format.XML, "T"); fail(); }
        catch (Exception expected) { }
    }

    @Test public void bothExcelFormatsReadHeaderAndRows() throws Exception {
        for (Workbook book : Arrays.asList(new HSSFWorkbook(), new XSSFWorkbook())) {
            byte[] bytes;
            try {
                Sheet sheet = book.createSheet("数据"); Row header = sheet.createRow(0); header.createCell(0).setCellValue("ID"); header.createCell(1).setCellValue("NOTE");
                Row row = sheet.createRow(1); row.createCell(0).setCellValue("1"); row.createCell(1).setCellValue("中文");
                ByteArrayOutputStream output = new ByteArrayOutputStream(); book.write(output); bytes = output.toByteArray();
            } finally { book.close(); }
            TableFileTransfer.Format format = book instanceof HSSFWorkbook ? TableFileTransfer.Format.XLS : TableFileTransfer.Format.XLSX;
            List<Map<String, String>> rows = TableFileTransfer.readFile(bytes, format, "T");
            assertEquals("1", rows.get(0).get("ID")); assertEquals("中文", rows.get(0).get("NOTE"));
        }
    }
}
