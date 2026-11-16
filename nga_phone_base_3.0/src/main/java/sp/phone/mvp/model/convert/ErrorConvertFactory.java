package sp.phone.mvp.model.convert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.net.UnknownHostException;

import okhttp3.ResponseBody;
import retrofit2.HttpException;
import retrofit2.Response;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ContextUtils;

/**
 * Created by Justwen on 2017/11/23.
 */
public class ErrorConvertFactory {

    public static String getErrorMessage(String js) {
        if (js.isEmpty()) {
            return ContextUtils.getString(R.string.network_error);
        } else if (js.contains("未登录")) {
            return "请重新登录";
        } else if (js.contains("无此页")) {
            return ContextUtils.getString(R.string.last_page_prompt);
        } else {
            try {
                String json = js;
                if (json.startsWith("window.script_muti_get_var_store=")) {
                    json = json.substring("window.script_muti_get_var_store=".length());
                }
                JSONObject root = (JSONObject) JSON.parse(json);
                JSONObject data = root.getJSONObject("data");
                if (data != null && data.getJSONObject("__MESSAGE") != null) {
                    return stripHtml(data.getJSONObject("__MESSAGE").getString("1"));
                }
                JSONObject error = root.getJSONObject("error");
                if (error != null) {
                    String message = error.getString("0");
                    if (message != null) {
                        // 形如 "2048:注册用户/威望大于0方可使用搜索"
                        int index = message.indexOf(':');
                        if (index > 0 && message.substring(0, index).matches("\\d+")) {
                            message = message.substring(index + 1);
                        }
                        return stripHtml(message);
                    }
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static String stripHtml(String text) {
        return text == null ? null : text.replaceAll("<[^>]*>", "").trim();
    }

    private static String readErrorBody(HttpException exception) {
        try {
            Response<?> response = exception.response();
            ResponseBody body = response == null ? null : response.errorBody();
            if (body == null) {
                return "";
            }
            return new String(body.bytes(), "GBK");
        } catch (Exception e) {
            return "";
        }
    }

    public static String getErrorMessage(Throwable throwable) {
        String error;
        if (throwable instanceof UnknownHostException) {
            error = ContextUtils.getString(R.string.network_error);
        } else if (throwable instanceof HttpException) {
            // NGA 出错时经常返回 4xx 状态码，真正的原因写在响应正文里
            HttpException httpException = (HttpException) throwable;
            error = getErrorMessage(readErrorBody(httpException));
            if (error == null || error.isEmpty()) {
                error = "请求失败（HTTP " + httpException.code() + "）";
            }
        } else {
            error = throwable.getMessage();
        }
        return error;
    }

}
