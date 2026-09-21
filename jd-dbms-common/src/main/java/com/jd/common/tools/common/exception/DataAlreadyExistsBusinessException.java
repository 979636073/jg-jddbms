package com.jd.common.tools.common.exception;


import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import lombok.Getter;

/**
 * Data already exists exception
 *
 * @author Jiaju Zhuang
 */
@Getter
public class DataAlreadyExistsBusinessException extends BusinessException {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;

    public DataAlreadyExistsBusinessException() {
        super("common.dataAlreadyExists");
    }

    public DataAlreadyExistsBusinessException(String key, Object value) {
        super("common.dataAlreadyExistsWithParam", new Object[] {key, value});
    }
}