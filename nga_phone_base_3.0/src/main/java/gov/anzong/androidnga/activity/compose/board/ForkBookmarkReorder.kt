package gov.anzong.androidnga.activity.compose.board

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex

/**
 * Fork: 「我的收藏」长按拖动调整顺序。
 *
 * 手势挂在整个网格上（坐标系稳定，和 LazyGridItemInfo.offset 一致），
 * 长按选中手指下的格子；拖到别的格子中心时通过 [onMove] 交换顺序；
 * 被拖的格子用 graphicsLayer 平移，始终跟着手指。
 */
class BookmarkDragState(
    private val gridState: LazyGridState,
    private val isReorderable: (Any) -> Boolean,
    private val onMove: (fromKey: Any, toKey: Any, fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    // 被拖格子左上角在视口里的位置（= 起点 + 手指累计位移）
    private var draggingPosition by mutableStateOf(Offset.Zero)

    private fun itemInfo(key: Any): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private fun itemAt(point: Offset): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            point.x >= it.offset.x && point.x < it.offset.x + it.size.width &&
                    point.y >= it.offset.y && point.y < it.offset.y + it.size.height
        }

    /** 返回是否选中了可拖动的格子 */
    fun start(point: Offset): Boolean {
        val info = itemAt(point)?.takeIf { isReorderable(it.key) } ?: return false
        draggingKey = info.key
        draggingPosition = info.offset.toOffset()
        return true
    }

    fun drag(delta: Offset) {
        val key = draggingKey ?: return
        draggingPosition += delta
        val current = itemInfo(key) ?: return
        val center = draggingPosition + Offset(current.size.width / 2f, current.size.height / 2f)

        val target = itemAt(center)?.takeIf { it.key != key && isReorderable(it.key) }
        if (target != null) {
            onMove(key, target.key, current.index, target.index)
        }

        // 拖到上下边缘时自动滚动
        val layout = gridState.layoutInfo
        val edge = current.size.height / 3f
        if (center.y > layout.viewportEndOffset - edge) {
            gridState.dispatchRawDelta(edge / 4)
        } else if (center.y < layout.viewportStartOffset + edge) {
            gridState.dispatchRawDelta(-edge / 4)
        }
    }

    fun end() {
        if (draggingKey != null) {
            draggingKey = null
            onDragEnd()
        }
    }

    fun translation(key: Any): Offset {
        if (key != draggingKey) return Offset.Zero
        val info = itemInfo(key) ?: return Offset.Zero
        return draggingPosition - info.offset.toOffset()
    }
}

/** 挂在 LazyVerticalGrid 上（放在 padding 之后，坐标才和格子 offset 对得上）。 */
@Composable
fun Modifier.bookmarkReorderGestures(state: BookmarkDragState): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                if (state.start(it)) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { change, amount ->
                if (state.draggingKey != null) {
                    change.consume()
                    state.drag(amount)
                }
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
}

/** 挂在格子上：拖动中的格子浮在最上层、稍微放大并跟随手指。 */
fun Modifier.bookmarkDragItem(state: BookmarkDragState, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            val t = state.translation(key)
            translationX = t.x
            translationY = t.y
            if (dragging) {
                scaleX = 1.08f
                scaleY = 1.08f
                alpha = 0.9f
            }
        }
}
