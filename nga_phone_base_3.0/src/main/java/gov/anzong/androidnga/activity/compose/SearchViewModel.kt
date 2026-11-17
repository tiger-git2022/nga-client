package gov.anzong.androidnga.activity.compose

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import gov.anzong.androidnga.arouter.ARouterConstants
import kotlinx.coroutines.launch
import sp.phone.common.UserManagerImpl

class SearchViewModel : ViewModel() {

    companion object {
        const val SEARCH_MODE_USER: Int = 0
        const val SEARCH_MODE_TOPIC: Int = 1
        const val SEARCH_MODE_BOARD: Int = 2

        const val SEARCH_MODE_USER_NAME: String = "username"
        const val SEARCH_MODE_USER_ID: String = "uid"

        const val SEARCH_MODE_TOPIC_CURRENT: String = "current"
        const val SEARCH_MODE_TOPIC_ALL: String = "all"

    }

    val searchData: List<Pair<String, Int>> = listOf(
        Pair("搜主题", SEARCH_MODE_TOPIC),
        Pair("搜板块", SEARCH_MODE_BOARD),
        Pair("搜用户", SEARCH_MODE_USER),
    )

    val searchUserData: List<Pair<String, String>> = listOf(
        Pair("用户名", SEARCH_MODE_USER_NAME),
        Pair("用户ID", SEARCH_MODE_USER_ID),
    )

    val searchTopicData: List<Pair<String, String>> = listOf(
        Pair("当前板块", SEARCH_MODE_TOPIC_CURRENT),
        Pair("全部板块", SEARCH_MODE_TOPIC_ALL),
    )

    var searchMode: MutableLiveData<Int> = object : MutableLiveData<Int>(SEARCH_MODE_TOPIC) {

        override fun setValue(value: Int?) {
            if (getValue() == value) return
            keyList.value = searchModel.getSearchHistory(value!!)
            super.setValue(value)
        }

        override fun postValue(value: Int?) {
            if (getValue() == value) return
            keyList.value = searchModel.getSearchHistory(value!!)
            super.postValue(value)
        }
    }

    var searchUserMode: String = SEARCH_MODE_USER_NAME

    var searchTopicMode: String = SEARCH_MODE_TOPIC_CURRENT

    var searchTopicWithContent: Boolean = false

    var fid: Int = 0

    var keyList = MutableLiveData<List<String>>()

    private val searchModel: SearchModel = SearchModel()

    init {
        keyList.value = searchModel.getSearchHistory(searchMode.value!!)
    }

    fun getSearchTintText(searchMode: Int): String {
        return when (searchMode) {
            SEARCH_MODE_USER -> "默认查看自己的用户信息"
            SEARCH_MODE_BOARD -> "强撸灰飞烟灭"
            SEARCH_MODE_TOPIC -> "强撸灰飞烟灭"
            else -> "搜索"
        }
    }

    fun query(context: Context, query: String) {
        when (searchMode.value) {
            SEARCH_MODE_USER -> queryUser(query)
            SEARCH_MODE_TOPIC -> queryTopic(query)
            SEARCH_MODE_BOARD -> queryBoard(context, query)
        }
    }

    // Fork: 搜板块时列出全部结果让用户选（原版直接进入第一个结果），见 ForkBoardSearch.kt
    val boardSearchState = MutableLiveData<BoardSearchState?>(null)

    private fun queryBoard(context: Context, query: String) {
        if (query.isEmpty()) {
            return
        }
        putHistory(searchMode.value!!, query)

        boardSearchState.value = BoardSearchState(query, loading = true)
        viewModelScope.launch {
            val result = ForkBoardSearch.search(query)
            // 连续搜索时只保留最后一次的结果
            if (boardSearchState.value?.key == query) {
                boardSearchState.value = result
            }
        }
    }

    fun clearBoardSearch() {
        boardSearchState.value = null
    }

    private fun queryUser(query: String) {
        var realQuery = query
        if (query.isEmpty()) {
            val user = UserManagerImpl.getInstance().activeUser ?: return
            realQuery = if (searchUserMode == SEARCH_MODE_USER_NAME) user.nickName else user.userId
        } else {
            putHistory(searchMode.value!!, query)
        }
        ARouter.getInstance()
            .build(ARouterConstants.ACTIVITY_PROFILE)
            .withString("mode", searchUserMode)
            .withString(searchUserMode, realQuery)
            .navigation()
    }

    private fun queryTopic(query: String) {
        if (query.isEmpty()) {
            return
        }

        putHistory(searchMode.value!!, query)

        val postcard = ARouter.getInstance()
            .build(ARouterConstants.ACTIVITY_TOPIC_LIST)
            .withInt("content", if (searchTopicWithContent) 1 else 0)
            .withString("key", query)
        if (searchTopicMode == SEARCH_MODE_TOPIC_CURRENT && fid != 0) {
            postcard.withInt("fid", fid)
        }
        postcard.navigation()
    }

    private fun putHistory(searchMode: Int, key: String) {
        val newKeyList = searchModel.putHistory(searchMode, key)
        newKeyList?.let {
            keyList.value = it
        }
    }

    fun deleteHistory(key: String) {
        val newKeyList = searchModel.deleteHistory(searchMode.value!!, key)
        newKeyList?.let {
            keyList.value = it
        }
    }

}
