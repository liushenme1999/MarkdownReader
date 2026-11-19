package space.liushenme.markdownreader.ui.screens.legal

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.model.LegalWebDocument
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

/**
 * 在线协议页：顶部返回 / 居中标题 / 右上角用系统浏览器打开，正文用 WebView 嵌入官网页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalWebScreen(
    document: LegalWebDocument,
    navController: NavController,
) {
    val context = LocalContext.current
    val pageBg = shelfStylePageBackground()
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }

    val openInBrowser = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(document.url))
        runCatching { context.startActivity(intent) }
        Unit
    }

    BackHandler {
        val view = webViewHolder.value
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            navController.navigateUp()
        }
    }

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                CenterAlignedTopAppBar(
                    windowInsets = WindowInsets(),
                    title = {
                        Text(
                            text = stringResource(document.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back_cd),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = openInBrowser) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.legal_web_open_in_browser),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(paddingValues),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.setSupportZoom(false)
                        setBackgroundColor(android.graphics.Color.WHITE)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                progress = 100
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    loadFailed = true
                                    progress = 100
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url
                                val host = url.host.orEmpty()
                                if (host.endsWith("liushenme.space")) {
                                    // 站内跳转保持嵌入式布局，避免又出现官网导航栏。
                                    if (!url.toString().contains("embed=1")) {
                                        view.loadUrl(withEmbed(url.toString()))
                                        return true
                                    }
                                    return false
                                }
                                return runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                    true
                                }.getOrDefault(false)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress >= 100) {
                                    progress = 100
                                    loadFailed = false
                                } else {
                                    progress = newProgress
                                }
                            }
                        }
                        webViewHolder.value = this
                        loadUrl(document.embedUrl)
                    }
                },
            )

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }

            if (loadFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pageBg)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.legal_web_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        loadFailed = false
                        progress = 0
                        webViewHolder.value?.reload()
                    }) {
                        Text(stringResource(R.string.legal_web_retry))
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.value?.apply {
                stopLoading()
                destroy()
            }
            webViewHolder.value = null
        }
    }
}

/** 给官网地址追加 embed=1，官网据此隐藏导航栏、主题按钮与页脚。 */
private fun withEmbed(url: String): String =
    if (url.contains("embed=1")) {
        url
    } else {
        url + if (url.contains('?')) "&embed=1" else "?embed=1"
    }
