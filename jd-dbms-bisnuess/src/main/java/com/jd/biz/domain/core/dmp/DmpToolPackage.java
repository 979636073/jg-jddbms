package com.jd.biz.domain.core.dmp;

import lombok.Data;

import java.io.Serializable;

@Data
public class DmpToolPackage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String databaseType;
    private String toolFamily;
    private String version;
    private String os;
    private String arch;
    private String exportExecutable;
    private String importExecutable;
    private String packagePath;
    private String originalFileName;
    private String checksum;
    private Boolean enabled;
    private String status;
    private String statusMessage;
    private String createdTime;
    private Boolean currentPlatformCompatible;
}
