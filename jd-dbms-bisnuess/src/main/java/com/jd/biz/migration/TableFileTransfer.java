package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.tools.base.excption.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Table-level, bounded file interchange. Imports append to an existing table in one transaction. */
@Service
public class TableFileTransfer {
    static final int MAX_ROWS = 100000;
    static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    private static final String IDENTIFIER = "(\"(?:[^\"]|\"\")+\"|[\\w$#]+)";
    private static final Pattern INSERT = Pattern.compile("(?is)^INSERT\\s+INTO\\s+(?:" + IDENTIFIER + "\\s*\\.\\s*)?" + IDENTIFIER + "\\s*\\((.*)\\)\\s*VALUES\\s*\\((.*)\\)$");
    private static final Pattern CREATE = Pattern.compile("(?is)^CREATE\\s+TABLE\\s+(?:" + IDENTIFIER + "\\s*\\.\\s*)?" + IDENTIFIER + "\\s*\\(.*\\)$");
    private final MigrationConnections connections;

    public TableFileTransfer(MigrationConnections connections) { this.connections = connections; }

    public byte[] exportFile(Long id, String schema, String table, String format) throws Exception {
        DataSource source = connections.authorized(id, DataSourceAction.DATA_READ);
        return exportFile(source, schema, table, format);
    }

    public byte[] exportFile(DataSource source, String schema, String table, String format) throws Exception {
        Format kind = Format.parse(format);
        MigrationDialect.quote(schema); MigrationDialect.quote(table);
        try (Connection connection = connections.open(source)) {
            String qualified = MigrationDialect.qualified(schema, table);
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + qualified)) {
                statement.setFetchSize(500);
                try (ResultSet rows = statement.executeQuery()) {
                    ResultSetMetaData meta = rows.getMetaData();
                    List<String> headers = headers(meta);
                    ByteArrayOutputStream output = new LimitedOutputStream();
                    if (kind == Format.XLS || kind == Format.XLSX) writeExcel(rows, headers, kind, output);
                    else writeText(rows, meta, headers, table, kind, output);
                    if (output.size() > MAX_FILE_BYTES) throw new BusinessException("导出文件超过 50 MiB，请缩小表的数据范围");
                    return output.toByteArray();
                }
            }
        }
    }

    public int importFile(Long id, String schema, String table, String format, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) throw new BusinessException("请选择不超过 50 MiB 的文件");
        DataSource target = connections.authorized(id, DataSourceAction.DATA_WRITE);
        return importFile(target, schema, table, format, file.getBytes());
    }

    public int importFile(DataSource target, String schema, String table, String format, byte[] bytes) throws Exception {
        Format kind = Format.parse(format);
        MigrationDialect.quote(schema); MigrationDialect.quote(table);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw new BusinessException("请选择不超过 50 MiB 的文件");
        List<Map<String, String>> rows = readFile(bytes, kind, table);
        if (rows.isEmpty()) return 0;
        if (rows.size() > MAX_ROWS) throw new BusinessException("单次最多导入 " + MAX_ROWS + " 行");
        try (Connection connection = connections.open(target)) {
            connection.setAutoCommit(false);
            try {
                int count = insertRows(connection, schema, table, rows);
                connection.commit();
                return count;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) { throw new BusinessException(MigrationConnections.safeError(error)); }
    }

    private static List<String> headers(ResultSetMetaData meta) throws SQLException {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) result.add(meta.getColumnLabel(i));
        return result;
    }

    private static void writeText(ResultSet rows, ResultSetMetaData meta, List<String> headers, String table, Format kind, OutputStream output) throws Exception {
        Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        int count = 0;
        if (kind == Format.CSV || kind == Format.TSV) {
            writer.write('\ufeff');
            writeDelimited(writer, headers, kind == Format.CSV ? ',' : '\t');
        } else if (kind == Format.SQL) {
            writer.write(createTableSql(meta, table));
        } else if (kind == Format.JSON) writer.write("[\n");
        XMLStreamWriter xml = null;
        if (kind == Format.XML) {
            xml = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
            xml.writeStartDocument("UTF-8", "1.0"); xml.writeStartElement("table"); xml.writeAttribute("name", table);
        }
        while (rows.next()) {
            if (++count > MAX_ROWS || (kind == Format.XLS && count > 65535)) throw new BusinessException("单次最多导出 " + MAX_ROWS + " 行");
            List<String> values = new ArrayList<>();
            Map<String, String> object = new LinkedHashMap<>();
            for (int i = 1; i <= headers.size(); i++) {
                Object value = rows.getObject(i);
                String text = value == null ? null : value instanceof byte[] ? "BASE64:" + Base64.getEncoder().encodeToString((byte[]) value) : value instanceof Blob ? blob((Blob) value) : value instanceof Clob ? clob((Clob) value) : String.valueOf(value);
                values.add(text); object.put(headers.get(i - 1), text);
            }
            if (kind == Format.CSV || kind == Format.TSV) writeDelimited(writer, values, kind == Format.CSV ? ',' : '\t');
            else if (kind == Format.JSON) { if (count > 1) writer.write(",\n"); writer.write(JSON.toJSONString(object)); }
            else if (kind == Format.SQL) {
                Set<Integer> binaryColumns = new HashSet<>();
                for (int i = 1; i <= headers.size(); i++) if (binaryType(meta.getColumnType(i))) binaryColumns.add(i - 1);
                writer.write(insertSql(table, headers, values, binaryColumns));
            }
            else {
                xml.writeStartElement("row");
                for (int i = 0; i < headers.size(); i++) {
                    xml.writeStartElement("column"); xml.writeAttribute("name", headers.get(i));
                    if (values.get(i) == null) xml.writeAttribute("null", "true"); else xml.writeCharacters(values.get(i));
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }
        }
        if (kind == Format.JSON) writer.write("\n]\n");
        if (xml != null) { xml.writeEndElement(); xml.writeEndDocument(); xml.flush(); xml.close(); }
        writer.flush();
    }

    private static String blob(Blob blob) throws SQLException {
        if (blob.length() > MAX_FILE_BYTES) throw new BusinessException("单个 BLOB 超出文件大小上限");
        return "BASE64:" + Base64.getEncoder().encodeToString(blob.getBytes(1, (int) blob.length()));
    }
    private static String clob(Clob clob) throws SQLException {
        if (clob.length() > MAX_FILE_BYTES) throw new BusinessException("单个 CLOB 超出文件大小上限");
        return clob.getSubString(1, (int) clob.length());
    }

    private static void writeDelimited(Writer writer, List<String> values, char delimiter) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(delimiter);
            String value = values.get(i);
            if (value == null) continue;
            boolean quoted = value.indexOf(delimiter) >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
            if (quoted) writer.write('"');
            writer.write(quoted ? value.replace("\"", "\"\"") : value);
            if (quoted) writer.write('"');
        }
        writer.write("\r\n");
    }

    static String createTableSql(ResultSetMetaData meta, String table) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(MigrationDialect.quote(table)).append(" (\n");
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (i > 1) sql.append(",\n");
            sql.append("  ").append(MigrationDialect.quote(meta.getColumnLabel(i))).append(' ').append(portableType(meta, i));
            if (meta.isNullable(i) == ResultSetMetaData.columnNoNulls) sql.append(" NOT NULL");
        }
        return sql.append("\n);\n").toString();
    }

    private static String portableType(ResultSetMetaData meta, int i) throws SQLException {
        int length = meta.getPrecision(i), scale = meta.getScale(i);
        switch (meta.getColumnType(i)) {
            case Types.CHAR: return "CHAR(" + Math.max(1, Math.min(length, 2000)) + ")";
            case Types.NCHAR: return "NCHAR(" + Math.max(1, Math.min(length, 2000)) + ")";
            case Types.VARCHAR: case Types.LONGVARCHAR: return length > 4000 ? "CLOB" : "VARCHAR2(" + Math.max(1, length) + ")";
            case Types.NVARCHAR: case Types.LONGNVARCHAR: return length > 2000 ? "NCLOB" : "NVARCHAR2(" + Math.max(1, length) + ")";
            case Types.CLOB: return "CLOB";
            case Types.NCLOB: return "NCLOB";
            case Types.BLOB: case Types.BINARY: case Types.VARBINARY: case Types.LONGVARBINARY: return "BLOB";
            case Types.DATE: return "DATE";
            case Types.TIMESTAMP: case Types.TIMESTAMP_WITH_TIMEZONE: return "TIMESTAMP";
            case Types.FLOAT: case Types.REAL: case Types.DOUBLE: return "FLOAT";
            case Types.INTEGER: case Types.SMALLINT: case Types.TINYINT: case Types.BIGINT: case Types.NUMERIC: case Types.DECIMAL:
                return length > 0 && length <= 38 && scale >= 0 && scale <= length ? "NUMBER(" + length + "," + scale + ")" : "NUMBER";
            default: throw new BusinessException("列 " + meta.getColumnLabel(i) + " 的类型不能生成通用 SQL，请使用其他格式或数据库直连迁移");
        }
    }

    static String insertSql(String table, List<String> headers, List<String> values) {
        return insertSql(table, headers, values, Collections.emptySet());
    }

    private static boolean binaryType(int type) {
        return type == Types.BLOB || type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY;
    }

    static String insertSql(String table, List<String> headers, List<String> values, Set<Integer> binaryColumns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(MigrationDialect.quote(table)).append(" (");
        for (int i = 0; i < headers.size(); i++) { if (i > 0) sql.append(", "); sql.append(MigrationDialect.quote(headers.get(i))); }
        sql.append(") VALUES (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.append(", ");
            String value = values.get(i);
            if (value == null) sql.append("NULL");
            else if (binaryColumns.contains(i) && value.startsWith("BASE64:")) {
                byte[] binary = Base64.getDecoder().decode(value.substring(7));
                sql.append("HEXTORAW('"); for (byte b : binary) sql.append(String.format("%02X", b & 255)); sql.append("')");
            } else sql.append(MigrationDialect.literal(value));
        }
        return sql.append(");\n").toString();
    }

    private static void writeExcel(ResultSet rows, List<String> headers, Format kind, OutputStream output) throws Exception {
        Workbook book = kind == Format.XLS ? new HSSFWorkbook() : new SXSSFWorkbook(100);
        try {
            Sheet sheet = book.createSheet("数据"); Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) head.createCell(i).setCellValue(headers.get(i));
            int rowIndex = 0;
            while (rows.next()) {
                if (++rowIndex > MAX_ROWS || kind == Format.XLS && rowIndex > 65535) throw new BusinessException(kind == Format.XLS ? "XLS 最多导出 65535 行，请改用 XLSX" : "单次最多导出 " + MAX_ROWS + " 行");
                Row row = sheet.createRow(rowIndex);
                for (int i = 0; i < headers.size(); i++) {
                    Object value = rows.getObject(i + 1);
                    if (value != null) row.createCell(i).setCellValue(value instanceof byte[] ? "BASE64:" + Base64.getEncoder().encodeToString((byte[]) value) : value instanceof Blob ? blob((Blob) value) : value instanceof Clob ? clob((Clob) value) : String.valueOf(value));
                }
            }
            book.write(output);
        } finally { book.close(); if (book instanceof SXSSFWorkbook) ((SXSSFWorkbook) book).dispose(); }
    }

    static List<Map<String, String>> readFile(byte[] bytes, Format kind, String table) throws Exception {
        if (kind == Format.XLS || kind == Format.XLSX) return readExcel(bytes);
        String content = new String(bytes, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
        if (kind == Format.CSV || kind == Format.TSV) return readDelimited(content, kind == Format.CSV ? ',' : '\t');
        if (kind == Format.JSON) {
            JSONArray array = JSON.parseArray(content); if (array == null) throw new BusinessException("JSON 顶层必须是对象数组");
            List<Map<String, String>> output = new ArrayList<>();
            for (Object item : array) {
                if (!(item instanceof JSONObject)) throw new BusinessException("JSON 顶层必须是对象数组");
                Map<String, String> row = new LinkedHashMap<>();
                ((JSONObject) item).forEach((key, value) -> row.put(key, value == null ? null : String.valueOf(value)));
                output.add(row); checkRowLimit(output.size());
            }
            return output;
        }
        if (kind == Format.XML) return readXml(bytes);
        return readSql(content, table);
    }

    private static List<Map<String, String>> readDelimited(String content, char delimiter) {
        List<List<String>> records = new ArrayList<>(); List<String> record = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') { if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') { field.append('"'); i++; } else quoted = !quoted; }
            else if (c == delimiter && !quoted) { record.add(field.toString()); field.setLength(0); }
            else if ((c == '\n' || c == '\r') && !quoted) { if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++; record.add(field.toString()); field.setLength(0); records.add(record); record = new ArrayList<>(); }
            else field.append(c);
        }
        if (quoted) throw new BusinessException("CSV/TSV 引号未闭合");
        if (field.length() > 0 || !record.isEmpty()) { record.add(field.toString()); records.add(record); }
        if (records.isEmpty()) return Collections.emptyList();
        List<String> headers = records.remove(0); List<Map<String, String>> output = new ArrayList<>();
        for (List<String> values : records) {
            if (values.size() != headers.size()) throw new BusinessException("CSV/TSV 数据列数与表头不一致");
            Map<String, String> row = new LinkedHashMap<>(); for (int i = 0; i < headers.size(); i++) row.put(headers.get(i), values.get(i)); output.add(row); checkRowLimit(output.size());
        }
        return output;
    }

    private static List<Map<String, String>> readExcel(byte[] bytes) throws Exception {
        List<Map<String, String>> output = new ArrayList<>();
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = book.getSheetAt(0); if (sheet.getPhysicalNumberOfRows() == 0) return output;
            DataFormatter formatter = new DataFormatter(Locale.ROOT); Row first = sheet.getRow(sheet.getFirstRowNum());
            List<String> headers = new ArrayList<>(); for (Cell cell : first) headers.add(formatter.formatCellValue(cell));
            for (int r = first.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row source = sheet.getRow(r); if (source == null) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) { Cell cell = source.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL); row.put(headers.get(c), cell == null ? null : formatter.formatCellValue(cell)); }
                output.add(row); checkRowLimit(output.size());
            }
        }
        return output;
    }

    private static List<Map<String, String>> readXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        if (!"table".equals(document.getDocumentElement().getNodeName())) throw new BusinessException("XML 根节点应为 table");
        List<Map<String, String>> output = new ArrayList<>(); NodeList rows = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < rows.getLength(); i++) if (rows.item(i) instanceof Element) {
            Element rowElement = (Element) rows.item(i); if (!"row".equals(rowElement.getTagName())) throw new BusinessException("XML 只允许 row 节点");
            Map<String, String> row = new LinkedHashMap<>(); NodeList columns = rowElement.getChildNodes();
            for (int j = 0; j < columns.getLength(); j++) if (columns.item(j) instanceof Element) {
                Element column = (Element) columns.item(j); if (!"column".equals(column.getTagName())) throw new BusinessException("XML 只允许 column 节点");
                row.put(column.getAttribute("name"), "true".equals(column.getAttribute("null")) ? null : column.getTextContent());
            }
            output.add(row); checkRowLimit(output.size());
        }
        return output;
    }

    private static List<Map<String, String>> readSql(String content, String table) {
        List<Map<String, String>> output = new ArrayList<>();
        for (String statement : splitSql(stripSqlComments(content))) {
            String sql = statement.trim(); if (sql.isEmpty()) continue;
            if (sql.toUpperCase(Locale.ROOT).startsWith("CREATE TABLE ")) {
                Matcher create = CREATE.matcher(sql);
                if (!create.matches() || !unquote(create.group(2)).equalsIgnoreCase(table)) throw new BusinessException("SQL 脚本的建表名称与所选表不同");
                continue; // Right-click imports always target an existing table; never execute DDL from a file.
            }
            Matcher matcher = INSERT.matcher(sql);
            if (!matcher.matches() || !unquote(matcher.group(2)).equalsIgnoreCase(table)) throw new BusinessException("SQL 文件只允许对所选表的 INSERT 和 CREATE TABLE 语句");
            List<String> names = splitSqlList(matcher.group(3)), values = splitSqlList(matcher.group(4));
            if (names.size() != values.size()) throw new BusinessException("INSERT 字段与值数量不一致");
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) row.put(unquote(names.get(i).trim()), sqlValue(values.get(i).trim()));
            output.add(row); checkRowLimit(output.size());
        }
        return output;
    }

    private static String stripSqlComments(String content) {
        StringBuilder clean = new StringBuilder(content.length());
        boolean single = false, quotedIdentifier = false, lineComment = false, blockComment = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i), next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
            if (lineComment) { if (current == '\n' || current == '\r') { lineComment = false; clean.append(current); } continue; }
            if (blockComment) { if (current == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (!single && !quotedIdentifier && current == '-' && next == '-') { clean.append(' '); lineComment = true; i++; continue; }
            if (!single && !quotedIdentifier && current == '/' && next == '*') { clean.append(' '); blockComment = true; i++; continue; }
            clean.append(current);
            if (current == '\'' && !quotedIdentifier) {
                if (single && next == '\'') { clean.append(next); i++; } else single = !single;
            } else if (current == '"' && !single) {
                if (quotedIdentifier && next == '"') { clean.append(next); i++; } else quotedIdentifier = !quotedIdentifier;
            }
        }
        if (blockComment) throw new BusinessException("SQL 注释未闭合");
        return clean.toString();
    }

    private static String sqlValue(String input) {
        if ("NULL".equalsIgnoreCase(input)) return null;
        if (input.startsWith("'") && input.endsWith("'")) return input.substring(1, input.length() - 1).replace("''", "'");
        if (input.matches("[-+]?[0-9]+(\\.[0-9]+)?")) return input;
        Matcher hex = Pattern.compile("(?is)^HEXTORAW\\('([0-9A-F]*)'\\)$").matcher(input);
        if (hex.matches()) { String digits = hex.group(1); if (digits.length() % 2 != 0) throw new BusinessException("十六进制值长度无效"); byte[] binary = new byte[digits.length() / 2]; for (int i = 0; i < binary.length; i++) binary[i] = (byte) Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16); return "BASE64:" + Base64.getEncoder().encodeToString(binary); }
        throw new BusinessException("SQL 文件包含不支持的表达式，只接受字面量和 HEXTORAW");
    }

    private static List<String> splitSql(String content) { return splitAware(content, ';'); }
    private static List<String> splitSqlList(String content) { return splitAware(content, ','); }
    private static List<String> splitAware(String content, char delimiter) {
        List<String> parts = new ArrayList<>(); StringBuilder part = new StringBuilder(); boolean single = false, doubleQuote = false; int depth = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\'' && !doubleQuote) { if (single && i + 1 < content.length() && content.charAt(i + 1) == '\'') { part.append("''"); i++; continue; } single = !single; }
            else if (c == '"' && !single) { if (doubleQuote && i + 1 < content.length() && content.charAt(i + 1) == '"') { part.append("\"\""); i++; continue; } doubleQuote = !doubleQuote; }
            else if (!single && !doubleQuote && c == '(') depth++;
            else if (!single && !doubleQuote && c == ')') depth--;
            if (c == delimiter && !single && !doubleQuote && depth == 0) { parts.add(part.toString()); part.setLength(0); } else part.append(c);
        }
        if (single || doubleQuote || depth != 0) throw new BusinessException("SQL 文件引号或括号未闭合");
        if (part.length() > 0) parts.add(part.toString()); return parts;
    }
    private static String unquote(String value) { return value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1).replace("\"\"", "\"") : value; }

    private static void checkRowLimit(int count) { if (count > MAX_ROWS) throw new BusinessException("单次最多导入 " + MAX_ROWS + " 行"); }

    private static final class LimitedOutputStream extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) { ensure(1); super.write(value); }
        @Override public synchronized void write(byte[] data, int offset, int length) { ensure(length); super.write(data, offset, length); }
        private void ensure(int length) { if ((long) count + length > MAX_FILE_BYTES) throw new BusinessException("导出文件超过 50 MiB，请缩小表的数据范围"); }
    }

    private static int insertRows(Connection connection, String schema, String table, List<Map<String, String>> rows) throws Exception {
        String qualified = MigrationDialect.qualified(schema, table);
        Map<String, Integer> types = new LinkedHashMap<>(); Map<String, String> actualNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (PreparedStatement probe = connection.prepareStatement("SELECT * FROM " + qualified + " WHERE 1=0"); ResultSet empty = probe.executeQuery()) {
            ResultSetMetaData meta = empty.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) { actualNames.put(meta.getColumnLabel(i), meta.getColumnLabel(i)); types.put(meta.getColumnLabel(i), meta.getColumnType(i)); }
        }
        List<String> inputNames = new ArrayList<>(rows.get(0).keySet()); if (inputNames.isEmpty()) throw new BusinessException("导入文件没有表头");
        List<String> columns = new ArrayList<>(); Set<String> unique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : inputNames) {
            String actual = actualNames.get(name); if (actual == null || !unique.add(actual)) throw new BusinessException("文件字段缺失、重复或目标表不存在对应字段：" + name);
            columns.add(actual);
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(qualified).append(" (");
        for (int i = 0; i < columns.size(); i++) { if (i > 0) sql.append(','); sql.append(MigrationDialect.quote(columns.get(i))); }
        sql.append(") VALUES ("); for (int i = 0; i < columns.size(); i++) { if (i > 0) sql.append(','); sql.append('?'); } sql.append(')');
        try (PreparedStatement insert = connection.prepareStatement(sql.toString())) {
            int count = 0;
            for (Map<String, String> row : rows) {
                if (!row.keySet().equals(rows.get(0).keySet())) throw new BusinessException("文件各行字段不一致");
                for (int i = 0; i < columns.size(); i++) bind(insert, i + 1, types.get(columns.get(i)), row.get(inputNames.get(i)));
                insert.addBatch(); count++;
                if (count % 500 == 0) insert.executeBatch();
            }
            insert.executeBatch(); return count;
        }
    }

    private static void bind(PreparedStatement insert, int index, int type, String value) throws Exception {
        if (value == null) { insert.setNull(index, type); return; }
        if (value.isEmpty() && type != Types.CHAR && type != Types.VARCHAR && type != Types.NCHAR && type != Types.NVARCHAR
                && type != Types.CLOB && type != Types.NCLOB && type != Types.LONGVARCHAR && type != Types.LONGNVARCHAR) {
            insert.setNull(index, type); return;
        }
        switch (type) {
            case Types.BLOB: case Types.BINARY: case Types.VARBINARY: case Types.LONGVARBINARY:
                if (!value.startsWith("BASE64:")) throw new BusinessException("二进制字段必须使用 BASE64: 前缀");
                insert.setBytes(index, Base64.getDecoder().decode(value.substring(7))); break;
            case Types.NUMERIC: case Types.DECIMAL: case Types.INTEGER: case Types.BIGINT: case Types.SMALLINT: case Types.TINYINT:
                insert.setBigDecimal(index, new BigDecimal(value)); break;
            case Types.FLOAT: case Types.REAL: case Types.DOUBLE: insert.setDouble(index, Double.parseDouble(value)); break;
            case Types.DATE: if (value.contains(":")) insert.setTimestamp(index, Timestamp.valueOf(value.replace('T', ' '))); else insert.setDate(index, java.sql.Date.valueOf(value)); break;
            case Types.TIMESTAMP: case Types.TIMESTAMP_WITH_TIMEZONE: insert.setTimestamp(index, Timestamp.valueOf(value.replace('T', ' '))); break;
            default: insert.setString(index, value);
        }
    }

    public enum Format {
        SQL("sql", "text/plain"), CSV("csv", "text/csv"), TSV("tsv", "text/tab-separated-values"), XLS("xls", "application/vnd.ms-excel"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), JSON("json", "application/json"), XML("xml", "application/xml");
        public final String extension, mime;
        Format(String extension, String mime) { this.extension = extension; this.mime = mime; }
        static Format parse(String value) { try { return Format.valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception e) { throw new BusinessException("不支持的文件格式"); } }
    }
}
