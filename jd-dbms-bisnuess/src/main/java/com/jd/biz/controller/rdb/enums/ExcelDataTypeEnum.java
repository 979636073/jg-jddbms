package com.jd.biz.controller.rdb.enums;


public enum ExcelDataTypeEnum {
    ADD,UPDATE,DELETE,ERROR,SUCCESS;


    /**
     * 通过type获取枚举
     * @param type
     * @return
     */
    public static ExcelDataTypeEnum getByType(String type) {
        for (ExcelDataTypeEnum ruleType : ExcelDataTypeEnum.values()) {
            if (ruleType.name().equals(type)) {
                return ruleType;
            }
        }
        return null;
    }
}
