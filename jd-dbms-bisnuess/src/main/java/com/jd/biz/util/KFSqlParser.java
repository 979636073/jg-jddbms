package com.jd.biz.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: corn
 * @CreateTime: 2024-12-07
 * @Description:
 * @Version: 1.0
 */
public class KFSqlParser {

    private static String SELECT_FROM = "SELECT(.*?)FROM";


    /**
     * 获取SQL 中从 SELECT - FROM 信息
     * @param sql sql
     * @return String
     */
    public static String extractBetweenSelectAndFrom(String sql) {
        Matcher matcher = Pattern.compile(SELECT_FROM, Pattern.DOTALL).matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).trim();
        } else {
            return null;
        }
    }
}
