package com.jd.web.exception.convertor;

import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.util.ExceptionUtils;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * MethodArgumentTypeMismatchException
 *
 * @author 是仪
 */
public class MethodArgumentTypeMismatchExceptionConvertor
    implements ExceptionConvertor<MethodArgumentTypeMismatchException> {

    @Override
    public ActionResult convert(MethodArgumentTypeMismatchException exception) {
        return ActionResult.fail("common.paramError", I18nUtils.getMessage("common.paramError"), ExceptionUtils.getErrorInfoFromException(exception));
    }
}
