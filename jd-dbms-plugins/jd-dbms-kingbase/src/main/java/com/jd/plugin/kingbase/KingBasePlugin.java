package com.jd.plugin.kingbase;

import com.jd.spi.DBManage;
import com.jd.spi.MetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DBConfig;
import com.jd.spi.util.FileUtils;

public class KingBasePlugin implements Plugin {
    @Override
    public DBConfig getDBConfig() {
        return FileUtils.readJsonValue(this.getClass(),"kingbase.json", DBConfig.class);
    }

    @Override
    public MetaData getMetaData() {
        return new KingBaseMetaData();
    }

    @Override
    public DBManage getDBManage() {
        return new KingBaseDBManage();
    }
}
