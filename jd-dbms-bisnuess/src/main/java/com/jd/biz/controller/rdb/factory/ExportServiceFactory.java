package com.jd.biz.controller.rdb.factory;

import com.jd.biz.domain.api.enums.ExportTypeEnum;
import com.jd.biz.controller.rdb.doc.export.*;
import lombok.SneakyThrows;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExportServiceFactory
 *
 * @author lzy
 **/
public class ExportServiceFactory {

    /**
     * Export实现类缓存池
     */
    private static final Map<String, Class<?>> REPORT_POOL = new ConcurrentHashMap<>(8);




    static {
        REPORT_POOL.put(ExportTypeEnum.EXCEL.name(), ExportExcelService.class);
        REPORT_POOL.put(ExportTypeEnum.WORD.name(), ExportWordSuperService.class);
        REPORT_POOL.put(ExportTypeEnum.MARKDOWN.name(), ExportMarkdownService.class);
        REPORT_POOL.put(ExportTypeEnum.HTML.name(), ExportHtmlService.class);
        REPORT_POOL.put(ExportTypeEnum.PDF.name(), ExportPdfService.class);
    }

    /**
     * 获取对应接口
     *
     * @param type 报表类型
     * @return Class
     */
    @SneakyThrows
    public static Class<?> get(String type) {
        Class<?> dataResult = REPORT_POOL.get(type);
        if (dataResult == null) {
            throw new ClassNotFoundException("no ExportUI was found");
        } else {
            return dataResult;
        }
    }
}
