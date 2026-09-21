package com.jd.plugin.oracle;


import com.jd.spi.DBManage;
import com.jd.spi.MetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DBConfig;
import com.jd.spi.util.FileUtils;

public class OraclePlugin implements Plugin {
    @Override
    public DBConfig getDBConfig() {
        return FileUtils.readJsonValue(this.getClass(),"oracle.json", DBConfig.class);

    }

    @Override
    public MetaData getMetaData() {
        return new OracleMetaData();
    }

    @Override
    public DBManage getDBManage() {
        return new OracleDBManage();
    }
}
