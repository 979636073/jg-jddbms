package com.jd.biz.controller.rdb.vo;

import com.jd.spi.model.Database;
import com.jd.spi.model.Schema;
import lombok.Data;

import java.util.List;

@Data
public class MetaSchemaVO {
    /**
     * database list
     */
    private List<Database> databases;

    /**
     * schema list
     */
    private List<Schema> schemas;
}
