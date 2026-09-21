package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import java.util.List;

/**
 * 修改表sql请求
 *
 * @author 是仪
 */
@Data
public class TableRequest extends DataSourceBaseRequest {
    /**
     * 表名称
     */
    private String name;

    /**
     * 表描述
     */
    private String comment;

    /**
     * 列
     */
    private List<TableColumn> columnList;

    /**
     * 索引
     */
    private List<TableIndex> indexList;


    /**
     * 空间名
     */
    private String schemaName;

    /**
     * 数据库名
     */
    private String databaseName;


    private String engine;


    private String charset;


    private String collate;

    private Long incrementValue;

    private String partition;

    private List<String> tableNames;

}
