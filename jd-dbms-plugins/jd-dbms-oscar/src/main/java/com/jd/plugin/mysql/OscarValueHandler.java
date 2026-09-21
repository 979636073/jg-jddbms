//package com.jd.plugin.mysql;
//
//import com.jd.plugin.mysql.type.OscarColumnTypeEnum;
//import com.jd.plugin.mysql.value.GeometryValueHandler;
//import com.jd.spi.ValueHandler;
//import com.jd.spi.jdbc.DefaultValueHandler;
//
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.util.HashMap;
//import java.util.Map;
//
//public class OscarValueHandler extends DefaultValueHandler {
//
//    private static final Map<String, ValueHandler> VALUE_HANDLER_MAP = new HashMap<>();
//
//    static {
//        VALUE_HANDLER_MAP.put(OscarColumnTypeEnum.GEOMETRY.name(), new GeometryValueHandler());
//    }
//
//    @Override
//    public String getString(ResultSet rs, int index, boolean limitSize) throws SQLException {
//        Object obj = rs.getObject(index);
//        if (obj == null) {
//            return null;
//        }
//        String columnTypeName = rs.getMetaData().getColumnTypeName(index);
//        if (OscarColumnTypeEnum.POINT.name().equalsIgnoreCase(columnTypeName)
//                || OscarColumnTypeEnum.LINESTRING.name().equalsIgnoreCase(columnTypeName)
//                || OscarColumnTypeEnum.POLYGON.name().equalsIgnoreCase(columnTypeName)
//                || OscarColumnTypeEnum.MULTIPOINT.name().equalsIgnoreCase(columnTypeName)
//                || OscarColumnTypeEnum.MULTILINESTRING.name().equalsIgnoreCase(columnTypeName)
//                || OscarColumnTypeEnum.MULTIPOLYGON.name().equalsIgnoreCase(columnTypeName)
//                || OscarColumnTypeEnum.GEOMETRYCOLLECTION.name().equalsIgnoreCase(columnTypeName)
//        ) {
//            ValueHandler handler = VALUE_HANDLER_MAP.get(OscarColumnTypeEnum.GEOMETRY.name());
//            return handler.getString(rs, index, limitSize);
//        } else {
//            return super.getString(rs, index, limitSize);
//        }
//    }
//
//}
