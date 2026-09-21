package com.jd.web.config.config;

import org.apache.poi.ss.SpreadsheetVersion;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.lang.reflect.Field;

/**
 * 统一修复 Excel 列的最大长度
 */
@Component
public class ApplicationCommandLine implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        SpreadsheetVersion spreadsheetVersion = SpreadsheetVersion.EXCEL2007;
        Field maxTextLength = spreadsheetVersion.getClass().getDeclaredField("_maxTextLength");
        maxTextLength.setAccessible(true);
        maxTextLength.set(spreadsheetVersion, Integer.MAX_VALUE);
    }
}
