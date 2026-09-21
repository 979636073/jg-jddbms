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
public class SimpleTable implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 表名
     */
    @JsonAlias({"TABLE_NAME"})
    private String name;

    /**
     * 描述
     */
    @JsonAlias({"REMARKS"})

    private String comment;
}
