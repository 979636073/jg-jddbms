package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

import java.io.File;
import java.util.List;


@Data
public class DmpImportRequest  extends DataSourceBaseRequest {

    private String dmpUrl;


    private String logUrl;

    /**
     * 文件下载路径
     */
    private String filePath;

    /**
     * 是否是回调状态
     */
    private Boolean status =  false;

    /**
     * 文件名
     */
    private File fileName;


    private List<String> tableList;

    /**
     * from userName
     */
    private String fromName;

    /**
     * to userName
     */
    private String toName;

    private List<String> fromUser;


    private List<String> toUser;

    /** 客户端工具ID；为空时自动选择。 */
    private String dmpToolId;

    /** ORACLE_DATAPUMP、ORACLE_LEGACY 或 DM_DEXP。 */
    private String dmpToolFamily;

    /** 模式级内容选项；为空字段(null)表示全部内容。 */
    private List<String> contentOptions;

}
