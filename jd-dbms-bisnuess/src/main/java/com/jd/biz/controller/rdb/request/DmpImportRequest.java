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


}
