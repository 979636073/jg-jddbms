package com.jd.common.tools.common.exception;


import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import lombok.Getter;

/**
 * Data not found exceptions
 *
 * @author Jiaju Zhuang
 */
@Getter
public class DataNotFoundException extends BusinessException {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;

    public DataNotFoundException() {
        super("common.dataNotFound");
    }

}