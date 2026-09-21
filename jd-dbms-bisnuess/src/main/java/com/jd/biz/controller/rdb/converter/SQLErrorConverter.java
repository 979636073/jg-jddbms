package com.jd.biz.controller.rdb.converter;

/**
 * @Author: corn
 * @CreateTime: 2024-11-06
 * @Description: 异常转换
 * @Version: 1.0
 */
public class SQLErrorConverter {
    public static String transfer(String message){
        return message.split("\\n")[1].replaceAll("\\r", ";");
    }
}
