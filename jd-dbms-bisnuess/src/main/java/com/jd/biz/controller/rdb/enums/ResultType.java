package com.jd.biz.controller.rdb.enums;

public enum ResultType {

    ONE(1, "列信息"),
    TWO(2, "列信息详情");

    private Integer type;

    private String desc;

    ResultType(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }


    public void setType(Integer type) {
        this.type = type;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
