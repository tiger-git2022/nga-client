package sp.phone.common

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import gov.anzong.androidnga.base.util.ToastUtils
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Fork（tiger-git2022）：在应用里下载新版本的 APK 并调起系统安装，不用跳到浏览器。
 *
 * APK 下到 cacheDir/update/（同一个版本下完后再点直接安装），通过 FileProvider 交给系统安装器；
 * 第一次安装前系统会要求在设置里允许「安装未知应用」（权限 REQUEST_INSTALL_PACKAGES 只在 custom 构建的清单里声明）。
 */
object ForkUpdateDownloader {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running: Task? = null

    private class Task {
        @Volatile
        var cancelled = false
    }

    @JvmStatic
    fun download(activity: Activity, url: String, tag: String) {
        if (running != null) {
            ToastUtils.info("正在下载更新…")
            return
        }
        val dir = File(activity.cacheDir, "update")
        val name = "nga-custom-" + tag.replace(Regex("[^0-9A-Za-z._-]"), "_") + ".apk"
        val target = File(dir, name)
        if (target.isFile && target.length() > 0) {
            install(activity, target)
            return
        }
        // 旧版本的安装包和没下完的文件都删掉
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        val part = File(dir, "$name.part")

        val density = activity.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 1000
        }
        val text = TextView(activity).apply { text = "正在连接…" }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(progress)
            addView(text)
        }
        val task = Task()
        running = task
        val dialog = AlertDialog.Builder(activity)
            .setTitle("下载 $tag")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ -> task.cancelled = true }
            .show()

        thread(name = "fork-update-download") {
            val result = runCatching { fetch(url, part, task) { done, total -> post(progress, text, done, total) } }
            mainHandler.post {
                running = null
                if (dialog.isShowing && !activity.isDestroyed) runCatching { dialog.dismiss() }
                val error = result.exceptionOrNull()
                when {
                    task.cancelled -> part.delete()
                    error != null -> {
                        part.delete()
                        ToastUtils.error("下载失败：${error.message ?: error.javaClass.simpleName}")
                    }
                    !part.renameTo(target) -> ToastUtils.error("下载失败：无法保存安装包")
                    activity.isFinishing || activity.isDestroyed -> ToastUtils.success("新版本已下载，再次检查更新即可安装")
                    else -> install(activity, target)
                }
            }
        }
    }

    private var lastPost = 0L

    private fun post(progress: ProgressBar, text: TextView, done: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastPost < 100 && done != total) return
        lastPost = now
        mainHandler.post {
            if (total > 0) {
                progress.isIndeterminate = false
                progress.progress = (done * 1000 / total).toInt()
                text.text = String.format(Locale.US, "%.1f / %.1f MB", done / 1048576f, total / 1048576f)
            } else {
                text.text = String.format(Locale.US, "%.1f MB", done / 1048576f)
            }
        }
    }

    private fun fetch(url: String, out: File, task: Task, onProgress: (Long, Long) -> Unit) {
        var conn = URL(url).openConnection() as HttpURLConnection
        // GitHub 的下载地址会重定向到另一个域名，自己跟随，最多 5 次
        var redirects = 0
        while (true) {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val location = conn.getHeaderField("Location") ?: throw IOException("HTTP $code")
                conn.disconnect()
                conn = URL(URL(url), location).openConnection() as HttpURLConnection
                redirects++
                continue
            }
            if (code != 200) throw IOException("HTTP $code")
            break
        }
        try {
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (task.cancelled) return
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("下载不完整")
        } finally {
            conn.disconnect()
        }
    }

    private fun install(activity: Activity, apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("需要允许安装应用")
                .setMessage("新版本已下载。请在接下来的设置里允许本应用「安装未知应用」，返回后再点一次检查更新即可安装。")
                .setPositiveButton("去设置") { _, _ ->
                    runCatching {
                        activity.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName))
                        )
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName, apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }.onFailure { ToastUtils.error("无法打开安装程序") }
    }
}
