package com.jd.biz.controller.rdb.doc.export;

import com.jd.biz.domain.api.enums.ExportFileSuffix;
import com.jd.biz.domain.api.enums.ExportTypeEnum;
import com.jd.biz.domain.api.model.TableParameter;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.biz.controller.rdb.doc.DatabaseExportService;
import com.jd.biz.controller.rdb.doc.adaptive.CustomCellWriteHeightConfig;
import com.jd.biz.controller.rdb.doc.adaptive.CustomCellWriteWidthConfig;
import com.jd.biz.controller.rdb.doc.conf.ExportOptions;
import com.jd.biz.controller.rdb.doc.merge.MyMergeExcel;
import com.jd.biz.controller.rdb.doc.style.CustomExcelStyle;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import lombok.SneakyThrows;
import lombok.val;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ExportExcelService
 *
 * @author lzy
 **/
public class ExportExcelService extends DatabaseExportService {

    public ExportExcelService() {
        exportTypeEnum = ExportTypeEnum.EXCEL;
        suffix = ExportFileSuffix.EXCEL.getSuffix();
        contentType = "text/csv";
    }

    @SneakyThrows
    @Override
    public void export(OutputStream outputStream, ExportOptions exportOptions) {
        List<TableParameter> export = new ArrayList<>();
        for (Map.Entry<String, List<TableParameter>> item : listMap.entrySet()) {
            val t = new TableParameter();
            t.setNo(item.getKey()).setColumnComment(MyMergeExcel.NAME);
            export.add(t);
            export.addAll(item.getValue());
        }
        EasyExcel.write(outputStream)
                .registerWriteHandler(new HorizontalCellStyleStrategy(CustomExcelStyle.getHeadStyle(), CustomExcelStyle.getContentWriteCellStyle()))
                .registerWriteHandler(new CustomCellWriteHeightConfig())
                .registerWriteHandler(new CustomCellWriteWidthConfig())
                .registerWriteHandler(new MyMergeExcel())
                .sheet(I18nUtils.getMessage("main.sheetName"))
                .doWrite(export);
    }

}
