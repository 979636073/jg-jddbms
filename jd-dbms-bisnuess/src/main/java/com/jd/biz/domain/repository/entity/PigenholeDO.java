package com.jd.biz.domain.repository.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("PIGENHOLE")
public class PigenholeDO implements Serializable {
    private static final long serialVersionUID = 8377899386569086415L;
    /**
     * 主键
     */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /**
     * 用户
     */
    private String userName;

    /**
     * 过滤条件
     */
    private String filter;

    /**
     * 表名
     */
    private String tables;
    /**
     * 修改时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    private Data updateTime;

    /**
     * 归档任务名称
     */
    private String taskName;



}
