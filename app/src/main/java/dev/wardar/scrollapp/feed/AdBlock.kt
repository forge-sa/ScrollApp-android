package dev.wardar.scrollapp.feed

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Request-level blocking for ad networks. Instagram and TikTok serve their ads inside the
 * normal feed responses, so those are caught on the page instead (see Platform.adMarkers).
 */
object AdBlock {
    private val hosts = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "imasdk.googleapis.com",
        "googletagservices.com",
        "analytics.tiktok.com",
        "an.facebook.com",
    )
    private val youtubePaths = listOf("/pagead/", "/api/stats/ads", "/get_midroll_", "/ptracking")

    fun isBlocked(url: Uri): Boolean {
        val host = url.host ?: return false
        if (hosts.any { host == it || host.endsWith(".$it") }) return true
        if (host == "youtube.com" || host.endsWith(".youtube.com")) {
            val path = url.path.orEmpty()
            return youtubePaths.any { path.startsWith(it) }
        }
        return false
    }

    fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
