package com.jd.spi.jdbc;

import cn.hutool.core.io.unit.DataSizeUtil;
import com.alibaba.druid.DbType;
import com.jd.common.constant.Constants;
import com.jd.common.enums.DBTypeEnum;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.ValueHandler;
import com.jd.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Slf4j
public class DefaultValueHandler implements ValueHandler {

    private static final long MAX_RESULT_SIZE = 256 * 1024;

    @Override
    public String getString(ResultSet rs, int index, boolean limitSize) throws SQLException {
        Object obj = rs.getObject(index);
        if (obj == null) {
            return null;
        }
        try {
            if (obj instanceof BigDecimal) {
                BigDecimal bigDecimal = (BigDecimal)obj;
                return bigDecimal.toPlainString();
            } else if (obj instanceof Double) {
                Double d = (Double)obj;
                return BigDecimal.valueOf(d).toPlainString();
            } else if (obj instanceof Float) {
                Float f = (Float)obj;
                return BigDecimal.valueOf(f).toPlainString();
            } else if (obj instanceof Clob) {
                Clob clob = (Clob)obj;
                try (Reader reader = clob.getCharacterStream()) {
                    StringBuilder sb = new StringBuilder();
                    char[] buffer = new char[4096];
                    int read;
                    int remaining = limitSize ? (int) MAX_RESULT_SIZE : Integer.MAX_VALUE;
                    while (remaining > 0 && (read = reader.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                        sb.append(buffer, 0, read);
                        remaining -= read;
                    }
                    return sb.toString();
                }
            } else if (obj instanceof byte[]) {
                byte[] byteData = (byte[])obj;
                return largeStringByteData(byteData, limitSize);
            } else if (obj instanceof Blob) {
                Blob blob= (Blob)obj;
                return largeStringBlob(blob, limitSize);
            } else if (obj instanceof Timestamp || obj instanceof LocalDateTime) {
                return largeTime(obj);
            } else if (obj instanceof RowId) {
                String dbType = Chat2DBContext.getDBConfig().getDbType();
                // 物理行ID
                if (DBTypeEnum.DM.name().equals(dbType)) {
                    return obj.toString();
                } else {
                    byte[] bytes = rs.getBytes(index);
                    return new String(bytes);
                }
            } else {
                return obj.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to parse number:{},{}", index, obj, e);
            return obj.toString();
        }
    }

    private String largeStringBlob(Blob blob, boolean limitSize) throws SQLException {
        if (blob == null) {
            return null;
        }
        long length = blob.length();
        long maxLength = limitSize ? MAX_RESULT_SIZE : length;
        try (InputStream input = blob.getBinaryStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long remaining = maxLength;
            int read;
            while (remaining > 0 && (read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                output.write(buffer, 0, read);
                remaining -= read;
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (java.io.IOException e) {
            throw new SQLException("读取 Blob 失败", e);
        }
    }

    private String largeStringByteData(byte[] byteData, boolean limitSize) throws SQLException {
        if (byteData == null) {
            return null;
        }
        int length = limitSize ? Math.min(byteData.length, (int) MAX_RESULT_SIZE) : byteData.length;
        return Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(byteData, length));
    }

//    private String largeStringClob(Clob clob, boolean limitSize) throws SQLException, IOException {
//        if (clob == null) {
//            return null;
//        }
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clob.getAsciiStream(), StandardCharsets.UTF_8));
//             ByteArrayOutputStream clobValue = new ByteArrayOutputStream()) {
//            char[] chars = new char[4096];
//            int bytesRead;
//            while ((bytesRead = reader.read(chars)) != -1 ) {
//                clobValue.write(new String(chars, 0, bytesRead).getBytes(StandardCharsets.UTF_8));
//            }
//            byte[] bytes = clobValue.toByteArray();
//            return Base64.getEncoder().encodeToString(bytes);
//        }
//    }

    private String largeTime(Object obj) throws SQLException {
        Object timeField = obj; // Assuming a time field of type Object

        LocalDateTime localDateTime;

        if (obj instanceof Timestamp) {
            // Convert a time field of type Object to a LocalDateTime object
            localDateTime = ((Timestamp) timeField).toLocalDateTime();
        } else {
            localDateTime = LocalDateTime.parse(timeField.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }

        // Create a DateTimeFormatter instance and specify the output date and time format
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Format date time
        String formattedDateTime = dtf.format(localDateTime);
        return formattedDateTime;
    }

    private static String largeString(ResultSet rs, int index, boolean limitSize) throws SQLException {
        String result = rs.getString(index);
        if (result == null) {
            return null;

        }
        if (!limitSize) {
            return result;
        }

        if (result.length() > MAX_RESULT_SIZE) {
            return "[ " + DataSizeUtil.format(MAX_RESULT_SIZE) + " of " + DataSizeUtil.format(result.length()) + " ,"
                    + I18nUtils.getMessage("execute.exportCsv") + " ] " + result.substring(0,
                    Math.toIntExact(MAX_RESULT_SIZE));
        }
        return result;
    }
}
