package com.jd.common.tools.common.exception;


import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import lombok.Getter;

/**
 * Permission Denied
 *
 * @author Jiaju Zhuang
 */
@Getter
public class PermissionDeniedBusinessException extends BusinessException {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;

    public PermissionDeniedBusinessException() {
        super("common.permissionDenied");
    }
}