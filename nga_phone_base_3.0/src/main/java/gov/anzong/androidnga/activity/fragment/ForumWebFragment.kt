package gov.anzong.androidnga.activity.fragment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.MenuProvider
import com.justwen.androidnga.ui.fragment.WebViewFragment
import gov.anzong.androidnga.R
import sp.phone.common.ReadProgressManager
import sp.phone.common.UserManagerImpl

class ForumWebFragment : WebViewFragment() {

    /** 第一次打开帖子时要滚动到的楼层（来自阅读进度），-1 表示不需要 */
    private var pendingFloor = -1

    /** 从帖子列表带过来的总楼数（回复数 + 1），网页不在最后一页时拿不到准确的总楼数，用它兜底 */
    private var knownRows = 0

    private val scrollHandler = Handler(Looper.getMainLooper())

    private val saveRunnable = Runnable { saveWebProgress() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingFloor = arguments?.getInt(ARG_SCROLL_FLOOR, -1) ?: -1
        knownRows = arguments?.getInt(ARG_ROWS, 0) ?: 0
        activity?.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.fragment_webview, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.menu_open_by_browser) {
                    return startExternalBrowser(requireContext(), webView!!.url)
                }
                return false
            }

        }, this)
    }

    private fun startExternalBrowser(context: Context, url: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(url))
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            return false
        }
    }

    /**
     * 登录是在 ngabbs.com 的网页里完成的，cookie 只记在那个域名下；
     * 网页模式打开的是当前选择的域名（默认 bbs.nga.cn），这里把当前账号的 cookie 同步过去，否则网页显示未登录。
     */
    private fun syncLoginCookie(url: String?) {
        val cookie = UserManagerImpl.getInstance().cookie
        if (cookie.isNullOrEmpty()) {
            return
        }
        val hosts = linkedSetOf("bbs.nga.cn", "ngabbs.com", "nga.178.com")
        url?.let { Uri.parse(it).host }?.let { hosts.add(it) }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        for (host in hosts) {
            // 服务器会给游客在父域（如 .nga.cn）下发 ngaPassportUid=guest…，必须写到同一个父域上覆盖掉，
            // 否则请求里会同时带上游客 uid 和登录 uid，服务器仍然当作未登录
            val labels = host.split(".")
            val parentDomain = if (labels.size > 2) "." + labels.takeLast(2).joinToString(".") else ".$host"
            for (item in cookie.split(";")) {
                val pair = item.trim()
                if (pair.isNotEmpty()) {
                    cookieManager.setCookie("https://$host", "$pair; domain=$parentDomain; path=/")
                    cookieManager.setCookie("https://$host", "$pair; path=/")
                }
            }
        }
        cookieManager.flush()
    }

    private fun initWebView() {
        webView?.apply {
            val webSettings = settings
            webSettings.javaScriptEnabled = true
            webSettings.javaScriptCanOpenWindowsAutomatically = true
            webSettings.loadWithOverviewMode = true
            webSettings.textZoom = 100
            webSettings.setSupportZoom(true)
            webSettings.builtInZoomControls = true
            webSettings.useWideViewPort = true

            setOnScrollChangeListener { _, _, _, _, _ ->
                // 滚动停下 1 秒后记录一次
                scrollHandler.removeCallbacks(saveRunnable)
                scrollHandler.postDelayed(saveRunnable, 1000)
            }

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val urlStr = request.url.toString()
                    val host = request.url.host
                    if (host.isNullOrEmpty()) {
                        return super.shouldOverrideUrlLoading(view, request)
                    }
                    if ((host.contains("nga") || host.contains("178"))
                        && (urlStr.contains("&rand") || urlStr.contains("?rand"))
                    ) {
                        return super.shouldOverrideUrlLoading(view, request)
                    }
                    view.loadUrl(urlStr)
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.title?.let {
                        setTitle(it)
                    }
                    onThreadPageFinished(view, url)
                    super.onPageFinished(view, url)
                }
            }

        }
    }

    // ---------------- 网页模式的阅读进度 ----------------

    private class ThreadUrl(val tid: Int, val page: Int)

    private fun parseThreadUrl(url: String?): ThreadUrl? {
        if (url.isNullOrEmpty()) return null
        val uri = Uri.parse(url)
        if (uri.path?.endsWith("read.php") != true) return null
        // 跳到某一楼、只看某人的页面不记录
        if (!uri.getQueryParameter("pid").isNullOrEmpty()) return null
        if (!uri.getQueryParameter("authorid").isNullOrEmpty()) return null
        val tid = uri.getQueryParameter("tid")?.trim()?.toIntOrNull() ?: return null
        val pageStr = uri.getQueryParameter("page")
        val page = if (pageStr.isNullOrEmpty()) 1 else pageStr.trim().toIntOrNull() ?: return null
        return ThreadUrl(tid, page)
    }

    private fun onThreadPageFinished(view: WebView, url: String) {
        if (parseThreadUrl(url) == null) return
        val floor = pendingFloor
        pendingFloor = -1
        if (floor > 0) {
            view.evaluateJavascript("($JS_SCROLL_TO_FLOOR)($floor)") { saveWebProgress() }
        } else {
            saveWebProgress()
        }
    }

    /** 读取网页当前看到的楼层和总楼数，写入阅读进度 */
    private fun saveWebProgress() {
        val view = webView ?: return
        val thread = parseThreadUrl(view.url) ?: return
        val title = view.title
        view.evaluateJavascript(JS_READ_STATE) { result ->
            // 结果形如 "\"3,12,215,1\""：第一个可见楼层,最后一个可见楼层,总楼数,总楼数是否准确（拿不到时为 -1）
            val parts = result?.trim('"')?.split(",") ?: emptyList()
            val first = parts.getOrNull(0)?.toIntOrNull() ?: -1
            val last = parts.getOrNull(1)?.toIntOrNull() ?: -1
            val jsRows = parts.getOrNull(2)?.toIntOrNull() ?: -1
            val exact = parts.getOrNull(3) == "1"
            if (first < 0 || last < 0) {
                return@evaluateJavascript
            }
            val rows = if (exact) jsRows else maxOf(jsRows, knownRows)
            val index = (first - (thread.page - 1) * PAGE_SIZE).coerceIn(0, PAGE_SIZE - 1)
            ReadProgressManager.save(
                thread.tid, thread.page, index, 0, last,
                rows.coerceAtLeast(0),
                title?.takeIf { it.isNotBlank() && !it.contains("NGA玩家社区") }
            )
        }
    }

    override fun onPause() {
        scrollHandler.removeCallbacks(saveRunnable)
        saveWebProgress()
        super.onPause()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initWebView()
        syncLoginCookie(url)
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {

        const val ARG_SCROLL_FLOOR = "scroll_floor"

        const val ARG_ROWS = "rows"

        private const val PAGE_SIZE = 20

        /** 用网页打开帖子时的参数：带上阅读进度里的页码和楼层，以及列表里的回复数 */
        @JvmStatic
        fun threadUrl(host: String, tid: Int, page: Int): String {
            val base = host.trimEnd('/') + "/read.php?tid=" + tid
            return if (page > 1) "$base&page=$page" else base
        }

        /** 阅读进度里记录的、在 page 页上要回到的楼层；没有记录时为 -1 */
        @JvmStatic
        fun restoreFloor(tid: Int, page: Int): Int {
            val progress = ReadProgressManager.get(tid) ?: return -1
            if (progress.page != page) return -1
            return (page - 1) * PAGE_SIZE + progress.index
        }

        /** 从列表带过来的帖子信息（JSON）里取总楼数 */
        @JvmStatic
        fun topicRows(topicInfo: String?): Int {
            if (topicInfo.isNullOrEmpty()) return 0
            return try {
                val obj = com.alibaba.fastjson2.JSON.parseObject(topicInfo)
                obj.getIntValue("replies") + 1
            } catch (e: Exception) {
                0
            }
        }

        /**
         * NGA 网页里每一楼都有一个 name 为 "l楼层号" 的锚点。
         * 返回 "最后一个进入屏幕的楼层,总楼数"，总楼数取自页面脚本里的 __PAGE / __ROWS，取不到为 -1。
         */
        private const val JS_READ_STATE = """
(function(){
  var first=-1,last=-1,maxFloor=-1;
  var anchors=document.querySelectorAll('a[name^="l"]');
  for(var i=0;i<anchors.length;i++){
    var m=/^l(\d+)$/.exec(anchors[i].getAttribute('name'));
    if(!m) continue;
    var f=parseInt(m[1]);
    maxFloor=Math.max(maxFloor,f);
    var r=anchors[i].getBoundingClientRect();
    if(r.width==0&&r.height==0&&r.top==0) continue;
    if(r.top<window.innerHeight){
      last=Math.max(last,f);
      if(r.top<=window.innerHeight*0.3) first=Math.max(first,f);
    }
  }
  if(first<0&&last>=0){
    first=last;
    for(var j=0;j<anchors.length;j++){
      var n=/^l(\d+)$/.exec(anchors[j].getAttribute('name'));
      var t=anchors[j].getBoundingClientRect();
      if(n&&t.top>=0&&t.top<window.innerHeight){ first=Math.min(first,parseInt(n[1])); }
    }
  }
  var rows=-1,exact=0,P=window.__PAGE;
  if(P&&P[1]&&P[2]&&P[3]){
    if(P[2]>=P[1]&&maxFloor>=0){ rows=maxFloor+1; exact=1; }
    else { rows=(P[1]-1)*P[3]+1; }
  }
  return first+','+last+','+rows+','+exact;
})()
"""

        private const val JS_SCROLL_TO_FLOOR = """
function(floor){
  var a=document.querySelector('a[name="l'+floor+'"]');
  if(a){ a.scrollIntoView(); }
  return !!a;
}
"""
    }
}
