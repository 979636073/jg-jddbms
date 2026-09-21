package com.jd.framework.config;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;


public class AuthorizeUtil {

    /**
     * 获取登录用户
     * @param ticket_token
     * @return
     */
    public static SsoUser getloginuser(String ticket_token){
        try {
            if (ticket_token == null || "".equals(ticket_token.toString())) {
                return null;
            } else {
                String url = Properties.getValue("sso.url") + "/sso/user?ticket_token=" + ticket_token+
                        "&client_id="+Properties.getValue("sso.client_id")+"&client_secret="+Properties.getValue("sso.client_secret");
//                JSONObject jsonObject = UrlConn.loadGetJSON(Properties.getValue("sso.url") + "/sso/user?ticket_token=" + ticket_token+
//                        "&client_id="+Properties.getValue("sso.client_id")+"&client_secret="+Properties.getValue("sso.client_secret"));
                String result = HttpUtil.get(url);
                JSONObject jsonObject = JSONUtil.parseObj(result);
                String data = retdata(jsonObject);
                return JSONUtil.toBean(data, SsoUser.class);
            }
        }catch (Exception e){
            return null;
        }
    }

    /**
     * 格式化json错误数据
     *
     * @param jsonObj
     * @return
     */
    public static String retdata(JSONObject jsonObj) throws Exception {
        if (Properties.getValue("sso.tip.code_success").equals(jsonObj.get(Properties.getValue("sso.tip.code")).toString())) {
            return jsonObj.get(Properties.getValue("sso.tip.data")).toString();
        } else {
            return null;
        }
    }

}
