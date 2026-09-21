package com.jd.common.tools.common.exception;


import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import lombok.Getter;

/**
 * Parameter exceptions
 *
 * @author Jiaju Zhuang
 */
@Getter
public class ParamBusinessException extends BusinessException {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;

    public ParamBusinessException() {
        super("common.paramError");
    }

    public ParamBusinessException(String paramString) {
        super("common.paramDetailError", new Object[] {paramString});
    }
}