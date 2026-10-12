package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

import java.util.List;

@Data
public class DmpExportRequest  extends DataSourceBaseRequest {

    /**
     * 导出dmp文件名称
     */
    private String dmpFileName;

    /**
     * 是否是回调状态
     */
    private Boolean status =  Boolean.FALSE;

    /**
     * 导出dmp日志文件名称
     */
    private String logFileName;


    /**
     * 文件下载路径
     */
    private String filePath;

    /**
     * 导出dmp表名称
     */
    private List<String> tableList;

    /**
     * 导出dmp用户名称
     */
    private List<String> schemaList;

    /** 客户端工具ID；为空时自动选择。 */
    private String dmpToolId;

    /** ORACLE_DATAPUMP、ORACLE_LEGACY 或 DM_DEXP。 */
    private String dmpToolFamily;

    /** 模式级内容选项；为空字段(null)表示全部内容。 */
    private List<String> contentOptions;

    /** Oracle Data Pump 导出的目标兼容版本。 */
    private String targetVersion;



}
