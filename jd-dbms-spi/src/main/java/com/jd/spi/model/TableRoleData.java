package com.jd.spi.model;

import lombok.Data;

@Data
public class TableRoleData {

    private Boolean isAdmin;

    private Boolean isDefault;

    private Boolean isGranted;

    private String role;

}
