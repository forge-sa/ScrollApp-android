package dev.wardar.scrollapp.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.wardar.scrollapp.Platform
import dev.wardar.scrollapp.feed.PlatformFeed

/** Signs in on the site itself; the session cookies are shared with the feed's WebViews. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(platform: Platform, onDone: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onDone()
    }
    Surface(Modifier.fillMaxSize(), color = Ink.Page) {
        Column(Modifier.systemBarsPadding()) {
            ScreenHeader("Log in to ${platform.title}", onBack = onDone, action = {
                TextButton(onClick = onDone) { Text("Done") }
            })
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = PlatformFeed.browserUserAgent(settings.userAgentString, desktop = false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme
                                return scheme != "http" && scheme != "https"
                            }
                        }
                        loadUrl(platform.loginUrl)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
