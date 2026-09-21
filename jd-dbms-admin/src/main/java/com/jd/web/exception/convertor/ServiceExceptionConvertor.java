package com.jd.web.exception.convertor;

import com.jd.common.exception.ServiceException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.spi.util.ExceptionUtils;

public class ServiceExceptionConvertor implements ExceptionConvertor<ServiceException> {

    @Override
    public ActionResult convert(ServiceException exception) {
        return ActionResult.fail("500", exception.getMessage(),
                ExceptionUtils.getErrorInfoFromException(exception));
    }
}
