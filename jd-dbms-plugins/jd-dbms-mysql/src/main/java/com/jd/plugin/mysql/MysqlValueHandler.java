package com.jd.plugin.mysql;

import com.jd.common.enums.HttpMethod;
import com.jd.plugin.mysql.type.MysqlColumnTypeEnum;
import com.jd.plugin.mysql.value.GeometryValueHandler;
import com.jd.spi.ValueHandler;
import com.jd.spi.jdbc.DefaultValueHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MysqlValueHandler extends DefaultValueHandler {

    private static final Map<String, ValueHandler> VALUE_HANDLER_MAP = new HashMap<>();

    static {
        VALUE_HANDLER_MAP.put(MysqlColumnTypeEnum.GEOMETRY.name(), new GeometryValueHandler());
    }

    @Override
    public String getString(ResultSet rs, int index, boolean limitSize) throws SQLException {
        Object obj = rs.getObject(index);
        if (obj == null) {
            return null;
        }
        String columnTypeName = rs.getMetaData().getColumnTypeName(index);
        if (MysqlColumnTypeEnum.GEOMETRY.name().equalsIgnoreCase(columnTypeName)
                || MysqlColumnTypeEnum.POINT.name().equalsIgnoreCase(columnTypeName)
                || MysqlColumnTypeEnum.LINESTRING.name().equalsIgnoreCase(columnTypeName)
                || MysqlColumnTypeEnum.POLYGON.name().equalsIgnoreCase(columnTypeName)
                || MysqlColumnTypeEnum.MULTIPOINT.name().equalsIgnoreCase(columnTypeName)
                || MysqlColumnTypeEnum.MULTILINESTRING.name().equalsIgnoreCase(columnTypeName)
                || MysqlColumnTypeEnum.MULTIPOLYGON.name().equalsIgnoreCase(columnTypeName)
                || MysqlColumnTypeEnum.GEOMETRYCOLLECTION.name().equalsIgnoreCase(columnTypeName)
        ) {
            ValueHandler handler = VALUE_HANDLER_MAP.get(MysqlColumnTypeEnum.GEOMETRY.name());
            return handler.getString(rs, index, limitSize);
        } else {
            return super.getString(rs, index, limitSize);
        }
    }

}
