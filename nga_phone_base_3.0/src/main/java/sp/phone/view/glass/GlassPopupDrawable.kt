package sp.phone.view.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.PixelCopy
import android.view.View
import androidx.core.graphics.ColorUtils
import org.xmlpull.v1.XmlPullParser

/**
 * Fork：弹出菜单的玻璃背景（res/drawable/fork_glass_popup.xml，做法同 S1 分支）。
 *
 * 弹出菜单是独立的窗口，拿不到下面的 View 来实时模糊。菜单打开时下面的内容不会变，
 * 所以第一次绘制时用 PixelCopy 复制 Activity 窗口里菜单底下那块，模糊后作为背景，再叠一层半透明的中性色
 * （菜单文字是深色时用白色，浅色时用黑色）。复制完成前先用较浓的颜色顶上，避免闪一下透明。
 */
class GlassPopupDrawable : Drawable() {

    private var density = Resources.getSystem().displayMetrics.density
    private val node = RenderNode("glass-popup")
    private var snapshot: Bitmap? = null
    private var requested = false
    private var tintColor = Color.BLACK
    private val clip = Path()
    private val location = IntArray(2)
    private val windowLocation = IntArray(2)

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, attrs, theme)
        density = r.displayMetrics.density
    }

    private val radius get() = GlassStyle.POPUP_CORNER_DP * density

    /**
     * 菜单所在的 View。View 体系的弹出菜单不用设（就是 callback）；Compose 的弹出菜单里设成 Popup 的 View，
     * 再把 callback 设成一个会触发重组的对象，截图好了会通过它通知重画。
     */
    var hostView: View? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        requested = false
    }

    override fun draw(canvas: Canvas) {
        val host = hostView ?: callback as? View
        if (!requested && host != null && host.isAttachedToWindow && !bounds.isEmpty) {
            requested = true
            requestSnapshot(host)
        }
        val b = RectF(bounds)
        clip.reset()
        clip.addRoundRect(b, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        val ready = snapshot != null && canvas.isHardwareAccelerated
        if (ready) {
            canvas.save()
            canvas.translate(b.left, b.top)
            canvas.drawRenderNode(node)
            canvas.restore()
        }
        GlassSettings.init()
        val opacity = GlassSettings.opacityPercent
        val alpha = if (ready) opacity else maxOf(opacity, 92)
        canvas.drawColor(ColorUtils.setAlphaComponent(tintColor, alpha * 255 / 100))
        canvas.restoreToCount(save)
    }

    private fun requestSnapshot(host: View) {
        val activity = host.context.findActivity() ?: return
        // 跟着菜单自己的文字颜色选玻璃深浅：深色字配白色玻璃，浅色字配黑色玻璃
        val tv = TypedValue()
        if (host.context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            val text = if (tv.resourceId != 0) host.context.getColorStateList(tv.resourceId).defaultColor else tv.data
            tintColor = if (ColorUtils.calculateLuminance(text) < 0.5) Color.WHITE
            else if (GlassStyle.isNight(host.context)) GlassStyle.NIGHT_TINT else Color.BLACK
        }
        val w = bounds.width()
        val h = bounds.height()
        host.getLocationOnScreen(location)
        activity.window.decorView.getLocationOnScreen(windowLocation)
        // 第一次绘制时菜单还在播放弹出动画（带位移），按动画结束后的最终位置截取
        val left = location[0] - windowLocation[0] + bounds.left - host.translationX.toInt()
        val top = location[1] - windowLocation[1] + bounds.top - host.translationY.toInt()
        val decor = activity.window.decorView
        val src = Rect(left, top, left + w, top + h)
        if (!src.intersect(0, 0, decor.width, decor.height) || src.isEmpty) return
        val bitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, src, bitmap, { result ->
            if (result != PixelCopy.SUCCESS) return@request
            snapshot = bitmap
            node.setPosition(0, 0, w, h)
            val px = GlassSettings.blurDp * density
            // 和顶栏一样：模糊之后提饱和度
            val saturate = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(GlassStyle.SATURATION) })
            )
            node.setRenderEffect(
                if (px > 0f) RenderEffect.createChainEffect(saturate, RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP))
                else saturate
            )
            val c = node.beginRecording(w, h)
            try {
                c.drawBitmap(bitmap, (src.left - left).toFloat(), (src.top - top).toFloat(), null)
            } finally {
                node.endRecording()
            }
            invalidateSelf()
        }, Handler(Looper.getMainLooper()))
    }

    override fun getPadding(padding: Rect): Boolean {
        val v = (8 * density).toInt()
        padding.set(0, v, 0, v)
        return true
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radius)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
