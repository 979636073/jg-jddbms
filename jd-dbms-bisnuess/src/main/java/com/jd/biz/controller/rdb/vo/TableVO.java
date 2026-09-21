package com.jd.biz.controller.rdb.vo;

import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableDetails;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableUserRole;
import lombok.Data;

import java.util.List;

/**
 * @author moji
 * @version TableVO.java, v 0.1 2022年09月16日 17:16 moji Exp $
 * @date 2022/09/16
 */
@Data
public class TableVO {

    /**
     * 表名称
     */
    private String name;

    /**
     * 表描述
     */
    private String comment;

    /**
     * 列
     */
    private List<TableColumn> columnList;

    /**
     * 索引
     */
    private List<TableIndex> indexList;

    /**
     * 是否已经被固定
     */
    private boolean pinned;

    /**
     * ddl
     */
    private String ddl;


    /**
     * 表详情信息
     */
    private TableDetails tableDetails;

    /**
     * 用户角色
     */
    private List<TableUserRole> tableUserRoles;

    /**
     * 构建的SQL
     */
    private String querySql;
}
