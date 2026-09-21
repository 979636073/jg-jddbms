package com.jd.common.constant;

/**
 * 缓存的key 常量
 * 
 * @author ruoyi
 */
public class CacheConstants
{
    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    /**
     * 单点登录 token redis key
     */
    public static final String SSO_TOKEN_KEY = "sso_tokens:";

    public static final String TOKEN_MAPPING_KEY = "token_mapping";

    public static final String EXPORT_KEY = "export:";

    public static final String IMPORT_KEY = "import:";

    public static final String EXPORT_DMP_KEY = "exportDmp:";

    public static final String IMPORT_DMP_KEY = "importDmp:";

    public static final String IMPORT_TABLE_KEY = "import_table:";

    public static final String PREVIEW_DATA_ADD_KEY = "preview_data_add:";

    public static final String PREVIEW_DATA_ERROR_KEY = "preview_data_error:";

    public static final String PREVIEW_DATA_SUCCESS_KEY = "preview_data_success:";

    public static final String PREVIEW_DATA_UPDATE_KEY = "preview_data_update:";

    public static final String PREVIEW_DATA_ALL_KEY = "preview_data_all:";

    public static final String DROP_OR_ADD_IDENTITY_INSERT_KEY = "identity_insert:";


    public static final String SELECT_TABLE_DETAILS_DATA_SQL = "SELECT_TABLE_DETAILS_DATA_SQL:";

    public static final String SELECT_TABLE_DETAILS_SQL_ = "SELECT_TABLE_DETAILS_SQL:";


    public static final String TABLE_INDEX_SQL_ = "TABLE_INDEX_SQL_:";

    public static final String TABLE_REFERENCED_ = "TABLE_REFERENCED_:";

    public static final String TABLE_UN_REFERENCED_ = "TABLE_UN_REFERENCED_:";
}
