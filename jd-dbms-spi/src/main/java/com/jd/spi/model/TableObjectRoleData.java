package com.jd.spi.model;


import lombok.Data;

@Data
public class TableObjectRoleData {

    private String desc;

    private Boolean rule = Boolean.FALSE;

    private Boolean toRule = Boolean.FALSE;

    private String userName;

    private String owner;

    private String tableName;


}
