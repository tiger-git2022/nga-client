package sp.phone.view.glass

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.shape.MaterialShapeDrawable
import gov.anzong.androidnga.R

/**
 * Fork：玻璃的固定参数。模糊度和不透明度在设置里调（[GlassSettings]，默认 10dp、40%，
 * 比 S1 的 12dp、60% 更透）；模糊后再提一点饱和度，后面的内容透过来更鲜明。
 */
object GlassStyle {
    const val SATURATION = 1.4f

    /** 标题栏下沿两个角的圆角 */
    const val BAR_CORNER_DP = 20f

    /** 弹出菜单的圆角 */
    const val POPUP_CORNER_DP = 24f
}

/**
 * Fork：帖子页的玻璃标题栏和底部页码栏（原型，只支持页码栏在底部的布局）。
 *
 * 帖子内容铺到标题栏和页码栏下面，两条栏的背景是下面内容的模糊加半透明染色（[GlassDrawable]）。
 * 标题栏往下读时收起、往上滑时浮出来盖在内容上（玻璃后面能看到内容），由当前页列表的滚动驱动（[State.follow]），
 * 不再用 AppBarLayout 的嵌套滚动（那样会先吃掉滑动距离，内容不动，顶部空出一条）。
 * 列表上下留出两条栏的高度（[applyListInsets]）。
 */
object ArticleGlass {

    /** 列表上下要留出的高度，以及标题栏跟随哪个列表 */
    class State(
        private val appBar: View,
        internal val tintColor: Int,
        internal val backdrop: Int,
        internal val foreground: Int,
    ) {
        var top = 0
            private set
        var bottom = 0
            private set
        private val listeners = mutableListOf<(Int, Int) -> Unit>()
        private val lists = mutableListOf<RecyclerView>()
        private val location = IntArray(2)

        internal fun register(rv: RecyclerView) {
            lists.add(rv)
        }

        internal fun unregister(rv: RecyclerView) {
            lists.remove(rv)
        }

        /** ViewPager 里正显示的那一页（左边缘在屏幕左半边） */
        private fun isVisiblePage(rv: RecyclerView): Boolean {
            if (!rv.isShown || rv.width <= 0) return false
            rv.getLocationOnScreen(location)
            return Math.abs(location[0]) < rv.width / 2
        }

        internal fun onListScrolled(rv: RecyclerView, dy: Int) {
            if (isVisiblePage(rv)) follow(rv, dy)
        }

        internal fun onListIdle(rv: RecyclerView) {
            if (isVisiblePage(rv)) snap(rv)
        }

        /** 翻到别的页：标题栏完整显示 */
        internal fun followVisibleList() {
            appBar.animate().translationY(0f).setDuration(150).start()
        }

        /** 列表从顶部滑过的距离（超过一个标题栏高时返回标题栏高） */
        private fun scrolledFromTop(rv: RecyclerView): Int {
            val lm = rv.layoutManager as? LinearLayoutManager ?: return 0
            val h = appBar.height
            val first = lm.findFirstVisibleItemPosition()
            return when {
                first < 0 -> 0
                first > 0 -> h
                else -> (rv.paddingTop - (lm.findViewByPosition(0)?.top ?: rv.paddingTop)).coerceIn(0, h)
            }
        }

        /**
         * 往下读时标题栏随内容滑走，往上滑立刻浮出来盖在内容上（玻璃后面能看到内容）；
         * 在顶部附近时不会比内容滑过的距离藏得更多，回到顶部一定完整显示。
         */
        private fun follow(rv: RecyclerView, dy: Int) {
            appBar.animate().cancel()
            val h = appBar.height.toFloat()
            var t = (appBar.translationY - dy).coerceIn(-h, 0f)
            t = maxOf(t, -scrolledFromTop(rv).toFloat())
            appBar.translationY = t
        }

        /** 停下时只露出一部分：露出过半就完整显示，否则收起（在顶部附近时保持跟随内容） */
        private fun snap(rv: RecyclerView) {
            val h = appBar.height.toFloat()
            val t = appBar.translationY
            if (t == 0f || t == -h || scrolledFromTop(rv) < h) return
            appBar.animate().translationY(if (t > -h / 2) 0f else -h).setDuration(150).start()
        }

        fun observe(listener: (Int, Int) -> Unit): () -> Unit {
            listeners.add(listener)
            if (top > 0 || bottom > 0) listener(top, bottom)
            return { listeners.remove(listener) }
        }

        internal fun update(top: Int, bottom: Int) {
            if (top == this.top && bottom == this.bottom) return
            this.top = top
            this.bottom = bottom
            listeners.toList().forEach { it(top, bottom) }
        }
    }

    private fun stateOf(activity: Activity?): State? =
        activity?.window?.decorView?.getTag(R.id.fork_glass_state) as? State

    /** 在 ArticleTabFragment.onViewCreated 里调用，root 是 fragment_article_tab_bottom 的根布局 */
    @JvmStatic
    fun install(activity: Activity, root: View) {
        val coordinator = root.findViewById<CoordinatorLayout>(R.id.main_content) ?: return
        val appBar = root.findViewById<AppBarLayout>(R.id.appbar) ?: return
        val container = root.findViewById<View>(R.id.container) ?: return
        val tabs = root.findViewById<View>(R.id.tabs) ?: return
        // 页码栏在顶部的布局里 tabs 在 AppBarLayout 里面，原型先不处理
        if (tabs.parent !== root) return
        // 底部页码栏去掉不显示（左右滑动翻页，「⋮」菜单里有「跳页」）；它原来占的底部位置也收回来
        tabs.visibility = View.GONE
        (coordinator.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 0
        coordinator.requestLayout()
        val state = installPage(activity, root as ViewGroup, coordinator, appBar, container, null)
        // 翻页停下后，标题栏完整显示
        root.findViewById<ViewPager>(R.id.pager)?.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageScrollStateChanged(s: Int) {
                if (s == ViewPager.SCROLL_STATE_IDLE) state.followVisibleList()
            }
        })
    }

    /**
     * 板块帖子列表（fragment_topic_list_board，根布局就是 CoordinatorLayout）的玻璃状态栏、标题栏、悬浮按钮。
     * 在 TopicListFragment.onViewCreated 里调用，之后再对列表调用 [applyListInsets]。
     */
    @JvmStatic
    fun installTopicList(activity: Activity, root: View) {
        val coordinator = root as? CoordinatorLayout ?: return
        val appBar = root.findViewById<AppBarLayout>(R.id.appbar) ?: return
        val content = root.findViewById<View>(R.id.swipe_refresh) ?: return
        // 原来靠 fitsSystemWindows 处理状态栏，改成由玻璃自己留出状态栏高度
        coordinator.fitsSystemWindows = false
        coordinator.statusBarBackground = null
        installPage(activity, coordinator, coordinator, appBar, content, null)
    }

    /** 这个页面是否装了玻璃（装了就不要再改标题栏的 scrollFlags） */
    @JvmStatic
    fun isInstalled(activity: Activity?): Boolean = stateOf(activity) != null

    /**
     * 通用部分：[content] 铺满 [coordinator]（含 [bottomBar] 下面），状态栏、标题栏、[bottomBar]、悬浮按钮换成玻璃，
     * 状态栏玻璃加在 [overlayParent] 里。
     */
    private fun installPage(
        activity: Activity, overlayParent: ViewGroup, coordinator: CoordinatorLayout,
        appBar: AppBarLayout, container: View, tabs: View?,
    ): State {
        val root = overlayParent
        val backdrop = themeColor(activity, android.R.attr.colorBackground)
        // 染色保持原来的颜色：标题栏、状态栏、悬浮按钮用顶栏色，页码栏用它原来的底色；文字图标按顶栏色深浅选
        val barTint = colorOf(appBar.background) ?: themeColor(activity, androidx.appcompat.R.attr.colorPrimary)
        // 页码栏原来没有自己的底色，透出的是页面底色（米色），所以用页面底色
        val tabsTint = backdrop
        val light = ColorUtils.calculateLuminance(barTint) > 0.5
        val foreground = if (light) activity.getColor(R.color.text_color) else Color.WHITE

        // 内容铺满整个 CoordinatorLayout（含页码栏下面），标题栏浮在上面
        (container.layoutParams as CoordinatorLayout.LayoutParams).behavior = null
        container.requestLayout()
        if (tabs != null) {
            (coordinator.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 0
            coordinator.requestLayout()
        }
        appBar.bringToFront()

        // 标题栏不再由 AppBarLayout 收起（那样会先吃掉滑动距离，内容不动，顶部空出一条），
        // 改成跟着当前页列表的内容一起移动：列表在顶部时完整显示，往下读时随内容滑走，回到顶部时跟着回来
        (appBar.findViewById<View>(R.id.toolbar)?.layoutParams as? AppBarLayout.LayoutParams)?.scrollFlags = 0
        appBar.stateListAnimator = null
        appBar.elevation = 0f
        // 标题栏下沿两个角是圆角（上面接着状态栏玻璃，是直角）
        appBar.background = GlassDrawable(
            appBar, container, barTint, backdrop,
            bottomCornerRadius = GlassStyle.BAR_CORNER_DP * activity.resources.displayMetrics.density
        )
        tabs?.let { it.background = GlassDrawable(it, container, tabsTint, backdrop) }
        appBar.findViewById<Toolbar>(R.id.toolbar)?.let { tintToolbar(it, foreground) }
        // 状态栏图标跟着玻璃深浅换颜色
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = light

        // 悬浮按钮原来在页码栏上面，现在 CoordinatorLayout 延伸到底了，抬高一个页码栏的高度；
        // 每个圆形按钮（含展开出来的）也换成玻璃
        root.findViewById<ViewGroup>(R.id.fab_menu)?.let { fam ->
            if (tabs != null) {
                val lp = fam.layoutParams as ViewGroup.MarginLayoutParams
                lp.bottomMargin += tabs.layoutParams.height
                fam.layoutParams = lp
            }
            for (i in 0 until fam.childCount) {
                (fam.getChildAt(i) as? FloatingActionButton)?.let {
                    glassFab(activity, coordinator, fam, it, container, barTint, backdrop, foreground)
                }
            }
        }

        // 状态栏也做成玻璃：隐藏 BaseActivity 在页面最上面插的实心色块（R.id.status_bar），内容铺到屏幕顶，
        // 状态栏位置放一条固定的玻璃，标题栏往下挪一个状态栏高；标题栏收起时滑到这条玻璃后面
        val statusGlass = View(activity)
        val statusLp = if (root is CoordinatorLayout) {
            CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { gravity = Gravity.TOP }
        } else {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
        }
        root.addView(statusGlass, statusLp)
        statusGlass.background = GlassDrawable(statusGlass, container, barTint, backdrop)
        var statusInset = 0

        val state = State(appBar, barTint, backdrop, foreground)
        activity.window.decorView.setTag(R.id.fork_glass_state, state)
        val update = { state.update(statusInset + appBar.height, tabs?.height ?: 0) }
        appBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        tabs?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }

        // 实心状态栏色块是在第一次分发 WindowInsets 时才加上的，所以在布局回调里找它
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val insets = ViewCompat.getRootWindowInsets(root) ?: return
                val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val solid = activity.window.decorView.findViewById<View>(R.id.status_bar)
                if (solid != null && solid.visibility != View.GONE) solid.visibility = View.GONE
                if (top != statusInset) {
                    statusInset = top
                    statusGlass.layoutParams.height = top
                    statusGlass.requestLayout()
                    val lp = appBar.layoutParams as ViewGroup.MarginLayoutParams
                    lp.topMargin = top
                    appBar.layoutParams = lp
                    update()
                }
                if (solid != null && top > 0) {
                    root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
        return state
    }

    /** 在 ArticleListFragment.onViewCreated 里调用：列表上下留出标题栏和页码栏的高度 */
    @JvmStatic
    fun applyListInsets(activity: Activity?, lifecycleView: View, recyclerView: RecyclerView) {
        val state = stateOf(activity) ?: return
        val baseTop = recyclerView.paddingTop
        val baseBottom = recyclerView.paddingBottom
        recyclerView.clipToPadding = false
        val refresh = generateSequence(recyclerView.parent) { it.parent }
            .filterIsInstance<SwipeRefreshLayout>().firstOrNull()
        val density = recyclerView.resources.displayMetrics.density

        // 下拉刷新的转圈按钮也换成玻璃圆（染顶栏色，箭头用顶栏文字色）
        refresh?.let { srl ->
            (0 until srl.childCount).map { srl.getChildAt(it) }.firstOrNull { it is ImageView }?.let { circle ->
                circle.background = GlassDrawable(circle, recyclerView, state.tintColor, state.backdrop, oval = true)
                circle.elevation = 0f
            }
            srl.setColorSchemeColors(state.foreground)
        }

        val remove = state.observe { top, bottom ->
            recyclerView.setPadding(recyclerView.paddingLeft, baseTop + top, recyclerView.paddingRight, baseBottom + bottom)
            refresh?.setProgressViewOffset(false, top - (40 * density).toInt(), top + (64 * density).toInt())
        }
        // 标题栏跟着当前页列表的内容移动（包括拖楼层滚动条、恢复阅读位置这类直接跳转）
        state.register(recyclerView)
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                state.onListScrolled(rv, dy)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) state.onListIdle(rv)
            }
        }
        recyclerView.addOnScrollListener(scrollListener)
        lifecycleView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                remove()
                state.unregister(recyclerView)
                recyclerView.removeOnScrollListener(scrollListener)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    /**
     * 悬浮按钮换成玻璃圆。项目里的 getbase 按钮其实是 Material 的 FloatingActionButton，不支持自定义背景，
     * 所以（同 S1）按钮本身改成透明、去掉投影（保留按下的水波纹），在 CoordinatorLayout 里、菜单的下面放一个
     * 同样大小的玻璃圆，每帧跟随按钮的位置（按钮在菜单里的位置 + 菜单的位置）、缩放、透明度和可见性。
     */
    private fun glassFab(
        activity: Activity, coordinator: ViewGroup, fam: View, fab: FloatingActionButton,
        container: View, tint: Int, backdrop: Int, foreground: Int,
    ) {
        fab.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        fab.compatElevation = 0f
        fab.elevation = 0f
        fab.imageTintList = ColorStateList.valueOf(foreground)

        val glass = View(activity)
        glass.background = GlassDrawable(glass, container, tint, backdrop, oval = true)
        coordinator.addView(glass, coordinator.indexOfChild(fam), CoordinatorLayout.LayoutParams(0, 0).apply {
            gravity = Gravity.TOP or Gravity.START
        })
        fab.viewTreeObserver.addOnPreDrawListener {
            val lp = glass.layoutParams
            if (lp.width != fab.width || lp.height != fab.height) {
                lp.width = fab.width
                lp.height = fab.height
                glass.layoutParams = lp
            }
            glass.x = fam.x + fab.x
            glass.y = fam.y + fab.y
            glass.scaleX = fab.scaleX * fam.scaleX
            glass.scaleY = fab.scaleY * fam.scaleY
            glass.alpha = fab.alpha * fam.alpha
            glass.visibility = if (fab.visibility == View.VISIBLE && fam.visibility == View.VISIBLE) View.VISIBLE else View.INVISIBLE
            true
        }
    }

    /**
     * 标题栏的标题、返回键、菜单图标（地球）和三个点改成 [color]。菜单图标是之后才加进来的，
     * 所以每次标题栏重新布局时再染一遍（图标 mutate 后只染一次就不会再变）。
     */
    private fun tintToolbar(toolbar: Toolbar, color: Int) {
        val apply = {
            toolbar.setTitleTextColor(color)
            toolbar.setSubtitleTextColor(color)
            toolbar.navigationIcon?.mutate()?.setTint(color)
            toolbar.overflowIcon?.mutate()?.setTint(color)
            val menu = toolbar.menu
            for (i in 0 until menu.size()) {
                menu.getItem(i).icon?.mutate()?.setTint(color)
            }
        }
        apply()
        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
    }

    private fun colorOf(drawable: android.graphics.drawable.Drawable?): Int? = when (drawable) {
        is MaterialShapeDrawable -> drawable.fillColor?.defaultColor
        is ColorDrawable -> drawable.color
        else -> null
    }

    private fun themeColor(activity: Activity, attr: Int): Int {
        val tv = TypedValue()
        activity.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
