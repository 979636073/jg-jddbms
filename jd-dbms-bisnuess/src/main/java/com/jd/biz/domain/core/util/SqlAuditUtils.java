package com.jd.biz.domain.core.util;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL audit formatting helpers.
 */
public final class SqlAuditUtils {

    private static final String MASK = "'******'";
    private static final int MAX_ERROR_LENGTH = 512;
    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:password|passwd|pwd)\\b\\s*(?::=|=>|=|:)\\s*)(?:'[^']*'|\"[^\"]*\"|[^\\s,;]+)");
    private static final Pattern PASSWORD_LITERAL = Pattern.compile(
            "(?i)(\\b(?:password|passwd|pwd)\\b\\s*)(?:'[^']*'|\"[^\"]*\")");
    private static final Pattern IDENTIFIED_BY = Pattern.compile(
            "(?i)(\\bidentified\\s+by\\s+)(?:'[^']*'|\"[^\"]*\"|[^\\s,;]+)");
    private static final Pattern CONNECT_PASSWORD = Pattern.compile(
            "(?i)(\\bconnect\\s+[^/\\s;]+/)([^\\s;]+)");
    private static final Pattern LEADING_COMMENTS = Pattern.compile(
            "(?s)^(?:\\s|--[^\\r\\n]*(?:\\r?\\n|$)|/\\*.*?\\*/)+");
    private static final Pattern FIRST_KEYWORD = Pattern.compile("^([a-zA-Z]+)");

    private SqlAuditUtils() {
    }

    public static String sanitizeSql(String sql) {
        if (sql == null) {
            return null;
        }
        String sanitized = PASSWORD_ASSIGNMENT.matcher(sql).replaceAll("$1" + MASK);
        sanitized = PASSWORD_LITERAL.matcher(sanitized).replaceAll("$1" + MASK);
        sanitized = IDENTIFIED_BY.matcher(sanitized).replaceAll("$1" + MASK);
        return CONNECT_PASSWORD.matcher(sanitized).replaceAll("$1******");
    }

    public static String sanitizeErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        String sanitized = sanitizeSql(message).replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.length() <= MAX_ERROR_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_ERROR_LENGTH);
    }

    public static String resolveSqlType(String reportedType, String sql) {
        if (StrUtil.isNotBlank(reportedType) && !"UNKNOWN".equalsIgnoreCase(reportedType)) {
            return reportedType.toUpperCase(Locale.ROOT);
        }
        if (StrUtil.isBlank(sql)) {
            return "UNKNOWN";
        }
        String normalized = LEADING_COMMENTS.matcher(sql).replaceFirst("").trim();
        Matcher matcher = FIRST_KEYWORD.matcher(normalized);
        if (!matcher.find()) {
            return "UNKNOWN";
        }
        String keyword = matcher.group(1).toUpperCase(Locale.ROOT);
        return "WITH".equals(keyword) ? "SELECT" : keyword;
    }
}
