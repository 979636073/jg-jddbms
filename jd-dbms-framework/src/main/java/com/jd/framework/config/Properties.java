package com.jd.framework.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
@Slf4j
public class Properties {
    private static java.util.Properties prop = new java.util.Properties();

    /**
     * 获取配置文件参数
     * @param key
     * @return
     * @throws Exception
     */
    public static String getValue(String key) throws Exception {
        String value = System.getenv(toEnvironmentName(key));
        if (value == null || "".equals(value)) {
            value = prop.getProperty(key);
        }
        if(value==null || "".equals(value)){
            try {
                value = initProp(key);
            }catch (IOException e) {
                throw new Exception("本地配置文件加载异常");
            }
            if(value==null || "".equals(value)){
                throw new Exception("本地配置文件参数异常");
            }
        }
        return value;
    }

    private static String toEnvironmentName(String key) {
        return "JDDBMS_" + key.toUpperCase().replace('.', '_');
    }

    /**
     * 加载配置文件
     * @param key
     * @return
     * @throws Exception
     */
    private static String initProp(String key) throws Exception {
        InputStream in = null;
        try {

            in = (new Properties()).getClass().getResourceAsStream("/authorize.properties");
            prop.load(in);

        } catch (IOException e) {
            throw new Exception("本地配置文件加载异常");
        }finally {
            try{
                if(in!=null){
                    in.close();
                }
            }catch (IOException e) {
                log.error("close stream error:{}",e);
            }
        }
        return prop.getProperty(key);
    }
}
