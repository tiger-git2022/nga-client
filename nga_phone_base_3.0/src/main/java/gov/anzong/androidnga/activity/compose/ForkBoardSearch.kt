package gov.anzong.androidnga.activity.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper
import gov.anzong.androidnga.R
import gov.anzong.androidnga.Utils
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.core.board.data.BoardEntity
import sp.phone.util.StringUtils

/**
 * Fork: 搜板块时列出全部结果让用户选，而不是直接进入第一个结果。
 *
 * NGA 的 forum.php?__output=8&key=xxx 按相关度返回板块和合集，
 * data 里 "0"、"1"… 每项有 fid、stid（合集才非 0）、name、parent{fid,name}，
 * 没有权限的项带 denied=1 和 error。需要登录，未登录时 error 里是提示文字。
 */
data class BoardSearchResult(
    val fid: Int,
    val stid: Int,
    val name: String,
    val parentName: String?,
    val deniedReason: String?,
)

data class BoardSearchState(
    val key: String,
    val loading: Boolean = false,
    val results: List<BoardSearchResult> = emptyList(),
    val error: String? = null,
)

object ForkBoardSearch {

    suspend fun search(key: String): BoardSearchState {
        return try {
            val url = Utils.getNGAHost() + "forum.php?__output=8&key=" +
                    StringUtils.encodeUrl(key, "gbk")
            val text = RetrofitHelper.getInstance().serviceKt.getString(url)
            parse(key, text)
        } catch (e: Exception) {
            BoardSearchState(key, error = "网络错误：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun parse(key: String, text: String): BoardSearchState {
        val root = JSON.parseObject(text) ?: return BoardSearchState(key, error = "返回内容无法解析")
        val data = root["data"] as? JSONObject
        val results = data?.entries
            ?.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }
            ?.sortedBy { it.first }
            ?.mapNotNull { (_, v) -> (v as? JSONObject)?.let { toResult(it) } }
            ?: emptyList()
        if (results.isNotEmpty()) {
            return BoardSearchState(key, results = results)
        }
        return BoardSearchState(key, error = errorMessage(root) ?: "没有找到符合条件的版面")
    }

    private fun toResult(obj: JSONObject): BoardSearchResult? {
        val fid = obj.getIntValue("fid")
        val stid = obj.getIntValue("stid")
        val name = obj.getString("name")
        if ((fid == 0 && stid == 0) || name.isNullOrEmpty()) {
            return null
        }
        val denied = obj.getString("denied") == "1"
        return BoardSearchResult(
            fid = fid,
            stid = stid,
            // 没权限的项 name 已被服务器换成「帐号权限不足」之类的提示
            name = name,
            parentName = obj.getJSONObject("parent")?.getString("name"),
            deniedReason = if (denied) obj.getString("error") ?: "没有权限" else null,
        )
    }

    private fun errorMessage(root: JSONObject): String? {
        val error = root["error"]
        val msg = when (error) {
            is JSONObject -> error.getString("0")
            is String -> error
            else -> null
        } ?: return null
        // 形如 "2048:你需要登录才能使用此功能"
        return msg.substringAfter(':', msg).ifBlank { null }
    }

    fun open(result: BoardSearchResult) {
        val known = ForumBoardViewModel.findBoard(result.fid, result.stid)
        val board = BoardEntity().apply {
            fid = result.fid
            stid = result.stid
            name = known?.name ?: result.name
            head = known?.head
        }
        ForumBoardViewModel.showTopicList(board)
    }
}

@Composable
fun BoardSearchResultView(state: BoardSearchState, onClose: () -> Unit) {
    val textColor = Color(ContextUtils.getContext().resources.getColor(R.color.text_color, null))
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = if (state.loading) "正在搜索「${state.key}」…"
                else "「${state.key}」的搜索结果" + if (state.results.isNotEmpty()) "（${state.results.size}）" else "",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, tint = Color.Gray, contentDescription = "返回搜索历史")
            }
        }
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null -> Text(
                modifier = Modifier.padding(16.dp),
                text = state.error,
                color = Color.Gray,
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.results, key = { "${it.fid}_${it.stid}" }) {
                    BoardSearchResultItem(it, textColor)
                    Divider(modifier = Modifier.padding(start = 16.dp))
                }
                item {
                    Box(modifier = Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
                }
            }
        }
    }
}

@Composable
private fun BoardSearchResultItem(result: BoardSearchResult, textColor: Color) {
    val enabled = result.deniedReason == null
    var bookmarked by remember(result) {
        mutableStateOf(ForumBoardViewModel.isBookmarkBoard(result.fid, result.stid))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { ForkBoardSearch.open(result) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (result.stid != 0) {
                    Text(
                        modifier = Modifier.padding(end = 6.dp),
                        text = "合集",
                        fontSize = 11.sp,
                        color = Color(0xFF3F7FBF),
                    )
                }
                Text(
                    text = result.name,
                    fontSize = 15.sp,
                    color = if (enabled) textColor else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val sub = buildList {
                result.parentName?.takeIf { it.isNotEmpty() }?.let { add("所属：$it") }
                add(if (result.stid != 0) "stid ${result.stid}" else "fid ${result.fid}")
            }.joinToString(" · ")
            Text(text = sub, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) {
            IconButton(onClick = {
                if (bookmarked) {
                    ForumBoardViewModel.removeBookmarkBoard(result.fid, result.stid)
                } else {
                    ForumBoardViewModel.addBookmarkBoard(result.name, result.fid, result.stid)
                }
                bookmarked = ForumBoardViewModel.isBookmarkBoard(result.fid, result.stid)
            }) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = Icons.Filled.Star,
                    tint = if (bookmarked) Color(0xFFF0A020) else Color.Gray.copy(alpha = 0.4f),
                    contentDescription = if (bookmarked) "取消收藏" else "收藏",
                )
            }
        }
    }
}
