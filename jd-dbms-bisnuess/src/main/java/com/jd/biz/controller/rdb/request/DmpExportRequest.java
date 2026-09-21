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




}
