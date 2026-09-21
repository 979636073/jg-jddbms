package com.jd.biz.controller.rdb.request;

import lombok.Data;

@Data
public class exportFile {

    private String blobStr;
    private String fileName;
    private String prefix;
}
