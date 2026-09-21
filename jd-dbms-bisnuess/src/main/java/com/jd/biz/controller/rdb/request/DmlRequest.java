package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceConsoleRequestInfo;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author moji
 * @version TableManageRequest.java, v 0.1 2022年09月16日 17:55 moji Exp $
 * @date 2022/09/16
 */
@Data
public class DmlRequest extends DataSourceBaseRequest implements DataSourceConsoleRequestInfo {

    /**
     * sql语句
     */
    @NotNull
    private String sql;

    /**
     * 控制台id
     */
    @NotNull
    private Long consoleId;

    /**
     * 分页编码
     * 只有select语句才有
     */
    private Integer pageNo;

    /**
     * 分页大小
     * 只有select语句才有
     */
    private Integer pageSize;

    /**
     * 是否是查询模版
     */
    private Boolean queryTemplate = false;

    /**
     * 返回全部数据
     * 只有select语句才有
     */
    private Boolean pageSizeAll;

    /**
     * 是否跳过精确总数查询，适用于表数据预览。
     */
    private Boolean skipCount = false;


    /**
     * sessionId
     */
    private String sessionId;

    /**
     * isCommit
     */
    private Boolean isCommit = false;

    /**
     * isRollback
     */
    private Boolean isRollback = false;

    /**
     * 关闭所有会话
     */
    private Boolean isAllDelete = false;

    /**
     * 排序升序降序 true升序
     */
    private Boolean isAsc;

    /**
     * 设置是否分页
     */
    private Boolean isPage =  false;

    /**
     * 设置是否分页
     */
    private Boolean isLog =  false;

    /**
     * 出错是否执行
     */
    private Boolean isErrorExecute =  false;

    /**
     * 排序字段
     */
    private String orderByColumn;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 是否查数据列
     */
    private Boolean isColumn = true;

    /**
     * 是否来源于数据查询
     */
    private Boolean isDataView = false;

    /**
     * 是否执行过程编译
     */
    private Boolean isExecuteCompile = false;

    /**
     * Whether the user explicitly confirmed SQL identified as high risk.
     */
    private Boolean confirmDangerousSql = false;

}
