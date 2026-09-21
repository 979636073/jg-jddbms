package com.jd.biz.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jd.biz.controller.rdb.enums.WordAlignStyleEnum;
import com.jd.biz.controller.rdb.vo.WordRealTextVO;
import com.jd.biz.controller.rdb.vo.WordStyleConfigVO;
import com.jd.common.constant.GenConstants;
import com.jd.common.tools.base.excption.BusinessException;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: corn
 * @CreateTime: 2024-11-26
 * @Description: word文档工具
 * @Version: 1.0
 */
@Slf4j
public class DocUtils {

    /**
     * 添加自定义样式
     * @param docxDocument
     * @param strStyleId
     * @param headingLevel
     */
    private static void addCustomHeadingStyle(XWPFDocument docxDocument, String strStyleId, int headingLevel) {
        CTStyle ctStyle = CTStyle.Factory.newInstance();
        ctStyle.setStyleId(strStyleId);
        CTString styleName = CTString.Factory.newInstance();
        styleName.setVal(strStyleId);
        ctStyle.setName(styleName);
        CTDecimalNumber indentNumber = CTDecimalNumber.Factory.newInstance();
        indentNumber.setVal(BigInteger.valueOf(headingLevel));
        ctStyle.setUiPriority(indentNumber);
        CTOnOff ctOnOff = CTOnOff.Factory.newInstance();
        ctStyle.setUnhideWhenUsed(ctOnOff);
        ctStyle.setQFormat(ctOnOff);
        CTPPr ctpPr = CTPPr.Factory.newInstance();
        ctpPr.setOutlineLvl(indentNumber);
        ctStyle.setPPr(ctpPr);
        XWPFStyle style = new XWPFStyle(ctStyle);
        XWPFStyles styles = docxDocument.createStyles();
        style.setType(STStyleType.PARAGRAPH);
        styles.addStyle(style);
    }


    /**
     * word 设置 word文件响应流
     * @param doc XWPFDocument
     * @param response HttpServletResponse
     * @param fileName fileName
     * @return XWPFDocument
     */
    public static void responseWord(XWPFDocument doc, HttpServletResponse response, String fileName) throws IOException {
        response.setHeader("content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".docx", CharsetUtil.UTF_8.toString()));
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ServletOutputStream outputStream = response.getOutputStream();
        doc.write(outputStream);
        doc.close();
        outputStream.close();
    }


    /**
     * 生成一个标题和一段落
     * @param xwpfDocument
     * @param wordStyleConfigDO
     * @param wordRealTextDO
     * @return  XWPFDocument
     */
    public static Integer generateDocx(XWPFDocument xwpfDocument, WordStyleConfigVO wordStyleConfigDO, WordRealTextVO wordRealTextDO, Integer index) {
        // 创建内容
        XWPFParagraph paragraph = xwpfDocument.createParagraph();
        XWPFRun titleRun = null;
        if (GenConstants.ONE.equals(wordStyleConfigDO.getIsTitle())) {
            addCustomHeadingStyle(xwpfDocument, "heading " + wordStyleConfigDO.getTitleType(), wordStyleConfigDO.getTitleType());
            paragraph.setStyle("heading " + wordStyleConfigDO.getTitleType());
            titleRun = paragraph.createRun();
            if (StrUtil.isNotBlank(wordRealTextDO.getHeaderInfo())) {
                titleRun.setText(wordRealTextDO.getHeaderInfo() + " " + wordRealTextDO.getName());
            } else {
                titleRun.setText(wordRealTextDO.getName());
            }
        } else {
            if (Objects.nonNull(wordStyleConfigDO.getLineIndentationSize())) {
                if (GenConstants.ONE.equals(wordStyleConfigDO.getLineIndentationType())) {
                    paragraph.setIndentationFirstLine(wordStyleConfigDO.getLineIndentationSize() * 300);
                } else if (GenConstants.TWO.equals(wordStyleConfigDO.getLineIndentationType())) {
                    paragraph.setIndentationLeft(wordStyleConfigDO.getLineIndentationSize() * 300);
                } else if (GenConstants.THREE.equals(wordStyleConfigDO.getLineIndentationType())) {
                    paragraph.setIndentationRight(wordStyleConfigDO.getLineIndentationSize() * 300);
                }
            }
        }
        if (titleRun == null) {
            titleRun = paragraph.createRun();
        }
        if (StrUtil.isBlank(titleRun.getText(0))) {
            if (StrUtil.isNotBlank(wordRealTextDO.getParagraphText())) {
                titleRun.setText(wordRealTextDO.getParagraphText());
            } else {
                String textInfo = wordRealTextDO.getTextInfo();
                if (StrUtil.isNotBlank(wordRealTextDO.getReserveText())) {
                    String[] split = wordRealTextDO.getReserveText().split(",");
                    for (String s : split) {
                        index++;
                        textInfo = textInfo.replaceFirst(s, String.format(GenConstants.reserve, index));
                    }
                }
                titleRun.setText(textInfo);
            }
        }
        titleRun.setBold(false);
        titleRun.setFontSize(Integer.parseInt(wordStyleConfigDO.getFontSize()));
        titleRun.setFontFamily(wordStyleConfigDO.getFontStyle());
        CTPPr pPr = paragraph.getCTP().getPPr();
        if (pPr == null) {
            pPr = paragraph.getCTP().addNewPPr();
        }
        // 设置对齐样式
        if (StrUtil.isNotBlank(wordStyleConfigDO.getAlignStyle())) {
            WordAlignStyleEnum byType = WordAlignStyleEnum.getByType(wordStyleConfigDO.getAlignStyle());
            if (byType != null) {
                CTJc jc = pPr.isSetJc() ? pPr.getJc(): pPr.addNewJc();
                jc.setVal(byType.getDesc());
            }
        }
        // 设置行间距
        if (Objects.nonNull(wordStyleConfigDO.getLineSpace())) {
            paragraph.setSpacingBefore(wordStyleConfigDO.getLineSpace());
        }
        return index;
    }



    /**
     * 生成一个标题和一个表格
     * @param xwpfDocument
     * @param wordStyleConfigDO
     * @param wordRealTextDO
     * @return  XWPFDocument
     */
    public static void generateDocxTable(XWPFDocument xwpfDocument, WordStyleConfigVO wordStyleConfigDO, WordRealTextVO wordRealTextDO) {
        // 创建内容
        XWPFParagraph paragraph = xwpfDocument.createParagraph();
        XWPFRun titleRun = null;
        if (GenConstants.ONE.equals(wordStyleConfigDO.getIsTitle())) {
            addCustomHeadingStyle(xwpfDocument, "heading " + wordStyleConfigDO.getTitleType(), wordStyleConfigDO.getTitleType());
            paragraph.setStyle("heading " + wordStyleConfigDO.getTitleType());
            titleRun = paragraph.createRun();
            titleRun.setStyle("宋体");
            if (StrUtil.isNotBlank(wordRealTextDO.getHeaderInfo())) {
                titleRun.setText(wordRealTextDO.getHeaderInfo() + "  " + wordRealTextDO.getName());
            } else {
                titleRun.setText(wordRealTextDO.getName());
              }
        }
        CTPPr pPr = paragraph.getCTP().getPPr();
        if (pPr == null) {
            pPr = paragraph.getCTP().addNewPPr();
        }
        // 设置对齐样式
        if (StrUtil.isNotBlank(wordStyleConfigDO.getAlignStyle())) {
            WordAlignStyleEnum byType = WordAlignStyleEnum.getByType(wordStyleConfigDO.getAlignStyle());
            if (byType != null) {
                CTJc jc = pPr.isSetJc() ? pPr.getJc(): pPr.addNewJc();
                jc.setVal(byType.getDesc());
            }
        }
        // 设置行间距
        if (Objects.nonNull(wordStyleConfigDO.getLineSpace())) {
            paragraph.setSpacingBefore(wordStyleConfigDO.getLineSpace());
        }
        XWPFTable table = xwpfDocument.createTable();
        table.setWidth("100%");
        if (CollUtil.isEmpty(wordRealTextDO.getHeaderList())) {
            log.warn("生成word表格,列头信息不存在");
            return;
        }
        XWPFTableRow headerRow = table.getRow(0);
        List<String> headerList = wordRealTextDO.getHeaderList();
        for (int i = 0; i < headerList.size(); i++) {
            XWPFTableCell xwpfTableCell = null;
            if (i == 0) {
                xwpfTableCell = headerRow.getCell(i);
            } else {
                xwpfTableCell = headerRow.addNewTableCell();
            }
            XWPFParagraph xwpfParagraph = xwpfTableCell.getParagraphs().get(0);
            XWPFRun run = xwpfParagraph.createRun();
            run.setFontSize(Integer.parseInt(wordStyleConfigDO.getFontSize()));
            run.setStyle("宋体");
            run.setBold(true);
            run.setText(headerList.get(i));
        }
        if (CollUtil.isEmpty(wordRealTextDO.getDataList())) {
            log.warn("生成word表格,表格信息不存在");
            return;
        }
        for (List<String> list : wordRealTextDO.getDataList()) {
            XWPFTableRow row = table.createRow();
            for (int i = 0; i < list.size(); i++) {
                XWPFTableCell cell = row.getCell(i);
                XWPFParagraph xwpfParagraph = cell.getParagraphs().get(0);
                XWPFRun run = xwpfParagraph.createRun();
                run.setFontSize(Integer.parseInt(wordStyleConfigDO.getFontSize()));
                run.setBold(true);
                run.setStyle("宋体");
                run.setText(StrUtil.isBlank(list.get(i)) ? null : list.get(i));
            }
        }
    }


    /**
     * word 占位符值替换
     * @param doc XWPFDocument
     * @param map map
     * @return XWPFDocument
     */
    public static XWPFDocument wordReplacement(XWPFDocument doc, Map<String, String> map) {
        Iterator<XWPFParagraph> paragraphsIterator = doc.getParagraphsIterator();
        while (paragraphsIterator.hasNext()) {
            XWPFParagraph xwpfParagraph = paragraphsIterator.next();
            for (XWPFRun run : xwpfParagraph.getRuns()) {
                String text = run.getText(0);
                if (StrUtil.isNotEmpty(text)) {
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        text = text.replace(entry.getKey(), entry.getValue());
                    }
                    run.setText(text, 0);
                }
            }
        }
        return doc;
    }



    /**
     * 生成多个标题和多个段落
     * @param titleList
     * @param contentList
     * @param filePath
     */
    public static XWPFDocument  generateDocxFile(List<String> titleList, List<String> contentList, String filePath) {
        if (titleList.size() != contentList.size()) {
            throw new BusinessException("title和content数量不匹配");
        }
        XWPFDocument xwpfDocument = new XWPFDocument();
        addCustomHeadingStyle(xwpfDocument, "heading 1", 1);
        for (int i = 0; i < titleList.size(); i++) {
            // 创建一个标题
            XWPFParagraph paragraph = xwpfDocument.createParagraph();
            paragraph.setStyle("heading 1");
            XWPFRun titleRun = paragraph.createRun();
            titleRun.setText(titleList.get(i));
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            CTPPr pPr = paragraph.getCTP().getPPr();
            if (pPr == null) {
                pPr = paragraph.getCTP().addNewPPr();
            }
            // 居中对齐
            CTJc jc = pPr.isSetJc() ? pPr.getJc(): pPr.addNewJc();
            jc.setVal(STJc.CENTER);
            // 创建一个段落
            XWPFParagraph documentParagraph = xwpfDocument.createParagraph();
            XWPFRun run = documentParagraph.createRun();
            run.setText(contentList.get(i));
        }
        return xwpfDocument;
    }

}
