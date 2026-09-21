package com.jd.biz.domain.core.impl;

import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Table;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class TestUpdateImpl {

    @Autowired
    private TableServiceImpl tableService;


    public Boolean updateTable(Table oldTable, Table newTable) throws SQLException {
        ListResult<Sql> sqlListResult = tableService.buildSql(oldTable, newTable);
        List<Sql> data = sqlListResult.getData();
        for (Sql datum : data) {
            String sql = datum.getSql();
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(),sql, new DefaultValueHandler());
        }
        return Boolean.TRUE;
    }
}
