package dev.wardar.scrollapp.feed

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.wardar.scrollapp.Platform
import org.json.JSONObject

/** What the page script last reported about the item on screen. */
data class FeedState(
    val id: String? = null,
    val skipKey: String? = null,
    val hasVideo: Boolean = false,
    /** The item has actually played (a video frozen behind a login wall has not). */
    val progressed: Boolean = false,
    /** "ad", "suggested" or "live" when the item should be skipped. */
    val skip: String? = null,
    val liked: Boolean? = null,
    val panelOpen: Boolean = false,
)

/** A point the page asked to be tapped, in CSS pixels. */
data class PageControl(val x: Float, val y: Float, val cssWidth: Float, val liked: Boolean?)

/**
 * One platform's feed: a WebView on the site's own short-video feed, plus the bookkeeping
 * that maps "the Nth item of this feed" onto swipes inside the page.
 */
@SuppressLint("SetJavaScriptEnabled")
class PlatformFeed(
    context: Context,
    val platform: Platform,
    script: String,
    desktop: Boolean,
    private val listener: Listener,
) {
    interface Listener {
        fun onFeedState(feed: PlatformFeed, state: FeedState)
        fun onVideoEnded(feed: PlatformFeed)
        fun onFeedCrashed(feed: PlatformFeed)

        /** The page loaded again by itself, so it is back at its first item. */
        fun onFeedReloaded(feed: PlatformFeed)
    }

    val view = WebView(context)
    var state = FeedState()
        private set

    /** Item the page is on, counted in swipes from the first one. */
    var position = 0
        private set
    private var target = 0
    private var stepping = false
    private var settledAt = 0L

    /** First item not yet placed in the unified feed. */
    var nextFresh = 0

    /** Consecutive skipped items; a runaway streak means the ad markers match normal videos. */
    var skipStreak = 0
    var lastSkipKey: String? = null
    var emptyStreak = 0
    var suspended = false

    /** Whether anything has played since the page loaded; cold starts get longer before giving up. */
    var everPlayed = false

    /** The page has shown a video, so its feed exists and can be swiped. */
    var ready = false
        private set
    private var loads = 0

    private var active = false
    private var sound = true
    private val handler = Handler(Looper.getMainLooper())
    private val pageScript = platform.pageScript(script)

    /** True while the page is moving or has just moved, when its reports may still describe the old item. */
    val busy: Boolean
        get() = stepping || position != target || SystemClock.uptimeMillis() - settledAt < 700

    init {
        view.setBackgroundColor(Color.BLACK)
        view.outlineProvider = null
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            userAgentString = browserUserAgent(userAgentString, desktop)
            if (desktop) {
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(Bridge(), "ScrollAppBridge")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, pageScript, platform.origins)
        }
        view.webViewClient = Client()
        view.webChromeClient = WebChromeClient()
        view.loadUrl(platform.feedUrl)
    }

    fun reload() {
        resetPosition()
        nextFresh = 0
        skipStreak = 0
        emptyStreak = 0
        suspended = false
        everPlayed = false
        view.loadUrl(platform.feedUrl)
    }

    fun resetPosition() {
        handler.removeCallbacksAndMessages(null)
        position = 0
        target = 0
        stepping = false
        ready = false
        state = FeedState()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    // ---------- playback ----------

    /**
     * Only the front feed may hold focus: a background feed taking it (from a lined-up swipe)
     * makes the front page lose focus, and some players pause on that.
     */
    fun setFront(front: Boolean) {
        view.isFocusable = front
        view.isFocusableInTouchMode = front
        if (front) view.requestFocus() else view.clearFocus()
    }

    fun setActive(on: Boolean, restart: Boolean, sound: Boolean) {
        active = on
        this.sound = sound
        js("__sh.setActive($on, $restart, $sound)")
    }

    fun setSound(on: Boolean) {
        sound = on
        js("__sh.setSound($on)")
    }

    // ---------- moving through the page's own feed ----------

    fun seek(index: Int) {
        target = index.coerceAtLeast(0)
        pump()
    }

    private fun pump() {
        if (stepping || position == target) return
        val before = state.id
        if (before == null || !ready) {
            // Nothing on screen yet (still loading, or a login wall): nowhere to swipe to.
            target = position
            return
        }
        stepping = true
        val dir = if (target > position) 1 else -1
        step(dir, before, byScript = preferScript, fallback = true)
    }

    /** Some pages ignore touch swipes (e.g. under a login modal); remember what worked for this one. */
    private var preferScript = false

    private fun step(dir: Int, before: String, byScript: Boolean, fallback: Boolean) {
        if (byScript) js("__sh.nav($dir)") else swipe(dir)
        // Checked twice, so a slow page is not mistaken for one that ignored the move (and moved twice).
        handler.postDelayed({
            if (state.id != before) return@postDelayed finishStep(dir)
            handler.postDelayed({
                when {
                    state.id != before -> {
                        if (!fallback) preferScript = byScript
                        finishStep(dir)
                    }
                    fallback -> {
                        Log.d("ScrollApp", "$platform ignored the ${if (byScript) "script scroll" else "swipe"}, trying the other way")
                        step(dir, before, !byScript, fallback = false)
                    }
                    // Neither moved it; count the step anyway rather than retry forever.
                    else -> finishStep(dir)
                }
            }, 800)
        }, 1200)
    }

    private fun finishStep(dir: Int) {
        position += dir
        stepping = false
        settledAt = SystemClock.uptimeMillis()
        pump()
    }

    /** A real touch swipe through the input pipeline, so the page handles it exactly like a finger. */
    private fun swipe(dir: Int) {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val x = w / 2f
        val fromY = if (dir > 0) h * 0.8f else h * 0.2f
        val toY = if (dir > 0) h * 0.2f else h * 0.8f
        val downTime = SystemClock.uptimeMillis()
        touch(downTime, MotionEvent.ACTION_DOWN, x, fromY)
        val steps = 12
        for (i in 1..steps) {
            handler.postDelayed({
                touch(downTime, MotionEvent.ACTION_MOVE, x, fromY + (toY - fromY) * i / steps)
            }, i * 16L)
        }
        // Hold briefly before lifting so the page snaps one item instead of flinging past several.
        handler.postDelayed({ touch(downTime, MotionEvent.ACTION_MOVE, x, toY) }, steps * 16L + 60)
        handler.postDelayed({ touch(downTime, MotionEvent.ACTION_UP, x, toY) }, steps * 16L + 110)
    }

    fun tap(control: PageControl) {
        if (control.cssWidth <= 0f || view.width <= 0) return
        val scale = view.width / control.cssWidth
        val x = control.x * scale
        val y = control.y * scale
        val downTime = SystemClock.uptimeMillis()
        touch(downTime, MotionEvent.ACTION_DOWN, x, y)
        handler.postDelayed({ touch(downTime, MotionEvent.ACTION_UP, x, y) }, 60)
    }

    private fun touch(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    // ---------- queries ----------

    fun findControl(kind: String, result: (PageControl?) -> Unit) {
        view.evaluateJavascript("window.__sh ? __sh.control('$kind') : null") { json ->
            val control = runCatching {
                val o = JSONObject(json)
                PageControl(
                    o.getDouble("x").toFloat(),
                    o.getDouble("y").toFloat(),
                    o.getDouble("width").toFloat(),
                    if (o.isNull("liked")) null else o.getBoolean("liked"),
                )
            }.getOrNull()
            result(control)
        }
    }

    fun videoUrl(result: (String) -> Unit) {
        view.evaluateJavascript("window.__sh ? __sh.url() : location.href") { json ->
            result(runCatching { JSONObject("{\"u\":$json}").getString("u") }.getOrNull() ?: platform.feedUrl)
        }
    }

    private fun js(code: String) = view.evaluateJavascript("window.__sh && $code", null)

    // ---------- page events ----------

    private fun onMessage(json: String) {
        val msg = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (msg.optString("t")) {
            "hello" -> {
                // A fresh copy of the script (new page load) starts inactive; restore our state.
                js("__sh.setActive($active, false, $sound)")
                if (loads++ > 0) listener.onFeedReloaded(this)
            }
            "ended" -> listener.onVideoEnded(this)
            "state" -> {
                state = FeedState(
                    id = msg.optStringOrNull("id"),
                    skipKey = msg.optStringOrNull("skipKey"),
                    hasVideo = msg.optBoolean("video"),
                    progressed = msg.optBoolean("progressed"),
                    skip = msg.optStringOrNull("skip"),
                    liked = if (msg.isNull("liked")) null else msg.optBoolean("liked"),
                    panelOpen = msg.optBoolean("panel"),
                )
                if (state.progressed) everPlayed = true
                if (state.hasVideo) ready = true
                listener.onFeedState(this, state)
            }
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(json: String) {
            handler.post { onMessage(json) }
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Stay on the web: drop "open in app" redirects (intent://, snssdk://, instagram://).
            val scheme = request.url.scheme
            return scheme != "http" && scheme != "https"
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            if (AdBlock.isBlocked(request.url)) AdBlock.emptyResponse() else null

        override fun onPageFinished(view: WebView, url: String) {
            // Covers WebViews without document-start scripts; the script ignores a second copy.
            view.evaluateJavascript(pageScript, null)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            listener.onFeedCrashed(this@PlatformFeed)
            return true
        }
    }

    companion object {
        /** The WebView's agent minus its "wv" markers, so sites serve the same page as Chrome. */
        fun browserUserAgent(webViewAgent: String, desktop: Boolean): String {
            if (desktop) {
                val chrome = Regex("Chrome/[\\d.]+").find(webViewAgent)?.value ?: "Chrome/140.0.0.0"
                return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
            }
            return webViewAgent.replace("; wv", "").replace(Regex("Version/[\\d.]+ "), "")
        }
    }
}

private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
