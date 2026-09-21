package com.jd.plugin.mysql;

import com.jd.spi.DBManage;
import com.jd.spi.MetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DBConfig;
import com.jd.spi.util.FileUtils;

public class OscarPlugin implements Plugin {

    @Override
    public DBConfig getDBConfig() {
        return FileUtils.readJsonValue(this.getClass(),"oscar.json", DBConfig.class);
    }

    @Override
    public MetaData getMetaData() {
        return new OscarMetaData();
    }

    @Override
    public DBManage getDBManage() {
        return new OscarDBManage();
    }
}
