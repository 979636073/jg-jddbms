package com.jd.spi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleColumn implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 列名
     */
    @JsonAlias({"COLUMN_NAME"})
    private String name;


    @JsonAlias({"TYPE_NAME"})
    private String columnType;

    /**
     * 注释
     */
    @JsonAlias({"REMARKS"})
    private String comment;
}
