package com.jd.biz.controller.driver.request;

import lombok.Data;

import java.util.List;

@Data
public class JdbcDriverRequest {
    String jdbcDriverClass;
    String dbType;

    List<String> jdbcDriver;
}
