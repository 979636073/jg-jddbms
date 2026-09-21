package com.jd.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DmpExportVo  implements Serializable {
    private static final long serialVersionUID = 837789938654153215L;

    /**
     * 数据源id
     */
    private Long dataSourceId;

    /**
     * DB名称
     */
    private String databaseName;

    /**
     * 表所在空间
     */
    private String schemaName;
    /**
     * 执行状态
     */
    private String status;
    /**
     * dmp地址
     */
    private String DmpUrl;

    /**
     * log地址
     */
    private String logUrl;
    private String msg;

    private Long userId;

    /**
     * 任务中心记录ID
     */
    private Long taskId;

    /**
     * cmd 命令
     */
    private String cmdCommand;

}
