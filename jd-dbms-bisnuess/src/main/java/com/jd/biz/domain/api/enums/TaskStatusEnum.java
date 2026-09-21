package com.jd.biz.domain.api.enums;

public enum TaskStatusEnum {

    INIT("INIT", "初始化", false),
    PROCESSING("PROCESSING", "处理中", false),
    FINISH("FINISH", "完成", true),
    ERROR("ERROR", "失败", true);

    private final String code;
    private final String description;
    private final boolean terminal;

    TaskStatusEnum(String code, String description, boolean terminal) {
        this.code = code;
        this.description = description;
        this.terminal = terminal;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
