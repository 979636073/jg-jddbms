package com.jd.biz.controller.data.source.request;

import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.KeyValue;
import com.jd.spi.model.SSHInfo;
import com.jd.spi.model.SSLInfo;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author moji
 * @version ConnectionCreateRequest.java, v 0.1 2022年09月16日 14:23 moji Exp $
 * @date 2022/09/16
 */
@Data
public class DataSourceTestRequest {

    /**
     * 连接别名
     */
    private String alias;

    /**
     * 连接地址
     */
    @NotNull
    private String url;

    /**
     * 连接用户
     */
    @NotNull
    private String user;

    /**
     * 密码
     */
    @NotNull
    private String password;

    /**
     * 数据库连接类型
     */
    @NotNull
    private String type;

    /**
     * 认证类型
     */
    private String authenticationType;

    /**
     * host
     */
    @NotBlank(message = "ip不能为空")
    private String host;

    /**
     * port
     */
    @NotBlank(message = "port不能为空")
    private String port;

    /**
     * ssh
     */
    private SSHInfo ssh;

    /**
     * ssh
     */
    private SSLInfo ssl;

    /**
     * sid
     */
    private String sid;

    /**
     * driver
     */
    private String driver;


    /**
     * jdbc版本
     */
    private String jdbc;

    /**
     * 扩展信息
     */
    private List<KeyValue> extendInfo;

    /**
     * 驱动配置
     */
    private DriverConfig driverConfig;
}
