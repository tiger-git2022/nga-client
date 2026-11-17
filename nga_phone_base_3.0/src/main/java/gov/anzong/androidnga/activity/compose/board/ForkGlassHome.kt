package gov.anzong.androidnga.activity.compose.board

import android.app.Activity
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.justwen.androidnga.ui.compose.widget.OptionMenuData
import com.justwen.androidnga.ui.compose.widget.TopAppBarData
import kotlinx.coroutines.launch
import sp.phone.view.glass.GlassPopupDrawable
import sp.phone.view.glass.GlassSettings
import sp.phone.view.glass.GlassStyle

/**
 * Fork：首页的玻璃顶栏（顶栏 + 板块分类标签，一直延伸到状态栏）。
 *
 * 原来是 ScaffoldApp（实心顶栏）+ TabLayoutWithPager（实心标签行）+ 板块网格。这里板块网格铺满整屏，
 * 顶部留出顶栏和标签的高度（滑动时从它们下面穿过）；网格画进一个 GraphicsLayer，顶栏和标签浮在上面，
 * 背景画这个图层的模糊（再提一点饱和度），叠一层原来的顶栏色。模糊度、不透明度来自设置（GlassSettings）。
 */
@Composable
fun ForkGlassHome(topAppBarData: TopAppBarData, viewModel: ForumBoardViewModel) {
    val boardData by viewModel.boardLiveData.observeAsState()
    val boards = boardData ?: return
    val tabs = boards.map { it.name }
    val initialPage = if ((viewModel.bookmarkSizeLiveData.value ?: 0) > 0) 0 else 1
    val pagerState = rememberPagerState(pageCount = { tabs.size }, initialPage = initialPage)
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    var barsHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // 夜间模式换成深灰，见 GlassStyle.NIGHT_TINT
    val tint = Color(GlassStyle.barTint(LocalContext.current, MaterialTheme.colors.primary.toArgb()))
    val backdrop = MaterialTheme.colors.background

    // 状态栏透明、浅色图标，玻璃顶栏延伸到状态栏下面
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            @Suppress("DEPRECATION")
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { contentOrigin = it.positionInRoot() }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                // 原来 ScaffoldApp 自带的页面底色（米色），没有它会透出窗口的深色背景
                .background(backdrop)
        ) { page ->
            ForumBoardContent(page, viewModel, with(density) { barsHeightPx.toDp() })
        }

        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { barsHeightPx = it.height }
        ) {
            GlassBackdrop(layer, contentOrigin, tint, backdrop, Modifier.matchParentSize())
            Column {
                TopAppBar(
                    title = { Text(text = topAppBarData.title, color = Color.White) },
                    windowInsets = WindowInsets.statusBars,
                    navigationIcon = {
                        IconButton(onClick = { topAppBarData.navigationIconAction?.invoke() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = Color.White,
                                contentDescription = null
                            )
                        }
                    },
                    actions = { GlassOptionMenu(topAppBarData.optionMenuData, menuTop = barsHeightPx) },
                    backgroundColor = Color.Transparent,
                    contentColor = Color.White,
                    elevation = 0.dp,
                )
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 0.dp,
                    backgroundColor = Color.Transparent,
                    contentColor = Color.White,
                    divider = {},
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶栏右侧的按钮和三个点菜单（代替 OptionActionMenu）：菜单从整个玻璃顶栏（标题栏 + 分类标签）的下沿弹出、靠右，
 * 不盖住顶栏；背景是玻璃（和 View 体系的弹出菜单一样用 GlassPopupDrawable，截取菜单底下的页面再模糊）。
 */
@Composable
private fun GlassOptionMenu(actions: List<OptionMenuData>?, menuTop: Int) {
    if (actions == null) return
    val showItems = actions.filter { it.type == OptionMenuData.OPTION_MENU_TYPE_ALWAYS_SHOW }
    val hideItems = actions.filter { it.type == OptionMenuData.OPTION_MENU_TYPE_HIDDEN }
    Row(verticalAlignment = Alignment.CenterVertically) {
        showItems.forEach {
            IconButton(onClick = { it.action() }) {
                Icon(painter = painterResource(it.icon!!), contentDescription = null, tint = Color.White)
            }
        }
        if (hideItems.isEmpty()) return@Row
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = !expanded }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
        }
        if (expanded) {
            val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
            val provider = remember(menuTop, margin) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
                    ) = IntOffset(windowSize.width - popupContentSize.width - margin, menuTop)
                }
            }
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                GlassMenuSurface {
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        hideItems.forEach { item ->
                            Text(
                                text = item.title ?: "",
                                color = MaterialTheme.colors.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expanded = false
                                        item.action()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 弹出菜单的玻璃底：GlassPopupDrawable 画在 Compose 里，截图完成后触发重画 */
@Composable
private fun GlassMenuSurface(content: @Composable () -> Unit) {
    val view = LocalView.current
    var redraw by remember { mutableIntStateOf(0) }
    val drawable = remember {
        GlassPopupDrawable().apply {
            hostView = view
            callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) {
                    redraw++
                }

                override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
                override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
            }
        }
    }
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .drawBehind {
                @Suppress("UNUSED_EXPRESSION") redraw
                drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                drawIntoCanvas { drawable.draw(it.nativeCanvas) }
            }
    ) {
        content()
    }
}

/** 画 [layer]（内容）里位于自己下面的那一块：模糊、提饱和度，再叠一层 [tint] */
@Composable
private fun GlassBackdrop(layer: GraphicsLayer, contentOrigin: Offset, tint: Color, backdrop: Color, modifier: Modifier) {
    GlassSettings.init()
    val density = LocalDensity.current.density
    val blurPx = GlassSettings.blurDp * density
    val opacity = GlassSettings.opacityPercent / 100f
    val effect = remember(blurPx) {
        val saturate = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(GlassStyle.SATURATION) })
        )
        (if (blurPx > 0f) RenderEffect.createChainEffect(saturate, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
        else saturate).asComposeRenderEffect()
    }
    var position by remember { mutableStateOf(Offset.Zero) }
    // 下沿两个角是圆角（和帖子页、板块列表的标题栏一样）
    val shape = RoundedCornerShape(bottomStart = GlassStyle.BAR_CORNER_DP.dp, bottomEnd = GlassStyle.BAR_CORNER_DP.dp)
    Box(
        modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .graphicsLayer {
                renderEffect = effect
                this.shape = shape
                clip = true
            }
            .drawBehind {
                drawRect(backdrop)
                translate(contentOrigin.x - position.x, contentOrigin.y - position.y) {
                    drawLayer(layer)
                }
            }
    )
    Box(modifier.background(tint.copy(alpha = opacity), shape))
}
