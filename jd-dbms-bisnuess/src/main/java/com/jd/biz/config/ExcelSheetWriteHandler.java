package com.jd.biz.config;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.streaming.SXSSFSheet;

/**
 * 拦截 Excel数据格式 设置为 文本格式
 */
public class ExcelSheetWriteHandler implements SheetWriteHandler {


    private Integer sum;


    @Override
    public void beforeSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        SheetWriteHandler.super.beforeSheetCreate(writeWorkbookHolder, writeSheetHolder);
    }


    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        for (int i = 0; i < sum; i++) {
            SXSSFSheet sheet = (SXSSFSheet) writeSheetHolder.getSheet();
            CellStyle cellStyle = writeWorkbookHolder.getCachedWorkbook().createCellStyle();
            // 49为文本格式
            cellStyle.setDataFormat((short) 49);
            sheet.setDefaultColumnStyle(i, cellStyle);
        }

    }


    public Integer getSum() {
        return sum;
    }

    public void setSum(Integer sum) {
        this.sum = sum;
    }

    public ExcelSheetWriteHandler(int sum) {
        this.sum = sum;
    }
}
