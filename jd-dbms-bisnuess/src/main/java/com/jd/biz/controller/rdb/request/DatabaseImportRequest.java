package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author: wangjinlong
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseImportRequest extends DataSourceBaseRequest {
    private String importUrl;
}
