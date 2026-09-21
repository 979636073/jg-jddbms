
package com.jd.biz.controller.data.source.converter;

import com.jd.biz.controller.data.source.request.SSHTestRequest;
import com.jd.spi.model.SSHInfo;
import org.mapstruct.Mapper;

/**
 * @author jipengfei
 * @version : SSHWebConverter.java
 */
@Mapper(componentModel = "spring")
public abstract class SSHWebConverter {

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract SSHInfo toInfo(SSHTestRequest request);
}