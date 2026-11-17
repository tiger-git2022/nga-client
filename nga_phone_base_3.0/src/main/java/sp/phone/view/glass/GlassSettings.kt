package sp.phone.view.glass

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import gov.anzong.androidnga.base.util.ContextUtils

/**
 * Fork：玻璃效果的模糊度和不透明度，在「设置 → 玻璃效果」里调（res/xml/fork_settings_glass.xml，同 S1 分支）。
 * 改动后立即生效：玻璃在下一次绘制前读取新值（[version] 变了就重新应用）。
 */
object GlassSettings {

    const val KEY_BLUR_DP = "fork_glass_blur_dp"
    const val KEY_OPACITY = "fork_glass_opacity"
    const val DEFAULT_BLUR_DP = 10
    const val DEFAULT_OPACITY = 40

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    var blurDp: Int = DEFAULT_BLUR_DP
        private set

    /** 染色的不透明度，0..100 */
    @Volatile
    var opacityPercent: Int = DEFAULT_OPACITY
        private set

    /** 每次设置变化加一 */
    @Volatile
    var version: Int = 0
        private set

    // 强引用，避免监听被回收
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == KEY_BLUR_DP || key == KEY_OPACITY) read(p)
    }

    fun init() {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = PreferenceManager.getDefaultSharedPreferences(ContextUtils.getContext())
            read(p)
            p.registerOnSharedPreferenceChangeListener(listener)
            prefs = p
        }
    }

    private fun read(p: SharedPreferences) {
        blurDp = p.getInt(KEY_BLUR_DP, DEFAULT_BLUR_DP).coerceIn(0, 40)
        opacityPercent = p.getInt(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(0, 100)
        version++
    }
}
