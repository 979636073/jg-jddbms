package com.jd.web.exception.convertor;

import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.spi.util.ExceptionUtils;

/**
 * 参数异常 目前包括
 * ConstraintViolationException
 * MissingServletRequestParameterException
 * IllegalArgumentException
 *
 * @author 是仪
 */
public class ParamExceptionConvertor implements ExceptionConvertor<Throwable> {

    @Override
    public ActionResult convert(Throwable exception) {
        return ActionResult.fail("common.paramError", exception.getMessage(), ExceptionUtils.getErrorInfoFromException(exception));
    }
}
