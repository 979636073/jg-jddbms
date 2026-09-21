package com.jd.common.constant;

public class SQLConstants {

    public static final String SELECT = "SELECT";
    public static final String INSERT = "INSERT";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";


    public static String getNme(String code) {
        String name = "";
        switch (code) {
            case SELECT:
                name = "查询";
                break;
            case INSERT:
                name = "新增";
                break;
            case UPDATE:
                name = "修改";
                break;
            case DELETE:
                name = "删除";
                break;
        }
        return name;
    }
}
