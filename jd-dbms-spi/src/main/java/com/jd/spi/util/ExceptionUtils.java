package com.jd.spi.util;

import java.io.PrintWriter;
import java.io.StringWriter;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * exception utils
 */
@Slf4j
public class ExceptionUtils {

    /**
     * print stack trace
     *
     * @param throwable
     * @return
     */
    public static String getErrorInfoFromException(Throwable throwable) {
        try (StringWriter stringWriter = new StringWriter(); PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
            return stringWriter.toString();
        } catch (Exception e) {
            log.error("ErrorInfoFromException", e);
            return "ErrorInfoFromException";
        }
    }

    public static String getMessage(Throwable e) {
        if (null == e) {
            return "";
        }
        String message = e.getMessage();
        if (StrUtil.isBlank(message)) {
            return "";
        }
        if (message.contains(":")) {
            return message.substring(message.lastIndexOf(":") + 1);
        } else {
            return message;
        }
    }
}
