package sp.phone.view.glass

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

/**
 * Fork：玻璃背景（做法同 S1 分支的 GlassBarDrawable）。
 *
 * 每次绘制前（OnPreDraw）把 [host] 下面的内容 [source] 录进一个 [RenderNode]，用 [RenderEffect] 模糊，
 * 再叠一层半透明的 [tintColor]。录制时子 View 只是引用各自的 RenderNode，开销很小；
 * host 自己的显示列表只引用这个 RenderNode，内容滚动时不用重绘 host。
 * 应用最低 Android 12，RenderEffect 总是可用。
 */
class GlassDrawable(
    private val host: View,
    private val source: View,
    @ColorInt private val tintColor: Int,
    @ColorInt private val backdropColor: Int,
    /** true：画成 [getBounds] 内切的圆（悬浮按钮）；录制范围仍是整个 host */
    private val oval: Boolean = false,
    /** 圆角半径（像素），大于 0 时画成圆角矩形，超过短边一半时就是胶囊形（楼层气泡） */
    private val cornerRadius: Float = 0f,
    /** 只把下沿两个角画成圆角（像素），标题栏用：和上面的状态栏玻璃连成一块，下沿是圆角 */
    private val bottomCornerRadius: Float = 0f,
) : Drawable() {

    private val clip = Path()

    private val node = RenderNode("glass")
    private var tint = 0
    private var appliedSettings = -1
    private val density = host.resources.displayMetrics.density
    private val hostLocation = IntArray(2)
    private val sourceLocation = IntArray(2)

    /*
     * 不能像 S1 那样每帧都录：帖子的每一楼是 WebView，WebView 被画进别的 RenderNode 时会要求重画自己，
     * 「录制 → WebView 重画 → 再录制」会一直刷新（页面一直在闪）。所以只在内容真的变了时重录：
     * 有 View 滚动（列表、翻页）时立即重录；布局变化后、或 host/source 的位置大小变了（标题栏收起展开）时，
     * 在下一次绘制前重录。WebView 因为被录而要求的那一次重画不会再触发录制，循环就断了。
     */
    private var dirty = true
    private var lastHostX = Int.MIN_VALUE
    private var lastHostY = Int.MIN_VALUE
    private var lastSourceX = Int.MIN_VALUE
    private var lastSourceY = Int.MIN_VALUE
    private var lastW = -1
    private var lastH = -1

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (applySettings()) invalidateSelf()
        if (dirty || moved()) record()
        true
    }

    /**
     * 立即按 host 现在的位置重录。host 被别的 OnPreDraw 挪动时（比如跟着楼层气泡走的玻璃底），
     * 自己的 OnPreDraw 可能已经先跑过、录的是旧位置下面的内容，挪完后调用这个。
     */
    fun refresh() {
        dirty = true
        record()
    }

    /** 设置里的模糊度、不透明度变了就重新应用；返回是否有变化 */
    private fun applySettings(): Boolean {
        GlassSettings.init()
        val v = GlassSettings.version
        if (v == appliedSettings) return false
        appliedSettings = v
        tint = ColorUtils.setAlphaComponent(tintColor, GlassSettings.opacityPercent * 255 / 100)
        // 模糊之后提高饱和度：后面的头像、图片透过来颜色更鲜明，更像玻璃而不是毛玻璃
        val px = GlassSettings.blurDp * density
        val saturate = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(GlassStyle.SATURATION) })
        )
        node.setRenderEffect(
            if (px > 0f) RenderEffect.createChainEffect(saturate, RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP))
            else saturate
        )
        return true
    }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { record() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { dirty = true }

    private fun moved(): Boolean {
        host.getLocationInWindow(hostLocation)
        source.getLocationInWindow(sourceLocation)
        return hostLocation[0] != lastHostX || hostLocation[1] != lastHostY ||
                sourceLocation[0] != lastSourceX || sourceLocation[1] != lastSourceY ||
                host.width != lastW || host.height != lastH
    }

    private fun addListeners(v: View) {
        v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        v.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        v.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            dirty = true
            addListeners(v)
        }

        override fun onViewDetachedFromWindow(v: View) {
            v.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            v.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            v.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
    }

    init {
        applySettings()
        host.addOnAttachStateChangeListener(attachListener)
        if (host.isAttachedToWindow) {
            addListeners(host)
        }
    }

    private fun record() {
        val w = host.width
        val h = host.height
        if (w <= 0 || h <= 0 || !source.isLaidOut) return
        // 看不见的（比如收起的悬浮按钮）先不录，变回可见时再录
        if (!host.isShown || host.alpha <= 0f) {
            dirty = true
            return
        }
        host.getLocationInWindow(hostLocation)
        source.getLocationInWindow(sourceLocation)
        dirty = false
        lastHostX = hostLocation[0]
        lastHostY = hostLocation[1]
        lastSourceX = sourceLocation[0]
        lastSourceY = sourceLocation[1]
        lastW = w
        lastH = h
        // 往四周多录一圈再模糊、画的时候裁回 host：模糊到边缘时用的是真实的相邻内容，而不是把边上一行往外延伸。
        // 这样相邻的两块玻璃（状态栏和标题栏）在交界处算出来的是同一片内容的模糊，像素连续，看不出分隔线
        val m = margin
        recordedMargin = m
        node.setPosition(0, 0, w + 2 * m, h + 2 * m)
        val canvas = node.beginRecording(w + 2 * m, h + 2 * m)
        try {
            canvas.drawColor(backdropColor)
            canvas.translate(m.toFloat(), m.toFloat())
            val dx = (sourceLocation[0] - hostLocation[0]).toFloat()
            val dy = (sourceLocation[1] - hostLocation[1]).toFloat()
            canvas.translate(dx - source.scrollX, dy - source.scrollY)
            // host（连同多录的一圈）在 source 内容坐标里的范围，只画这部分
            visible.set(
                -dx + source.scrollX - m, -dy + source.scrollY - m,
                -dx + source.scrollX + w + m, -dy + source.scrollY + h + m
            )
            drawTree(source, canvas, visible)
        } finally {
            node.endRecording()
        }
    }

    private var recordedMargin = 0

    /** 多录的一圈：模糊半径的 2 倍 */
    private val margin: Int get() = Math.ceil(GlassSettings.blurDp * density * 2.0).toInt()

    private val visible = RectF()

    /**
     * 不能直接 source.draw(canvas)：那样玻璃里引用的是各个子 View 的 RenderNode，WebView 每帧被画两次
     * （页面上一次、玻璃里一次），而 WebView 每被多画一次就会再要一帧，于是页面一直在重绘、闪烁。
     * 这里自己遍历：普通 View 把内容画进来（不引用它们的 RenderNode），WebView 只把被 host 挡住的那一小块
     * 用软件方式截成半分辨率的图片画进来。
     *
     * 调用时 canvas 的原点是 v 的内容坐标原点（和 View.draw 时一样），[area] 是 host 在 v 内容坐标里的范围。
     */
    private fun drawTree(v: View, canvas: Canvas, area: RectF) {
        if (v.visibility != View.VISIBLE || v.alpha <= 0f) return
        val left = v.scrollX.toFloat()
        val top = v.scrollY.toFloat()
        if (!RectF.intersects(area, RectF(left, top, left + v.width, top + v.height))) return
        when (v) {
            is WebView -> drawWebView(v, canvas, area)
            is ViewGroup -> {
                v.background?.let {
                    canvas.save()
                    canvas.translate(left, top)
                    it.draw(canvas)
                    canvas.restore()
                }
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i)
                    val tx = child.left + child.translationX - child.scrollX
                    val ty = child.top + child.translationY - child.scrollY
                    canvas.save()
                    canvas.translate(tx, ty)
                    drawTree(child, canvas, RectF(area.left - tx, area.top - ty, area.right - tx, area.bottom - ty))
                    canvas.restore()
                }
            }
            else -> v.draw(canvas)
        }
    }

    private var webBitmap: Bitmap? = null
    // WebView 用软件方式截图时会自己补一层白底（页面上它是透明的，透出米色），玻璃里就成了一片灰。
    // 用正片叠底画到已经画好的底色上：白底变回底色，文字和图片照常
    private val webPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        blendMode = BlendMode.MULTIPLY
    }

    private fun drawWebView(web: WebView, canvas: Canvas, area: RectF) {
        val left = web.scrollX.toFloat()
        val top = web.scrollY.toFloat()
        val r = RectF(left, top, left + web.width, top + web.height)
        if (!r.intersect(area) || r.width() < 1f || r.height() < 1f) return
        val scale = 0.5f
        val bw = Math.ceil((r.width() * scale).toDouble()).toInt().coerceAtLeast(1)
        val bh = Math.ceil((r.height() * scale).toDouble()).toInt().coerceAtLeast(1)
        var bitmap = webBitmap
        if (bitmap == null || bitmap.width < bw || bitmap.height < bh) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            webBitmap = bitmap
        }
        bitmap.eraseColor(Color.TRANSPARENT)
        val c = Canvas(bitmap)
        c.scale(scale, scale)
        c.translate(-r.left, -r.top)
        runCatching { web.draw(c) }
        canvas.drawBitmap(bitmap, Rect(0, 0, bw, bh), r, webPaint)
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        if (oval) {
            clip.reset()
            clip.addOval(RectF(bounds), Path.Direction.CW)
            canvas.clipPath(clip)
        } else if (cornerRadius > 0f) {
            val r = minOf(cornerRadius, bounds.width() / 2f, bounds.height() / 2f)
            clip.reset()
            clip.addRoundRect(RectF(bounds), r, r, Path.Direction.CW)
            canvas.clipPath(clip)
        } else if (bottomCornerRadius > 0f) {
            val r = minOf(bottomCornerRadius, bounds.width() / 2f, bounds.height().toFloat())
            clip.reset()
            clip.addRoundRect(RectF(bounds), floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r), Path.Direction.CW)
            canvas.clipPath(clip)
        } else {
            canvas.clipRect(bounds)
        }
        if (canvas.isHardwareAccelerated) {
            if (!node.hasDisplayList()) record()
            // 录的时候四周多了一圈，往回挪一圈画，裁掉多出来的部分
            canvas.save()
            canvas.translate(-recordedMargin.toFloat(), -recordedMargin.toFloat())
            canvas.drawRenderNode(node)
            canvas.restore()
            canvas.drawColor(tint)
        } else {
            canvas.drawColor(tintColor)
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
