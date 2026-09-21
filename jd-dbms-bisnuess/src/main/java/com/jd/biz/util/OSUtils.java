package com.jd.biz.util;

import com.jd.biz.controller.rdb.enums.EPlatform;

public class OSUtils {

    private static final String OS = System.getProperty("os.name").toLowerCase();


    private static OSUtils _instance = new OSUtils();

    private EPlatform ePlatform;

    private OSUtils() {

    }

    public static boolean isLinux() {
        return OS.indexOf("linux") >=0;
    }

    public static boolean isKylin() {
        return OS.indexOf("isKylin") >=0;
    }

    public static boolean isWindows() {
        return OS.indexOf("win") >=0;
    }

    public static boolean isMacOS() {
        return OS.indexOf("mac") >=0 && OS.indexOf("os") >0 && OS.indexOf("x") > 0;
    }

    public static EPlatform getOSName() {
        if (isLinux()) {
            return _instance.ePlatform = EPlatform.Linux;
        } else if (isKylin()) {
            return _instance.ePlatform = EPlatform.Kylin;
        } else if (isMacOS()) {
            return _instance.ePlatform = EPlatform.Mac_os;
        }
        return null;
    }
}
