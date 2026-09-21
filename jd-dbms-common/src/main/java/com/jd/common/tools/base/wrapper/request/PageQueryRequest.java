package com.jd.common.tools.base.wrapper.request;

import java.io.Serializable;

import com.jd.common.tools.base.constant.EasyToolsConstant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;

/**
 * 分页查询的参数
 *
 * @author zhuangjiaju
 * @date 2021/06/26
 */
@Data
@SuperBuilder
@AllArgsConstructor
public class PageQueryRequest implements Serializable {
    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;
    /**
     * 页码
     *
     * @mock 1
     */
    @NotNull(message = "分页页码不能为空")
    private Integer pageNo;
    /**
     * 分页条数
     *
     * @demo 10
     */
    @NotNull(message = "分页大小不能为空")
    @Range(min = 1, max = EasyToolsConstant.MAX_PAGE_SIZE,
        message = "分页大小必须在1-" + EasyToolsConstant.MAX_PAGE_SIZE + "之间")
    private Integer pageSize;

    public PageQueryRequest() {
        this.pageNo = 1;
        this.pageSize = 100;
    }
}
