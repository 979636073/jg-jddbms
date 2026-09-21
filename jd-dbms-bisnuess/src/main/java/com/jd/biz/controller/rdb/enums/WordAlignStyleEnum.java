package com.jd.biz.controller.rdb.enums;

import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;

/**
 * @Author: corn
 * @CreateTime: 2024-12-04
 * @Description:
 * @Version: 1.0
 */
public enum WordAlignStyleEnum {

    center("1", STJc.Enum.forString("center")),
    left("2", STJc.Enum.forString("left")),
    right("3", STJc.Enum.forString("right"));

    private String type;

    private STJc.Enum desc;

    WordAlignStyleEnum(String type, STJc.Enum desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public STJc.Enum getDesc() {
        return desc;
    }


    public void setType(String type) {
        this.type = type;
    }

    public void setDesc(STJc.Enum desc) {
        this.desc = desc;
    }

    /**
     * 通过type获取枚举
     * @param type
     * @return
     */
    public static WordAlignStyleEnum getByType(String type) {
        for (WordAlignStyleEnum dbTypeEnum : WordAlignStyleEnum.values()) {
            if (dbTypeEnum.type.equals(type)) {
                return dbTypeEnum;
            }
        }
        return null;
    }

}
