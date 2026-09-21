package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

import java.util.List;

/**
 * @Author: corn
 * @CreateTime: 2024-12-06
 * @Description:
 * @Version: 1.0
 */

@Data
public class ViewQueryRequest extends DataSourceBaseRequest  {

    private String oldViewName;

    private String newViewName;

    private List<String> newColumns;

    private List<String> oldColumns;
}
