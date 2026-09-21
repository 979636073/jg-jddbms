package com.jd.spi.util;

import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import org.apache.commons.collections4.CollectionUtils;

public class TableUtils {

    public static TableColumn getTableColumn(Table table,String columnName) {
        if(table == null || CollectionUtils.isEmpty(table.getColumnList())){
            return null ;
        }
        for (TableColumn tableColumn : table.getColumnList()) {
            if(tableColumn.getName().equalsIgnoreCase(columnName)){
                return tableColumn ;
            }
        }
        return null;
    }
}
