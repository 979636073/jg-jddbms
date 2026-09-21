package com.jd.web.exception.convertor;

import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.util.ExceptionUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * MaxUploadSizeExceededException
 *
 * @author 是仪
 */
public class MaxUploadSizeExceededExceptionConvertor implements ExceptionConvertor<MaxUploadSizeExceededException> {

    @Override
    public ActionResult convert(MaxUploadSizeExceededException exception) {
        return ActionResult.fail("common.maxUploadSize", I18nUtils.getMessage("common.maxUploadSize"), ExceptionUtils.getErrorInfoFromException(exception));
    }
}
