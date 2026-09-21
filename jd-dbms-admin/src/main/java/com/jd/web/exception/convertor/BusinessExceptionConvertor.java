package com.jd.web.exception.convertor;

import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.util.ExceptionUtils;

/**
 * BusinessException
 *
 * @author 是仪
 */
public class BusinessExceptionConvertor implements ExceptionConvertor<BusinessException> {

    @Override
    public ActionResult convert(BusinessException exception) {
        return ActionResult.fail(exception.getCode(), I18nUtils.getMessage(exception.getCode(), exception.getArgs()),
                ExceptionUtils.getErrorInfoFromException(exception));
    }
}
