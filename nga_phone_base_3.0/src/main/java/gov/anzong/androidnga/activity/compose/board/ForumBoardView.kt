package gov.anzong.androidnga.activity.compose.board

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.justwen.androidnga.ui.compose.widget.TabLayoutWithPager
import com.justwent.androidnga.bu.UserManager
import gov.anzong.androidnga.R
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.core.board.data.BoardEntity
import sp.phone.common.ApiConstants
import kotlin.math.abs


@Composable
fun ForumBoardView(forumBoardViewModel: ForumBoardViewModel) {
    val boardData by forumBoardViewModel.boardLiveData.observeAsState()
    val tabs = arrayListOf<String>()
    boardData?.let {
        it.forEach {
            tabs.add(it.name)
        }
        val initialPage = if (forumBoardViewModel.bookmarkSizeLiveData.value!! > 0) 0 else 1
        TabLayoutWithPager(tabs = tabs, initialPage = initialPage) {
            ForumBoardContent(it, forumBoardViewModel)
        }
    }
}

@Composable
fun ForumBoardGroupView(board: BoardEntity, context: Context = ContextUtils.getContext()) {
    Row(modifier = Modifier.padding(16.dp)) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = R.drawable.default_board_icon),
            contentDescription = "",
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = board.name,
            color = Color(context.resources.getColor(R.color.text_color, null)),
        )
    }
}

@Composable
private fun ForumBoardGridItemView(
    child: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel,
    context: Context = ContextUtils.getContext(),
    modifier: Modifier = Modifier, // Fork: 收藏拖动排序
) {
    val paddingValue = 4.dp
    val imageSize = 48.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(paddingValue)
            .fillMaxWidth()
            .clickable {
                forumBoardViewModel.showTopicList(child)
            }
    ) {
        Spacer(modifier = Modifier.height(paddingValue))
        val resId = getResId(child)
        if (resId > 0) {
            Image(
                modifier = Modifier.size(imageSize),
                painter = painterResource(id = resId),
                contentDescription = ""
            )
        } else {
            val url = getResUrl(child)
            Image(
                modifier = Modifier.size(imageSize),
                painter = rememberAsyncImagePainter(
                    model = url,
                    placeholder = painterResource(id = R.drawable.default_board_icon),
                    error = painterResource(id = R.drawable.default_board_icon)
                ),
                contentDescription = ""
            )
        }
        Text(
            modifier = Modifier
                .padding(top = paddingValue, bottom = paddingValue),
            color = Color(context.resources.getColor(R.color.text_color, null)),
            text = child.name
        )
        Spacer(modifier = Modifier.height(paddingValue))
    }
}

private fun getResUrl(board: BoardEntity): String {
    return board.iconUrl
}


private fun getResId(board: BoardEntity): Int {
    if (board.stid != 0) {
        return 0
    }

    val fid = board.fid
    val resName = if (fid > 0) "p$fid" else "p_" + abs(fid)
    return ContextUtils.getResources()
        .getIdentifier(resName, "drawable", ContextUtils.getContext().packageName)
}

@Composable
fun ForumBoardBookmarkContent(
    bookmark: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel,
    topInset: Dp = 0.dp, // Fork: 玻璃顶栏盖在上面时，网格顶部留出顶栏的高度
) {
    val bookmarkSize by forumBoardViewModel.bookmarkSizeLiveData.observeAsState()
    val maxColumn = 3

    // Fork: 长按拖动调整收藏顺序，见 ForkBookmarkReorder.kt
    var orderVersion by remember { mutableIntStateOf(0) }
    // 读 bookmarkSize / orderVersion 是为了在增删、排序后重组
    val boards = if (bookmarkSize!! >= 0 && orderVersion >= 0) bookmark.children!!.toList() else emptyList()
    // 万一旧数据里有重复 id，重复项换个 key（不能拖），避免 LazyGrid 因 key 重复崩溃
    val keys = HashSet<String>()
    val boardKeys = boards.mapIndexed { i, b -> if (keys.add(b.id)) b.id else "${b.id}#dup$i" }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val dragState = remember(gridState) {
        BookmarkDragState(
            gridState = gridState,
            isReorderable = { key -> bookmark.children!!.any { it.id == key } },
            onMove = { fromKey, toKey, fromIndex, toIndex ->
                val list = bookmark.children!!
                val from = list.indexOfFirst { it.id == fromKey }
                val to = list.indexOfFirst { it.id == toKey }
                // 移动的是第一个可见项时，列表会跟着它滚动，这里把位置固定住
                val first = gridState.firstVisibleItemIndex
                val firstOffset = gridState.firstVisibleItemScrollOffset
                forumBoardViewModel.moveBookmarkBoard(from, to)
                orderVersion++
                if (first == fromIndex || first == toIndex) {
                    scope.launch { gridState.scrollToItem(first, firstOffset) }
                }
            },
            onDragEnd = {},
        )
    }

    Column (Modifier.fillMaxSize()) {

        LazyVerticalGrid(
            columns = GridCells.Fixed(maxColumn),
            state = gridState,
            contentPadding = PaddingValues(top = topInset),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
                .bookmarkReorderGestures(dragState)
        ) {
            // Fork: 提示放进网格里，玻璃顶栏下面也能滑过去
            if (UserManager.getUserList().size == 1) {
                item(key = "fork_account_tip", span = { GridItemSpan(maxColumn) }) {
                    Text(modifier = Modifier.padding(vertical = 8.dp), text = "建议登录多个账号，可有效改善跳转系统浏览器问题")
                }
            }
            if (boards.size > 1) {
                item(key = "fork_reorder_hint", span = { GridItemSpan(maxColumn) }) {
                    Text(
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        text = "长按拖动可调整顺序",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                }
            }
            items(boards.size, key = { boardKeys[it] }) { index ->
                val board = boards[index]
                val itemModifier = Modifier.bookmarkDragItem(dragState, boardKeys[index])
                ForumBoardGridItemView(
                    board, forumBoardViewModel,
                    modifier = if (dragState.draggingKey == boardKeys[index]) itemModifier
                    else itemModifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                )
            }
            item(span = { GridItemSpan(maxColumn) }) {
                val paddingValues = WindowInsets.navigationBars.asPaddingValues()
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@Composable
fun ForumBoardContent(
    index: Int,
    forumBoardViewModel: ForumBoardViewModel,
    topInset: Dp = 0.dp, // Fork: 玻璃顶栏盖在上面时，网格顶部留出顶栏的高度
) {
    val boardData = forumBoardViewModel.getBoardData(index)
    if (boardData.type == BoardEntity.BoardType.BOOKMARK) {
        ForumBoardBookmarkContent(boardData, forumBoardViewModel, topInset)
    } else {
        val boardList: List<BoardEntity> = forumBoardViewModel.getBoardData(index).children!!
        val maxColumn = 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(maxColumn),
            contentPadding = PaddingValues(top = topInset),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
        ) {
            boardList.forEach {
                if (it.type == BoardEntity.BoardType.GROUP) {
                    item(span = { GridItemSpan(maxColumn) }) {
                        ForumBoardGroupView(it)
                    }
                    it.children?.let { data ->
                        items(data.size) { index ->
                            ForumBoardGridItemView(data[index], forumBoardViewModel)
                        }
                    }
                } else {
                    item {
                        ForumBoardGridItemView(it, forumBoardViewModel)
                    }
                }
            }
            item(span = { GridItemSpan(maxColumn) }) {
                val paddingValues = WindowInsets.navigationBars.asPaddingValues()
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}


