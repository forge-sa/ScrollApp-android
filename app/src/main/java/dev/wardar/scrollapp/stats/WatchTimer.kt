package dev.wardar.scrollapp.stats

import android.os.SystemClock
import dev.wardar.scrollapp.Platform

/** Accumulates on-screen time for the platform currently playing. */
class WatchTimer(private val stats: StatsRepository) {
    private var platform: Platform? = null
    private var since = 0L

    fun start(platform: Platform) {
        if (this.platform == platform) return
        stop()
        this.platform = platform
        since = SystemClock.elapsedRealtime()
    }

    fun stop() {
        checkpoint()
        platform = null
    }

    /** Saves the time so far without stopping, so a crash or kill loses at most one interval. */
    fun checkpoint() {
        val platform = platform ?: return
        val now = SystemClock.elapsedRealtime()
        if (now > since) stats.addWatchTime(platform, now - since)
        since = now
    }
}
