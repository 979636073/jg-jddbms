package com.jd.common.tools.common.exception;

import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import lombok.Getter;

/**
 * 用户登录异常
 *
 * @author Jiaju Zhuang
 */
@Getter
public class NeedLoggedInBusinessException extends BusinessException {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;

    public NeedLoggedInBusinessException() {
        super("common.needLoggedIn");
    }
}