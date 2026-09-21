package com.jd.biz.domain.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Session;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.util.ListUtils;
import com.alibaba.fastjson2.JSON;
import com.jd.biz.controller.rdb.vo.TableImportVo;
import com.jd.common.core.redis.RedisCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NoModelDataAddListener extends AnalysisEventListener<Map<Integer, Object>> {

    /**
     * 每隔50条存储数据库
     *
     * @param integerStringMap
     * @param analysisContext
     */
    private static final int BATCH_COUNT = 50;

    private Db db;

    private String filterName;

    private String filter;

    private String sheetName;


    private List<String> isAutoColumn;

    private Session session;


    private TableImportVo vo;


    @Autowired
    private RedisCache redisCache;

    public NoModelDataAddListener() {
    }

    public NoModelDataAddListener(Db db, String filterName, String filter, String sheetName, List<String> isAutoColumn, Session session, TableImportVo vo) {
        this.db = db;
        this.filterName = filterName;
        this.filter = filter;
        this.sheetName = sheetName;
        this.isAutoColumn = isAutoColumn;
        this.session = session;
        this.vo = vo;
    }

    private List<Entity> cacheDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

    private Map<Integer, Object> header = new HashMap<>();


    @Override
    public void invoke(Map<Integer, Object> data, AnalysisContext analysisContext) {
        log.info(JSON.toJSONString(data));
        if (CollUtil.isEmpty(header)) {
            header = data;
            return;
        }
        Entity e = new Entity();
        e.setTableName(sheetName);
        data.forEach((k, v) -> {
            if (!isAutoColumn.contains((String) header.get(k))) {
                e.set((String) header.get(k), v);
            }
        });
        cacheDataList.add(e);
        if (cacheDataList.size() == 50) {
            saveData();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        saveData();
        header = new HashMap<>();
    }


    private void saveData() {
        try {
            session.beginTransaction();
            db.insert(cacheDataList);
            session.commit();
        } catch (SQLException e) {
            session.quietRollback();
            e.printStackTrace();
            vo.getMessage().add(sheetName + "导入失败！！！" + e.getMessage());
            vo.setErrorNum(vo.getErrorNum() + 1);
            vo.setMessage(vo.getMessage());
        }
    }
}
