package com.jd.biz.controller.rdb.vo;

import com.jd.spi.model.TableColumn;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ExcelShowDataPageVo implements Serializable {
    private static final long serialVersionUID = 837789938654153215L;

    private List<Map<String,Object>> dataList;

    private List<Map<String,ExcelDataHighVO>> updateDataList;

    private List<String> dataIndex;

    private List<TableColumn> heardList;

    private String tableName;

    private String schemaName;

    private String dataType;

    private Integer page;

    private Integer size;

    private Integer total;

}
