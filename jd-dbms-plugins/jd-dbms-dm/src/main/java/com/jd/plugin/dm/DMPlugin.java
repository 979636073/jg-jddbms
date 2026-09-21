package com.jd.plugin.dm;

import com.jd.spi.DBManage;
import com.jd.spi.MetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DBConfig;
import com.jd.spi.util.FileUtils;

public class DMPlugin implements Plugin {
    @Override
    public DBConfig getDBConfig() {
        return FileUtils.readJsonValue(this.getClass(),"dm.json", DBConfig.class);

    }

    @Override
    public MetaData getMetaData() {
        return new DMMetaData();
    }

    @Override
    public DBManage getDBManage() {
        return new DMDBManage();
    }
}
