package com.jd.biz.controller.data.source.request;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
public class DataSourceCreateRequest {

    /**
     * 主键
     */
    private Long id;

    /**
     * 连接别名
     */
    private String alias;

    /**
     * 连接地址
     */
    @NotBlank(message = "连接地址不能为空")
    private String url;

    /**
     * 连接用户名
     */
    private String user;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 认证类型
     */
    private String authenticationType;

    /**
     * 连接类型
     */
    @NotBlank(message = "连接类型不能为空")
    private String type;

    /**
     * host
     */
    private String host;

    /**
     * port
     */
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


    /**
     * 环境id
     */
    private Long environmentId;



    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 服务类型
     */
    private String serviceType;

    /**
     * 是否保存密码
     */
    private Boolean checked;

}
