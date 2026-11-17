package sp.phone.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * Fork：只在相邻两项之间画分隔线（用主题的 listDivider），最后一项后面不画。
 *
 * 原来的 DividerItemDecoration 每一项下面都画，帖子页列表底部留出玻璃页码栏的高度后，
 * 拉到底时最后一楼下面那条线正好压在页码栏上沿。
 */
class InnerDividerDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val divider: Drawable? = context.obtainStyledAttributes(intArrayOf(android.R.attr.listDivider)).let {
        val d = it.getDrawable(0)
        it.recycle()
        d
    }

    private fun hasDividerBelow(parent: RecyclerView, view: View, state: RecyclerView.State): Boolean {
        val position = parent.getChildAdapterPosition(view)
        return position != RecyclerView.NO_POSITION && position < state.itemCount - 1
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val d = divider
        if (d != null && hasDividerBelow(parent, view, state)) {
            outRect.set(0, 0, 0, d.intrinsicHeight)
        } else {
            outRect.setEmpty()
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val d = divider ?: return
        val left = parent.paddingLeft
        val right = parent.width - parent.paddingRight
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (!hasDividerBelow(parent, child, state)) continue
            val top = child.bottom + child.translationY.roundToInt()
            d.setBounds(left, top, right, top + d.intrinsicHeight)
            d.draw(c)
        }
    }
}
