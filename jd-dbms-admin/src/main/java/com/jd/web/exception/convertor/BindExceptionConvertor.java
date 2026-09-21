package com.jd.web.exception.convertor;

import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.spi.util.ExceptionUtils;
import org.springframework.validation.BindException;

/**
 * BindException
 *
 * @author 是仪
 */
public class BindExceptionConvertor implements ExceptionConvertor<BindException> {

    @Override
    public ActionResult convert(BindException exception) {
        String message = ExceptionConvertorUtils.buildMessage(exception.getBindingResult());
        return ActionResult.fail("common.paramError", message, ExceptionUtils.getErrorInfoFromException(exception));
    }
}
