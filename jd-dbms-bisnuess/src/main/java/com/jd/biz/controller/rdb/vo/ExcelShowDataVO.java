package com.jd.biz.controller.rdb.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author moji
 * @version IndexVO.java, v 0.1 2022年09月16日 17:47 moji Exp $
 * @date 2022/09/16
 */
@Data
public class ExcelShowDataVO {

    /**
     * 需要新增的数据
     */
    private List<Map<String,ExcelDataHighVO>> insertDataList;


    /**
     * 需要更新的数据
     */
    private List<Map<String,ExcelDataHighVO>> updateDataList;


    /**
     * 全部数据
     */
    private List<Map<String,Object>> allDataList;


    /**
     * 表头数据
     */
    private List<String> heardList;

}
