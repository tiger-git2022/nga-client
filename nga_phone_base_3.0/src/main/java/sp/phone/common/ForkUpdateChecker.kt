package sp.phone.common

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import com.alibaba.fastjson2.JSON
import gov.anzong.androidnga.BuildConfig
import gov.anzong.androidnga.base.util.ToastUtils
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Fork（tiger-git2022）：检查本仓库 GitHub Releases 上的新版本。
 *
 * 发布工作流会在每个 Release 里上传 update.json（tag_name、version_code、body、html_url、assets），
 * 通过 /releases/latest/download/update.json 读取，不走 api.github.com，没有每小时 60 次的限制。
 */
object ForkUpdateChecker {

    private val executor = Executors.newSingleThreadExecutor()

    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun isEnabled(): Boolean = BuildConfig.UPDATE_REPO.isNotEmpty()

    /**
     * @param manual 手动检查时，已是最新或检查失败也给提示
     */
    @JvmStatic
    fun check(activity: Activity, manual: Boolean) {
        if (!isEnabled()) {
            if (manual) ToastUtils.info("这个版本不检查更新")
            return
        }
        if (manual) ToastUtils.info("正在检查更新…")
        executor.execute {
            val release = runCatching { fetchLatest() }.getOrNull()
            mainHandler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                when {
                    release == null -> if (manual) ToastUtils.error("检查更新失败，请稍后重试")
                    release.versionCode > localVersionCode(activity) -> showDialog(activity, release)
                    manual -> ToastUtils.success("已是最新版本（${BuildConfig.VERSION_NAME}）")
                }
            }
        }
    }

    private class Release(
        val tag: String,
        val versionCode: Long,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?,
    )

    private fun fetchLatest(): Release? {
        val url = URL("https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest/download/update.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSON.parseObject(text) ?: return null
            val tag = obj.getString("tag_name") ?: return null
            val code = obj.getLongValue("version_code")
            if (code <= 0) return null
            val apk = obj.getJSONArray("assets")
                ?.mapNotNull { it as? com.alibaba.fastjson2.JSONObject }
                ?.firstOrNull { it.getString("name")?.endsWith(".apk") == true }
                ?.getString("browser_download_url")
            return Release(
                tag = tag,
                versionCode = code,
                body = obj.getString("body") ?: "",
                htmlUrl = obj.getString("html_url") ?: "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest",
                apkUrl = apk,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun localVersionCode(activity: Activity): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    private fun showDialog(activity: Activity, release: Release) {
        // 说明里中文在前、英文在 "---" 之后，只显示中文部分
        val notes = release.body.substringBefore("\n---").trim().let {
            if (it.length > 1500) it.substring(0, 1500) + "…" else it
        }
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${release.tag}")
            .setMessage("当前版本 ${BuildConfig.VERSION_NAME}\n\n$notes")
            .setPositiveButton("下载") { _, _ -> openUrl(activity, release.apkUrl ?: release.htmlUrl) }
            .setNeutralButton("查看发布页") { _, _ -> openUrl(activity, release.htmlUrl) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun openUrl(activity: Activity, url: String) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            ToastUtils.error("没有可以打开链接的应用")
        }
    }
}
