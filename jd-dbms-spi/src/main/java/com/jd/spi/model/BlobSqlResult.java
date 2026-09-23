package com.jd.spi.model;

import lombok.Data;

import java.util.List;

@Data
public class BlobSqlResult {

    private String dataSql;

    private List<String> blobValues;

    /**
     * Parameter data types, in the same order as {@link #blobValues}.
     */
    private List<String> parameterTypes;
}
