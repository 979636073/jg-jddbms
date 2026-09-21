package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Excel表数据导入
 *
 * @author 是仪
 */
@Data
public class ExcelImportRequest extends DataSourceBaseRequest {
    /**
     * 导入文件
     */
    private String file;

    /**
     * 表名称
     */
    private String name;

    /**
     * 页码
     */
    private Integer page;

    /**
     * 条数
     */
    private Integer size;

    /**
     * 数据类型
     */
    private String DataType;

    /**
     * 执行类型
     */
    private String executeType;


    private String engine;


    private String charset;


    private String collate;

    private Long incrementValue;

    private String partition;


    private List<TableColumn> fromHeardList;

    private List<TableColumn> toHeardList;

}
