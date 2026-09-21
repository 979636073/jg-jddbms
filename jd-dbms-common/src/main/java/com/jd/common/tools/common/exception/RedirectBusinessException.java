package com.jd.common.tools.common.exception;


import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import lombok.Getter;

/**
 * 需要重定向的业务异常
 *
 * @author Jiaju Zhuang
 */
@Getter
public class RedirectBusinessException extends BusinessException {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;
    private final String redirect;

    public RedirectBusinessException(String redirect) {
        super("common.redirect");
        this.redirect = redirect;
    }
}