package com.jd.biz.domain.core.util;


import org.apache.commons.lang3.StringUtils;

public class MetaNameUtils {

    private static final int MAX_IDENTIFIER_LENGTH = 128;

    public static String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException("数据库标识符不能为空");
        }
        String value = getMetaName(identifier).trim();
        if (value.length() > MAX_IDENTIFIER_LENGTH || value.indexOf('.') >= 0
                || value.indexOf(';') >= 0 || value.indexOf('\u0000') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("数据库标识符不合法");
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String getMetaName(String tableName) {
        if(StringUtils.isBlank(tableName)){
            return tableName;
        }
        if(tableName.startsWith("`") && tableName.endsWith("`")){
            return tableName.substring(1,tableName.length()-1);
        }
        if(tableName.startsWith("\"") && tableName.endsWith("\"")){
            return tableName.substring(1,tableName.length()-1);
        }
        if(tableName.startsWith("'") && tableName.endsWith("'")){
            return tableName.substring(1,tableName.length()-1);
        }
        if(tableName.startsWith("[") && tableName.endsWith("]")){
            return tableName.substring(1,tableName.length()-1);
        }
        return tableName;
    }
}
