package com.jd.biz.domain.api.param;

import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TaskPageParam  extends PageQueryParam implements Serializable {


    private Long userId;

    private List<String> taskType;

    private String taskStatus;

}
