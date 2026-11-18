package space.liushenme.markdownreader.ui.screens.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.InputType
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.widget.EditText
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import space.liushenme.markdownreader.R

/**
 * 合成基地址：给本地 HTML 一个真实的 https origin。
 *
 * `loadDataWithBaseURL(null, ...)` 得到的是 opaque origin，页面里访问
 * `localStorage` / `navigator.clipboard` 等会抛 SecurityError；
 * 若页面脚本在初始化阶段就用了它们（很多导出的报告类 HTML 会做主题持久化），
 * 异常会中断整个 IIFE，导致后续所有按钮的监听器都没挂上——表现就是「按钮全部失效」。
 */
private const val HTML_BASE_URL = "https://mdreader.local/"

/** 注入到页面里的兜底脚本：window.print 走系统打印，clipboard 不可用时走原生剪贴板。 */
private val READER_JS_SHIM = """
(function(){
  try {
    if (window.AndroidReader) {
      if (!window.__mdReaderPrintPatched) {
        window.__mdReaderPrintPatched = true;
        window.print = function(){ try { AndroidReader.printPage(); } catch (e) {} };
      }
      var nav = window.navigator;
      var clip = nav.clipboard;
      if (!clip) {
        clip = {};
        try { Object.defineProperty(nav, 'clipboard', { value: clip, configurable: true }); } catch (e) {}
      }
      if (!clip.writeText) {
        clip.writeText = function(text){ try { AndroidReader.copyText(String(text)); } catch (e) {} return Promise.resolve(); };
      } else if (!clip.__mdReaderPatched) {
        var original = clip.writeText.bind(clip);
        clip.__mdReaderPatched = true;
        clip.writeText = function(text){
          return original(text).catch(function(){
            try { AndroidReader.copyText(String(text)); } catch (e) {}
          });
        };
      }
    }
    // 修复 vh 塌陷导致的目录面板高度为 0：某些页面用 max-height:62vh 做弹层，
    // 但 WebView 里 vh 可能算成 0（innerHeight 正常），面板只剩标题、内容被裁掉。
    // 仅在「固定定位且 max-height 近似 0」时按 innerHeight 的 72% 补一个 px 上限。
    function mdReaderRepairTocPanels() {
      try {
        var els = document.querySelectorAll('.toc, #tocPanel, [class*="toc-panel"], [class*="tocPanel"]');
        for (var i = 0; i < els.length; i++) {
          var el = els[i];
          var cs = getComputedStyle(el);
          if (cs.position !== 'fixed') continue;
          var rect = el.getBoundingClientRect();
          if (cs.display === 'none' || rect.height <= 0) continue;
          var mh = parseFloat(cs.maxHeight);
          if (!mh || mh < 50) {
            el.style.maxHeight = Math.round((window.innerHeight || 800) * 0.72) + 'px';
            el.style.overflow = 'auto';
          }
        }
      } catch (e) {}
    }
    document.addEventListener('click', function(){ setTimeout(mdReaderRepairTocPanels, 30); }, true);
    window.addEventListener('resize', mdReaderRepairTocPanels);
    setTimeout(mdReaderRepairTocPanels, 600);
  } catch (e) {}
})();
""".trimIndent()

/**
 * JS ↔ 原生桥接：只暴露「打印当前页」和「写入剪贴板」两个能力，不暴露任何文件/网络访问。
 */
/**
 * 等 WebView 真正有尺寸后再 load。
 *
 * 在 Composable 的 factory 阶段 WebView 还没 measure，此时 loadDataWithBaseURL 会让页面
 * 的 initial containing block 记成 0×0 —— 之后 `1vh` 永远等于 0（innerHeight 明明有值），
 * 页面上所有用 `vh` 的高度限制都会塌成 0：典型表现就是固定定位的目录面板只剩标题、
 * 内容区被 max-height:0 裁掉。等有尺寸再加载就能拿到正确的视口高度。
 */
private fun WebView.loadHtmlWhenSized(baseUrl: String, html: String) {
    fun load() {
        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", baseUrl)
    }
    if (width > 0 && height > 0) {
        load()
        return
    }
    addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
        override fun onLayoutChange(
            view: android.view.View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int,
        ) {
            if (view.width > 0 && view.height > 0) {
                view.removeOnLayoutChangeListener(this)
                load()
            }
        }
    })
}

private class ReaderHtmlBridge(private val webView: WebView) {
    @JavascriptInterface
    fun printPage() {
        webView.post {
            runCatching {
                val printManager = webView.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    webView.createPrintDocumentAdapter("MD阅读器")
                } else {
                    @Suppress("DEPRECATION")
                    webView.createPrintDocumentAdapter()
                }
                printManager.print(
                    "MD阅读器",
                    adapter,
                    PrintAttributes.Builder().build(),
                )
            }.onFailure { Log.w("HtmlReader", "print failed: ${it.javaClass.simpleName}") }
        }
    }

    @JavascriptInterface
    fun copyText(text: String?) {
        if (text.isNullOrEmpty()) return
        webView.post {
            runCatching {
                val clipboard = webView.context
                    .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("text", text))
            }
        }
    }
}

/**
 * 用系统对话框承接 WebView 的 JS 弹窗。
 *
 * 必须在主线程创建；[configure] 里绑定按钮回调，取消/返回键统一走 [JsResult.cancel]，
 * 避免 JS 端 confirm/prompt 永远挂起或直接返回 false。
 */
private fun showJsDialog(
    webView: WebView,
    onFailure: () -> Unit,
    configure: (AlertDialog.Builder) -> Unit,
) {
    val shown = runCatching {
        val builder = AlertDialog.Builder(webView.context)
            .setTitle(android.R.string.dialog_alert_title)
        configure(builder)
        builder.create().apply {
            setCanceledOnTouchOutside(true)
            setOnDismissListener(DialogInterface.OnDismissListener { })
            show()
        }
    }.isSuccess
    // 对话框没能弹出时也要给 JS 一个确定结果，否则页面会一直等着回调。
    if (!shown) onFailure()
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
internal fun ReaderHtmlBody(
    html: String,
    modifier: Modifier = Modifier,
    baseUrl: String = "https://mdreader.local/",
    onScrollProgress: (Float) -> Unit,
    onTitleChanged: (String) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onTocReady: (List<HtmlTocEntry>) -> Unit = {},
    onControllerReady: (HtmlViewerController?) -> Unit = {},
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val uri = result.data?.data
        val ok = result.resultCode == Activity.RESULT_OK && uri != null
        callback.onReceiveValue(if (ok) arrayOf(uri) else null)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).also { webView ->
                webViewRef = webView
                onControllerReady(
                    HtmlViewerController(
                        reload = { webView.reload() },
                        scrollToAnchor = { anchorId ->
                            val quoted = org.json.JSONObject.quote(anchorId)
                            webView.evaluateJavascript(
                                "try{document.getElementById($quoted).scrollIntoView({block:'start'});}catch(e){}",
                                null,
                            )
                        },
                    ),
                )
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true
                    allowFileAccess = false
                    allowContentAccess = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    supportZoom()
                    // H5 应用常见的存储/媒体能力：DOM storage 已在上面开启，这里补上
                    // 多窗口与数据库支持，避免 target=_blank / 旧版 storage API 直接失效。
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = false
                    databaseEnabled = true
                }
                webView.isVerticalScrollBarEnabled = true
                webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webView.addJavascriptInterface(ReaderHtmlBridge(webView), "AndroidReader")
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val scheme = request.url.scheme?.lowercase()
                        return scheme != "http" && scheme != "https"
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onUrlChanged(url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onUrlChanged(url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        onUrlChanged(url)
                        // 兜底：window.print / navigator.clipboard 在 WebView 里不一定可用，
                        // 用原生能力补上，避免页面按钮点了没反应。
                        view.evaluateJavascript(READER_JS_SHIM, null)
                        // 提取页面目录：页面自带的目录面板在 WebView 里不一定好用，
                        // 这里再提供一个原生底部目录，保证一定能看到完整目录。
                        view.evaluateJavascript(READER_TOC_JS) { raw ->
                            onTocReady(parseHtmlToc(raw))
                        }
                    }
                }
                // 页面内的 alert / confirm / prompt 默认会被 WebView 直接丢弃（onJsAlert 返回 false
                // 等价于取消），表现为「按钮点了没反应」。这里统一用系统对话框承接，
                // 并把结果回填给 JS，页面里的交互逻辑才能继续执行。
                webView.setDownloadListener(DownloadListener { url, _, _, _, _ ->
                    // 浏览器式行为：下载链接交给系统处理，避免点了完全没反应。
                    if (url.isBlank()) return@DownloadListener
                    runCatching {
                        webView.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                })
                // 页面里的 alert / confirm / prompt 默认会被 WebView 直接丢弃（onJsAlert 返回 false
                // 等价于取消），表现为「按钮点了没反应」。这里统一用系统对话框承接，
                // 并把结果回填给 JS，页面里的交互逻辑才能继续执行。
                webView.webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (!title.isNullOrBlank()) onTitleChanged(title)
                    }

                    /** H5 页面里的 `<input type=file>`：走系统文件选择器。 */
                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams,
                    ): Boolean {
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = filePathCallback
                        return runCatching {
                            val intent = fileChooserParams.createIntent()
                            intent.addCategory(Intent.CATEGORY_OPENABLE)
                            fileChooserLauncher.launch(intent)
                            true
                        }.getOrElse {
                            fileChooserCallback = null
                            filePathCallback.onReceiveValue(null)
                            false
                        }
                    }

                    /** target=_blank / window.open：没有独立窗口，直接在同一个 WebView 打开。 */
                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message,
                    ): Boolean {
                        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                        val newWebView = WebView(view.context)
                        newWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                v: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                view.loadUrl(request.url.toString())
                                v.destroy()
                                return true
                            }
                        }
                        transport.webView = newWebView
                        resultMsg.sendToTarget()
                        return true
                    }

                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        // 页面脚本出错时留在 logcat，便于排查「某个按钮没反应」。
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            Log.w(
                                "HtmlReader",
                                "${message.message()} @${message.sourceId()}:${message.lineNumber()}",
                            )
                        }
                        return true
                    }

                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult,
                    ): Boolean {
                        showJsDialog(view, onFailure = { result.confirm() }) { builder ->
                            builder.setMessage(message)
                                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                                .setOnCancelListener { result.cancel() }
                        }
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult,
                    ): Boolean {
                        showJsDialog(view, onFailure = { result.cancel() }) { builder ->
                            builder.setMessage(message)
                                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                                .setOnCancelListener { result.cancel() }
                        }
                        return true
                    }

                    override fun onJsPrompt(
                        view: WebView,
                        url: String,
                        message: String,
                        defaultValue: String?,
                        result: JsPromptResult,
                    ): Boolean {
                        val input = EditText(view.context).apply {
                            setText(defaultValue.orEmpty())
                            inputType = InputType.TYPE_CLASS_TEXT
                            setSingleLine()
                        }
                        showJsDialog(view, onFailure = { result.cancel() }) { builder ->
                            builder.setMessage(message)
                                .setView(input)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    result.confirm(input.text?.toString().orEmpty())
                                }
                                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                                .setOnCancelListener { result.cancel() }
                        }
                        return true
                    }
                }
                webView.setOnScrollChangeListener { view, _, scrollY, _, _ ->
                    val target = view as WebView
                    val contentHeight = target.contentHeight * target.scale
                    val maxScroll = max(1f, contentHeight - target.height)
                    onScrollProgress((scrollY / maxScroll).coerceIn(0f, 1f))
                }
                webView.tag = html
                webView.loadHtmlWhenSized(baseUrl, html)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadHtmlWhenSized(baseUrl, html)
            }
        },
        onRelease = { webView ->
            if (webViewRef == webView) webViewRef = null
            onControllerReady(null)
            webView.destroy()
        },
    )
}

/** HTML 页面的目录条目（由页面 h1~h4 提取）。 */
internal data class HtmlTocEntry(
    val title: String,
    val level: Int,
    val anchorId: String,
)

/** 暴露给 Compose 的 WebView 控制能力。 */
internal class HtmlViewerController(
    val reload: () -> Unit,
    val scrollToAnchor: (String) -> Unit,
)

/** 收集页面标题（h1~h4），没有 id 的补一个稳定 id。 */
private val READER_TOC_JS = """
(function(){
  try {
    var heads = document.querySelectorAll('h1,h2,h3,h4');
    var out = [];
    for (var i = 0; i < heads.length; i++) {
      var h = heads[i];
      if (!h.id) { h.id = 'mdreader-h-' + i; }
      var t = (h.innerText || h.textContent || '').replace(/\s+/g, ' ')
        .replace(/^#+\s*/, '').replace(/#+\s*$/, '').trim();
      if (!t) continue;
      out.push({ t: t.substring(0, 120), l: parseInt(h.tagName.substring(1), 10) || 2, i: h.id });
    }
    return JSON.stringify(out);
  } catch (e) { return '[]'; }
})()
""".trimIndent()

/**
 * 浏览器式标题 / 地址栏（HTML 书籍专用，常驻显示）。
 *
 * 第一行是页面标题（`<title>`），第二行是本地合成地址；右侧刷新按钮重新加载页面。
 * 地址栏只是展示用：本地页面统一挂在 `https://mdreader.local/book/<id>/`
 * 这个合成 origin 下，页面里的 localStorage / clipboard 等 API 才能正常工作。
 */
@Composable
internal fun ReaderHtmlAddressBar(
    title: String,
    url: String,
    onOpenToc: (() -> Unit)?,
    onReload: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onOpenToc != null) {
                IconButton(onClick = onOpenToc, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Toc,
                        contentDescription = stringResource(R.string.reader_html_toc),
                    )
                }
            }
            if (onReload != null) {
                IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.reader_html_reload),
                    )
                }
            }
        }
        Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 解析 [READER_TOC_JS] 的返回值（JSON 字符串，可能被再包一层引号）。 */
internal fun parseHtmlToc(raw: String?): List<HtmlTocEntry> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    val json = runCatching {
        val unquoted = org.json.JSONTokener(raw).nextValue()
        when (unquoted) {
            is String -> unquoted
            is org.json.JSONArray -> unquoted.toString()
            else -> return emptyList()
        }
    }.getOrElse { return emptyList() }
    val array = runCatching { org.json.JSONArray(json) }.getOrElse { return emptyList() }
    val result = ArrayList<HtmlTocEntry>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val title = item.optString("t").trim()
        val anchor = item.optString("i").trim()
        if (title.isEmpty() || anchor.isEmpty()) continue
        result += HtmlTocEntry(
            title = title,
            level = item.optInt("l", 2).coerceIn(1, 6),
            anchorId = anchor,
        )
    }
    return result
}

/**
 * 原生目录底部弹层（HTML 书籍）。
 *
 * 页面自带的 TOC 面板依赖各页面的 CSS，在 WebView 里经常高度异常；
 * 这里用兜底目录保证一定能看到完整目录并跳转。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HtmlTocSheet(
    entries: List<HtmlTocEntry>,
    onSelect: (HtmlTocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.reader_html_toc),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(bottom = 24.dp),
        ) {
            items(entries) { entry ->
                val indent = ((entry.level - 1).coerceIn(0, 3) * 16).dp
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry) }
                        .padding(start = 20.dp + indent, end = 20.dp, top = 10.dp, bottom = 10.dp),
                )
            }
        }
    }
}
