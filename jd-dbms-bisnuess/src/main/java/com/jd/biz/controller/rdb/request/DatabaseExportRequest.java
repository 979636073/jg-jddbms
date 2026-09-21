package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author: zgq
 * @date: February 27, 2024 22:03
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseExportRequest extends DataSourceBaseRequest {
    private Boolean containData;

    private List<String> tableList;
    /**
     * 下载时是否删除
     */
    private Boolean delete;

}
