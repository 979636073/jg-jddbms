#设置数据单元格格式为文本
.registerWriteHandler(new CellWriteHandler() {
@Override
public void afterCellCreate(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, Cell cell, Head head, Integer relativeRowIndex, Boolean isHead) {
Sheet sheet = writeSheetHolder.getSheet();
CellStyle cellStyle = sheet.getWorkbook().createCellStyle();
cellStyle.setDataFormat((short) 49);
cell.setCellStyle(cellStyle);
}
});


#设置空格单元格格式为文本
.registerWriteHandler(new SheetWriteHandler() {
@Override
public void beforeSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
SheetWriteHandler.super.beforeSheetCreate(writeWorkbookHolder, writeSheetHolder);
}

@Override
public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
    SXSSFSheet sheet = (SXSSFSheet)writeSheetHolder.getSheet();
    CellStyle cellStyle = writeWorkbookHolder.getCachedWorkbook().createCellStyle();
    cellStyle.setDataFormat((writeWorkbookHolder.getCachedWorkbook().createDataFormat().getFormat("@")));
    for (int i = 0; i < headerList.size(); i++) {
        sheet.setDefaultColumnStyle(i,cellStyle);
    }
}
});