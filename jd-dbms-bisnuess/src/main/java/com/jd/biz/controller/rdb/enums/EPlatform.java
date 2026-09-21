package com.jd.biz.controller.rdb.enums;

public enum EPlatform {

    Linux("linux"),
    Mac_os("Mac OS"),
    Kylin("Kylin");

    private String desc;

    private EPlatform(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return desc;
    }
}
