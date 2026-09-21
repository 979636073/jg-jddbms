package com.jd.plugin.gbase;


import com.jd.spi.DBManage;
import com.jd.spi.MetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DBConfig;
import com.jd.spi.util.FileUtils;

public class GbasePlugin implements Plugin {
    @Override
    public DBConfig getDBConfig() {
        return FileUtils.readJsonValue(this.getClass(),"gbase.json", DBConfig.class);

    }

    @Override
    public MetaData getMetaData() {
        return new GbaseMetaData();
    }

    @Override
    public DBManage getDBManage() {
        return new GbaseManage();
    }
}
