package com.jd.biz.domain.core.impl;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import com.jd.biz.controller.rdb.request.TableRequest;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.utils.StringUtils;
import com.jd.plugin.dm.type.DMIndexTypeEnum;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableIndexColumn;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TestCreateImpl {


    public ActionResult createTable(TableRequest request) {
        if(StringUtils.isBlank(request.getName())){
            return ActionResult.fail("表名称不能为空");
        }
        if(request.getColumnList().size()>0){
            try {
                Table table = new Table();
                BeanUtils.copyProperties(request, table);
                if(null == request.getColumnList()){
                    table.setColumnList(new ArrayList<TableColumn>());
                }
                if(null == table.getIndexList()){
                    table.setIndexList(new ArrayList<TableIndex>());
                    TableIndex tableIndex = new TableIndex();
                    tableIndex.setType(DMIndexTypeEnum.PRIMARY_KEY.getName());
                    tableIndex.setSchemaName(table.getSchemaName());
                    tableIndex.setTableName(table.getName());

                    List<TableIndexColumn> tableIndexColumnList = new ArrayList<>();
                    tableIndex.setColumnList(tableIndexColumnList);
                    for(TableColumn tableColumn:table.getColumnList()){//考虑到联合主键
                        if(tableColumn.getPrimaryKey()){
                            tableIndex.setColumn(tableColumn.getName());
                            tableIndex.setName(tableColumn.getName());

                            TableIndexColumn tableIndexColumn = new TableIndexColumn();
                            tableIndexColumn.setColumnName(tableColumn.getName());
                            tableIndex.getColumnList().add(tableIndexColumn);

                        }
                    }
                    table.getIndexList().add(tableIndex);
                }
                String sqlStr = Chat2DBContext.getSqlBuilder().buildCreateTableSql(table);
                String[] sqlArr = sqlStr.split(";");
                for(String sql:sqlArr){
                    if (StrUtil.isNotEmpty(sql)) {
                        SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(),sql.trim(), new DefaultValueHandler());

                    }
                }
                return ActionResult.isSuccess();
            } catch (Exception e) {
                log.warn("创建表失败,异常:{}", e.getMessage());
                return ActionResult.fail(EasyToolsConstant.ERROR_CODE, ExceptionUtil.stacktraceToString(e).split("\\n")[1].replaceAll("\\r", "") ,"创建表失败");
            }
        }else {
            return ActionResult.fail("列信息不能为空");
        }
    }
}
