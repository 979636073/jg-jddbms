package com.jd.biz.domain.core.impl;

import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.Table;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import org.springframework.stereotype.Service;
import java.sql.SQLException;

@Service
public class TestThreeImpl {

    public Boolean dropTable(Table table) throws SQLException {
        String sql = Chat2DBContext.getSqlBuilder().deleteTable(table);
        SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(),sql, new DefaultValueHandler());
        return Boolean.TRUE;
    }
}
