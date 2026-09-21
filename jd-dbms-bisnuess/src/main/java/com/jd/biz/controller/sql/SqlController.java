package com.jd.biz.controller.sql;


import cn.hutool.core.util.StrUtil;
import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.sql.request.FilterSqlFormatRequest;
import com.jd.biz.controller.sql.request.SortSqlFormatRequest;
import com.jd.biz.controller.sql.request.SqlFormatRequest;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.MetaData;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;


/**
 * SQL Controller
 */
@ConnectionInfoAspect
@RequestMapping("/api/sql")
@RestController
public class SqlController {

    /**
     * SQL Format
     *
     * @param sqlFormatRequest
     * @return
     */
    @GetMapping("/format")
    public DataResult<String> list(@Valid SqlFormatRequest sqlFormatRequest) {
        String sql = sqlFormatRequest.getSql();
        try {
            sql = SqlFormatter.format(sql);
        } catch (Exception e) {
            // ignore
        }
        return DataResult.of(sql);
    }

    /**
     * 格式化排序sql
     * @param sqlFormatRequest
     * @return
     */
    @GetMapping("/sortSqlFormat")
    public DataResult<String> sortSqlFormat(@Valid SortSqlFormatRequest sqlFormatRequest) {
        String dbType = Chat2DBContext.getConnectInfo().getDbType();
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
        MetaData metaData = Chat2DBContext.getMetaData();
        sqlFormatRequest.setColumnName(metaData.getMetaDataName(sqlFormatRequest.getColumnName()));
        String sql;
        if ((dbType.equalsIgnoreCase(DBTypeEnum.DM.name()) && dbType.equalsIgnoreCase(DBTypeEnum.ORACLE.name())) || StrUtil.isNotEmpty(sqlFormatRequest.getNullType())){
            sql = sqlFormatRequest.getColumnName()+" " + sqlFormatRequest.getSorType() + " NULLS " + sqlFormatRequest.getNullType();

        }else{
            sql = sqlFormatRequest.getColumnName()+" " + sqlFormatRequest.getSorType();
        }

        return DataResult.of(sql);
    }


    @GetMapping("/filterSqlFormat")
    public DataResult<String> filterSqlFormat(@Valid FilterSqlFormatRequest sqlFormatRequest) {
        MetaData metaData = Chat2DBContext.getMetaData();
        sqlFormatRequest.setColumnName(metaData.getMetaDataName(sqlFormatRequest.getColumnName()));
        String sql="";
        if (sqlFormatRequest.getFilterType().equalsIgnoreCase("between")){
            if (sqlFormatRequest.getStartValue().contains("\'")){
                sql=sqlFormatRequest.getColumnName() + " " + sqlFormatRequest.getFilterType() + " " + sqlFormatRequest.getStartValue() + " and " ;
            }else{
                sql=sqlFormatRequest.getColumnName() + " " + sqlFormatRequest.getFilterType() + " \'" + sqlFormatRequest.getStartValue() + "\' and " ;
            }
            if (sqlFormatRequest.getEndValue().contains("\'")){
                sql= sql+sqlFormatRequest.getEndValue();
            }else{
                sql= sql+"\'"+sqlFormatRequest.getEndValue()+"\'";
            }
        }else if(sqlFormatRequest.getFilterType().equalsIgnoreCase("in")
                ||sqlFormatRequest.getFilterType().equalsIgnoreCase("not in")){
            String sqls="";
            if (StrUtil.isNotEmpty(sqlFormatRequest.getStartValue())){
                String[] split = sqlFormatRequest.getStartValue().split(",");
                for (int i = 0;i<split.length;i++){
                    if (split[i].contains("\'")){
                        sqls=sqls+split[i]+",";
                    }else{
                        sqls=sqls+"\'"+split[i]+"\',";
                    }
                }
            }
            sql = sqlFormatRequest.getColumnName() + sqlFormatRequest.getFilterType() + " (" + sqls + ")";
        }else{
            sql = sqlFormatRequest.getColumnName() + sqlFormatRequest.getFilterType() + "\'"+sqlFormatRequest.getStartValue()+"\'";
        }
        return DataResult.of(sql);
    }







}
