package com.jd.plugin.gbase;

import com.jd.plugin.gbase.type.GbaseColumnTypeEnum;
import com.jd.plugin.gbase.value.GeometryValueHandler;
import com.jd.spi.ValueHandler;
import com.jd.spi.jdbc.DefaultValueHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class GbaseValueHandler extends DefaultValueHandler {

    private static final Map<String, ValueHandler> VALUE_HANDLER_MAP = new HashMap<>();

    static {
        VALUE_HANDLER_MAP.put(GbaseColumnTypeEnum.GEOMETRY.name(), new GeometryValueHandler());
    }

    @Override
    public String getString(ResultSet rs, int index, boolean limitSize) throws SQLException {
        Object obj = rs.getObject(index);
        if (obj == null) {
            return null;
        }
        String columnTypeName = rs.getMetaData().getColumnTypeName(index);
        if (GbaseColumnTypeEnum.GEOMETRY.name().equalsIgnoreCase(columnTypeName)
                || GbaseColumnTypeEnum.POINT.name().equalsIgnoreCase(columnTypeName)
                || GbaseColumnTypeEnum.LINESTRING.name().equalsIgnoreCase(columnTypeName)
                || GbaseColumnTypeEnum.POLYGON.name().equalsIgnoreCase(columnTypeName)
                || GbaseColumnTypeEnum.MULTIPOINT.name().equalsIgnoreCase(columnTypeName)
                || GbaseColumnTypeEnum.MULTILINESTRING.name().equalsIgnoreCase(columnTypeName)
                || GbaseColumnTypeEnum.MULTIPOLYGON.name().equalsIgnoreCase(columnTypeName)
        ) {
            ValueHandler handler = VALUE_HANDLER_MAP.get(GbaseColumnTypeEnum.GEOMETRY.name());
            return handler.getString(rs, index, limitSize);
        } else {
            return super.getString(rs, index, limitSize);
        }
    }

}
