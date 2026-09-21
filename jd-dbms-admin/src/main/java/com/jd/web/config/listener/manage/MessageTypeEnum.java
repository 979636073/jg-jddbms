package com.jd.web.config.listener.manage;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * 消息类型枚举
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum MessageTypeEnum implements BaseEnum<String> {
    /**
     * 检查是否正常运行
     */
    HEARTBEAT,


    ;



    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getDescription() {
        return this.name();
    }
}
