package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.param.TableSelector;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.MetaData;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import com.jd.spi.sql.Chat2DBContext;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据库表元数据管理
 */
@Service("jdTableService")
public class JdTableService {

    public DataResult<Table> query(TableQueryParam param, TableSelector selector) {
        try {
            MetaData metaSchema = Chat2DBContext.getMetaData();
            List<Table> tables = metaSchema.tables(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
            Table table = null;
            if (!CollectionUtils.isEmpty(tables)) {
                table = tables.get(0);
                List<TableIndex> indexes = metaSchema.indexes(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
                if(indexes.size() > 0){
                    indexes = indexes.stream().filter(t -> !"Normal".equals(t.getType())).collect(Collectors.toList());
                }
                table.setIndexList(indexes);
                table.setColumnList(
                        metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName()));
                List<String> collect = table.getColumnList().stream().filter(c -> c.getPrimaryKey()).map(TableColumn::getName).collect(Collectors.toList());
                // 非空约束
                table.getColumnList().forEach(c -> {
                    if (c.getNullable() == 1 && !collect.contains(c.getName())) {
                        TableIndex tableIndex = new TableIndex();
                        tableIndex.setName(c.getName());
                        tableIndex.setSchemaName(param.getSchemaName());
                        tableIndex.setTableName(param.getTableName());
                        tableIndex.setStatus("VALID");
                        tableIndex.setType("IS NOT NULL");
                        tableIndex.setConstraintsDesc(c.getName().toUpperCase() + "\n IS NOT NULL");
                        tableIndex.setColumn(c.getName());
                        tableIndex.setIsN(Boolean.TRUE);

                    }
                });
                Set<TableIndex> checkSQL = metaSchema.getCheckSQl(Chat2DBContext.getConnection(), param.getSchemaName(), param.getTableName());
                // ddl 建表语句
                String dropSql = Chat2DBContext.getSqlBuilder().dropTableSql(param.getSchemaName(), param.getTableName());
                String ddl = metaSchema.tableDDL(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
                table.setQuerySql(dropSql + "\n" +ddl);
            }
            return DataResult.of(table);
        }catch (Exception e) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE,e.getMessage());
        }
    }

}
