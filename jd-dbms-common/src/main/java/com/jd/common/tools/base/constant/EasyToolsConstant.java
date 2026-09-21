package com.jd.common.tools.base.constant;

/**
 * 常量
 *
 * @author 是仪
 */
public interface EasyToolsConstant {

    String ERROR_CODE = "505";

    /**
     * 日志的追踪id
     */
    String LOG_TRACE_ID = "EAGLEEYE_TRACE_ID";

    /**
     * 最大分页大小
     */
    int MAX_PAGE_SIZE = 1000;

    /**
     * 序列化id
     */
    long SERIAL_VERSION_UID = 1L;

    /**
     * 最大循环次数 防止很多循环进入死循环
     */
    int MAXIMUM_ITERATIONS = 10 * 1000;

    /**
     * 最大导出数据数
     */
    int MAX_EXPORT_SIZE = 1000;
    /**
     * 插入标记
     */
    String INSERT_MARK = "--JDMS INSERT \n";

    String UTF8_BOM="\uFEFF";

}
