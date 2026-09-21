package com.jd.web.exception.convertor;

import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.util.ExceptionUtils;

/**
 * 默认的异常处理
 * 直接抛出系统异常
 *
 * @author 是仪
 */
public class DefaultExceptionConvertor implements ExceptionConvertor<Throwable> {

    @Override
    public ActionResult convert(Throwable exception) {
        return ActionResult.fail("common.systemError", I18nUtils.getMessage("common.systemError"), ExceptionUtils.getErrorInfoFromException(exception));
    }
}
