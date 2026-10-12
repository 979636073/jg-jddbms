package com.jd.plugin.dm;

import com.alibaba.druid.DbType;
import com.jd.plugin.dm.builder.DMSqlBuilder;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.util.SqlUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class DMSqlBuilderCommentTest {

    @Test
    public void modifyTableKeepsSemicolonInsideEscapedComment() {
        Table oldTable = table("old");
        Table newTable = table("owner's; note");
        String sql = new DMSqlBuilder().buildModifyTaleSql(oldTable, newTable);
        Assert.assertEquals("COMMENT ON TABLE \"S\".\"T\" IS 'owner''s; note';", sql);
    }

    @Test
    public void createTableEscapesCommentAndQuotedIdentifier() {
        Table table = table("owner's; note");
        table.setName("T\"X");
        TableColumn column = new TableColumn();
        column.setName("C\"X");
        column.setSchemaName("S");
        column.setTableName("T\"X");
        column.setColumnType("VARCHAR");
        column.setColumnSize(20);
        column.setComment("it's; valid");
        table.setColumnList(Collections.singletonList(column));
        String sql = new DMSqlBuilder().buildCreateTableSql(table);
        Assert.assertTrue(sql.contains("CREATE TABLE \"S\".\"T\"\"X\""));
        Assert.assertTrue(sql.contains("\"C\"\"X\" VARCHAR"));
        Assert.assertTrue(sql.contains("COMMENT ON COLUMN \"S\".\"T\"\"X\".\"C\"\"X\" IS 'it''s; valid';"));
        Assert.assertTrue(sql.contains("COMMENT ON TABLE \"S\".\"T\"\"X\" IS 'owner''s; note';"));
        Assert.assertEquals(3, SqlUtils.parse(sql, DbType.dm).size());
    }

    private static Table table(String comment) {
        Table table = new Table();
        table.setSchemaName("S");
        table.setName("T");
        table.setComment(comment);
        table.setColumnList(Collections.emptyList());
        table.setIndexList(Collections.emptyList());
        return table;
    }
}
