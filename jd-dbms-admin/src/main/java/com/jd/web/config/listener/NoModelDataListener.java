package com.jd.web.config.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;


@Slf4j
public class NoModelDataListener extends AnalysisEventListener<LinkedHashMap<String, String>> {


    private List<LinkedHashMap<String, String>> cachedDataList = new ArrayList<>();

    public NoModelDataListener(List<LinkedHashMap<String, String>> cachedDataList) {
        this.cachedDataList = cachedDataList;
    }

    public NoModelDataListener() {

    }


    @Override
    public void invoke(LinkedHashMap<String, String> integerStringMap, AnalysisContext analysisContext) {
        cachedDataList.add(integerStringMap);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

}
