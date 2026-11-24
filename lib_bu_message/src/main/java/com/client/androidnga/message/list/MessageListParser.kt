package com.client.androidnga.message.list

import com.alibaba.fastjson2.JSONException
import com.alibaba.fastjson2.JSONObject
import com.client.androidnga.base.network.ApiResult
import com.client.androidnga.base.network.BusinessException
import gov.anzong.androidnga.base.util.StringUtils
import gov.anzong.androidnga.common.base.JavaBean

class MessageListParser {

    fun parse(js: String?): ApiResult<MessageListInfo> {
        if (js.isNullOrEmpty()) {
            return ApiResult.failure(Exception("网络错误,响应内容为空"))
        }
        val result = js.removePrefix("window.script_muti_get_var_store=")
        return try {
            parseInternal(result)
        } catch (e: Exception) {
            ApiResult.failure(Exception("消息列表解析失败", e))
        }
    }

    private fun parseInternal(result: String): ApiResult<MessageListInfo>  {
        val obj: JSONObject =
            JSONObject.parseObject(result) ?: return ApiResult.failure(JSONException("数据格式错误"))

        val dataObj = obj.getJSONObject("data")?.getJSONObject("0")
        dataObj?.let {
            val messageInfo = MessageListInfo().apply {
                rowsPerPage = it.getIntValue("rowsPerPage")
                nextPage = it.getIntValue("nextPage")
                currentPage = it.getIntValue("currentPage")
            }
            var i = 0
            var rowObj: JSONObject? = it.getJSONObject(i.toString())
            while (rowObj != null) {
                parseMessageListItem(rowObj)?.let { item ->
                    messageInfo.messageItemList.add(item)
                }
                i++
                rowObj = it.getJSONObject(i.toString())
            }
            return ApiResult.success(messageInfo)
        }
        val errorObj = obj.getJSONObject("error")
        errorObj?.let {
            val errorStr = it.getString("0")
            if (!errorStr.isNullOrEmpty()) {
                return ApiResult.failure(BusinessException(message = errorStr))
            }
        }
        return ApiResult.failure(Exception("未知错误"))
    }

    private fun parseMessageListItem(rowObj: JSONObject): MessageListItem? {
        try {
            val objBean = rowObj.toJavaObject(MessageListBean::class.java)
            val messageItem = MessageListItem().apply {
                mid = objBean.mid
                subject = objBean.subject
                posts = objBean.posts
                fromUsername = objBean.from_username
                lastFromUsername = objBean.last_from_username
                time = objBean.time.takeIf { it.isNotEmpty() }?.let {
                    StringUtils.timeStamp2Date1(it)
                }.orEmpty()
                lastTime = objBean.last_modify.takeIf { it.isNotEmpty() }?.let {
                    StringUtils.timeStamp2Date1(it)
                }.orEmpty()
            }
            return messageItem
        } catch (e: Exception) {
            return null
        }
    }
}

private data class MessageListBean(
    var all_user: String = "",
    var bit: Int = 0,
    var from: Int = 0,
    var from_user_bit: Int = 0,
    var from_username: String = "",
    var last_from: Int = 0,
    var last_from_username: String = "",
    var last_modify: String = "",
    var mid: String = "",
    var posts: Int = 0,
    var sbit: Int = 0,
    var subject: String = "",
    var time: String = ""
) : JavaBean