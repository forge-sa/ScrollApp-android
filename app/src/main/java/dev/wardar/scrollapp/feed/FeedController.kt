package dev.wardar.scrollapp.feed

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.wardar.scrollapp.Platform
import dev.wardar.scrollapp.settings.MixMode
import dev.wardar.scrollapp.settings.SettingsStore
import dev.wardar.scrollapp.stats.StatsRepository
import dev.wardar.scrollapp.stats.WatchTimer

/**
 * The unified feed. Each platform keeps its own WebView, stacked on top of each other; a swipe
 * slides the current one away to reveal the next platform's, which was already lined up on its
 * next unseen item. The feed is a history of (platform, item index) entries, so swiping back
 * returns to exactly what was shown.
 */
class FeedController(
    private val activity: Activity,
    private val settings: SettingsStore,
    private val stats: StatsRepository,
) : PlatformFeed.Listener, SwipeLayout.Listener {

    private class Entry(val platform: Platform, var index: Int) {
        var counted = false
        var sawVideo = false
    }

    val root = SwipeLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        listener = this@FeedController
    }

    private val script = activity.assets.open("scrollapp.js").bufferedReader().use { it.readText() }
    private val feeds = LinkedHashMap<Platform, PlatformFeed>()
    private val history = ArrayList<Entry>()
    private var position = -1
    private val handler = Handler(Looper.getMainLooper())
    private val timer = WatchTimer(stats)

    var current by mutableStateOf<Platform?>(null)
        private set
    var liked by mutableStateOf<Boolean?>(null)
        private set
    var autoscroll by mutableStateOf(settings.autoscroll)
        private set
    var sound by mutableStateOf(settings.sound)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    /** Bumped on each like, to play the heart animation. */
    var likeBurst by mutableIntStateOf(0)
        private set

    private var foreground = false
    private var covered = false
    private var animating = false
    private var peek: PlatformFeed? = null
    private var plannedNext: Platform? = null

    private val playing get() = foreground && !covered
    private val currentEntry get() = history.getOrNull(position)
    private val currentFeed get() = currentEntry?.let { feeds[it.platform] }

    // ---------- lifecycle ----------

    fun setForeground(on: Boolean) {
        if (foreground == on) return
        foreground = on
        // pause/resumeTimers act on every WebView in the process, so any one of them will do.
        if (on) {
            feeds.values.firstOrNull()?.view?.resumeTimers()
            feeds.values.forEach { it.view.onResume() }
        }
        refreshPlayback()
        if (!on) {
            feeds.values.forEach { it.view.onPause() }
            feeds.values.firstOrNull()?.view?.pauseTimers()
        }
    }

    /** Another screen (stats, settings, login) is on top of the feed. */
    fun setCovered(on: Boolean) {
        if (covered == on) return
        covered = on
        refreshPlayback()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        timer.stop()
        feeds.values.forEach { it.destroy() }
        feeds.clear()
    }

    private fun refreshPlayback() {
        val entry = currentEntry
        val feed = currentFeed
        if (playing && entry != null && feed != null) {
            feed.setActive(true, restart = false, sound = sound)
            timer.start(entry.platform)
            scheduleEntryChecks()
        } else {
            feed?.setActive(false, restart = false, sound = sound)
            timer.stop()
            handler.removeCallbacks(countVideo)
            handler.removeCallbacks(checkNoVideo)
        }
    }

    // ---------- sources ----------

    /** Brings the set of WebViews in line with the enabled platforms and restarts the feed if it changed. */
    fun syncFeeds() {
        val wanted = Platform.entries.filter { settings.isEnabled(it) }
        if (wanted == feeds.keys.toList()) return
        for (platform in feeds.keys.toList()) {
            if (platform !in wanted) feeds.remove(platform)?.destroy()
        }
        val kept = feeds.toMap()
        feeds.clear()
        for (platform in wanted) feeds[platform] = kept[platform] ?: createFeed(platform)
        restartFeed()
    }

    fun setPlatformEnabled(platform: Platform, on: Boolean) {
        settings.setEnabled(platform, on)
        syncFeeds()
    }

    fun setDesktopMode(platform: Platform, on: Boolean) {
        settings.setDesktop(platform, on)
        recreateFeed(platform)
    }

    /** Reloads a source from its start page, e.g. after logging in or after wandering off its feed. */
    fun reload(platform: Platform) {
        val feed = feeds[platform] ?: return
        forgetHistory(platform)
        feed.reload()
        if (currentEntry?.platform == platform) feed.nextFresh = 1
    }

    private fun createFeed(platform: Platform): PlatformFeed {
        val feed = PlatformFeed(activity, platform, script, settings.isDesktop(platform), this)
        feed.view.translationZ = Z_HIDDEN
        feed.setFront(false)
        root.addView(feed.view)
        return feed
    }

    private fun recreateFeed(platform: Platform) {
        val old = feeds[platform] ?: return
        val wasCurrent = currentFeed === old
        old.destroy()
        val feed = createFeed(platform)
        feeds[platform] = feed
        forgetHistory(platform)
        if (wasCurrent) {
            feed.nextFresh = 1
            stack(feed)
            feed.setActive(playing, restart = false, sound = sound)
        }
    }

    /** Drops this platform's past entries: after a reload its item indices mean something else. */
    private fun forgetHistory(platform: Platform) {
        val entry = currentEntry
        val kept = history.filter { it.platform != platform || it === entry }
        history.clear()
        history.addAll(kept)
        position = if (entry == null) -1 else history.indexOf(entry)
        if (entry?.platform == platform) entry.index = 0
        if (plannedNext == platform) plannedNext = null
    }

    private fun restartFeed() {
        leaveEntry()
        history.clear()
        position = -1
        plannedNext = null
        feeds.values.forEach {
            it.setActive(false, restart = false, sound = sound)
            it.nextFresh = it.position
        }
        if (feeds.isEmpty()) {
            current = null
            return
        }
        next()
    }

    // ---------- moving through the unified feed ----------

    fun next() {
        if (animating) return
        if (position + 1 >= history.size) {
            val platform = plannedNext ?: pickNext(currentEntry?.platform) ?: return
            val feed = feeds[platform] ?: return
            history += Entry(platform, feed.nextFresh++)
        }
        plannedNext = null
        moveTo(position + 1, forward = true)
    }

    fun previous() {
        if (animating) return
        if (position <= 0) {
            settleDrag()
            return
        }
        moveTo(position - 1, forward = false)
    }

    private fun pickNext(after: Platform?): Platform? {
        val order = feeds.keys.toList()
        val ready = order.filter { feeds[it]?.suspended == false }.ifEmpty { order }
        if (ready.isEmpty()) return null
        if (after == null || ready.size == 1) return ready.first()
        return when (settings.mixMode) {
            MixMode.SHUFFLE -> ready.filter { it != after }.random()
            MixMode.ROTATE -> {
                val start = order.indexOf(after)
                (1..order.size).map { order[(start + it) % order.size] }.first { it in ready }
            }
        }
    }

    private fun moveTo(target: Int, forward: Boolean) {
        val fromFeed = currentFeed
        val to = history[target]
        val toFeed = feeds[to.platform] ?: return
        leaveEntry()
        Log.d(TAG, "show #$target ${to.platform} item ${to.index}")
        position = target
        current = to.platform
        liked = null
        toFeed.seek(to.index)

        if (fromFeed === toFeed) {
            // Same source twice in a row: the page scrolls itself.
            settleDrag()
            enterEntry()
            return
        }
        fromFeed?.setActive(false, restart = false, sound = sound)
        animateSwap(fromFeed, toFeed, forward) {
            // Line the source we left up on its next item, so it is buffered when its turn comes back.
            if (forward && fromFeed != null) fromFeed.seek(nextIndexFor(fromFeed, after = target))
            toFeed.setActive(playing, restart = true, sound = sound)
            enterEntry()
        }
    }

    /** The index this feed shows next: its next entry in history, or its next unseen item. */
    private fun nextIndexFor(feed: PlatformFeed, after: Int): Int =
        history.drop(after + 1).firstOrNull { it.platform == feed.platform }?.index ?: feed.nextFresh

    private fun enterEntry() {
        val entry = currentEntry ?: return
        root.interceptEnabled = true
        if (playing) {
            timer.start(entry.platform)
            scheduleEntryChecks()
        }
    }

    private fun leaveEntry() {
        timer.stop()
        handler.removeCallbacks(countVideo)
        handler.removeCallbacks(checkNoVideo)
    }

    private fun scheduleEntryChecks() {
        handler.removeCallbacks(countVideo)
        handler.removeCallbacks(checkNoVideo)
        if (currentEntry?.counted == false) handler.postDelayed(countVideo, COUNT_AFTER_MS)
        entryPlayingSince = SystemClock.uptimeMillis()
        handler.postDelayed(checkNoVideo, CHECK_EVERY_MS)
    }

    /**
     * A video counts once it has been on screen for a moment (leaving time to skip ads first)
     * and has actually played; one frozen behind a login wall does not count.
     */
    private val countVideo: Runnable = Runnable {
        val entry = currentEntry ?: return@Runnable
        if (!playing || entry.counted) return@Runnable
        if (!entry.sawVideo) {
            handler.postDelayed(countVideo, CHECK_EVERY_MS)
            return@Runnable
        }
        entry.counted = true
        stats.addVideo(entry.platform)
    }

    private var entryPlayingSince = 0L

    /**
     * Photo posts never end and videos behind login walls never play: move on rather than stall.
     * A page still on its first load gets longer, unless another source is ready to show instead.
     */
    private val checkNoVideo: Runnable = Runnable {
        val entry = currentEntry ?: return@Runnable
        val feed = feeds[entry.platform] ?: return@Runnable
        if (!playing || entry.sawVideo) return@Runnable
        val coldStart = !feed.ready && feeds.values.none { it !== feed && it.ready }
        val limit = if (coldStart && !feed.everPlayed) FIRST_LOAD_MS else STALL_MS
        if (SystemClock.uptimeMillis() - entryPlayingSince < limit) {
            handler.postDelayed(checkNoVideo, CHECK_EVERY_MS)
            return@Runnable
        }
        Log.d(TAG, "${entry.platform} item ${entry.index} never played, moving on")
        if (++feed.emptyStreak >= 2 && feeds.size > 1) {
            feed.suspended = true
            flash("${entry.platform.title} isn't showing videos. Log in from Settings, then reload it.")
        }
        if (autoscroll || feed.suspended) next()
    }

    // ---------- page events ----------

    override fun onFeedState(feed: PlatformFeed, state: FeedState) {
        if (state.progressed) {
            feed.emptyStreak = 0
            feed.suspended = false
        }
        val entry = currentEntry
        val isCurrent = entry != null && feeds[entry.platform] === feed
        if (isCurrent) {
            liked = state.liked
            if (!animating) root.interceptEnabled = !state.panelOpen
            if (state.progressed) entry!!.sawVideo = true
        }
        if (state.skip == null && !feed.busy) feed.skipStreak = 0
        if (state.skip != null) skip(feed, state, if (isCurrent) entry else null)
    }

    private fun skip(feed: PlatformFeed, state: FeedState, entry: Entry?) {
        if (!settings.skipAds || feed.busy || state.skipKey == feed.lastSkipKey) return
        if (feed.skipStreak >= MAX_SKIP_STREAK) return
        feed.lastSkipKey = state.skipKey
        Log.d(TAG, "${feed.platform} skipping ${state.skip} at item ${feed.position}")
        when {
            entry != null && feed.position == entry.index -> {
                // Swap the item under the current entry for the one after it.
                history.forEach { if (it.platform == feed.platform && it.index >= entry.index && it !== entry) it.index++ }
                entry.index++
                feed.nextFresh = maxOf(feed.nextFresh, entry.index + 1)
                feed.seek(entry.index)
                if (!entry.counted) scheduleEntryChecks()
                flash("Skipped ${SKIP_LABELS[state.skip] ?: "an ad"}")
            }
            entry == null && feed.position == feed.nextFresh -> {
                // A lined-up source landed on an ad while waiting for its turn.
                feed.nextFresh++
                feed.seek(feed.nextFresh)
            }
            else -> return
        }
        stats.addAdSkipped(feed.platform)
        if (++feed.skipStreak == MAX_SKIP_STREAK) {
            flash("${feed.platform.title}: too many items look like ads, so skipping is paused there.")
        }
    }

    override fun onVideoEnded(feed: PlatformFeed) {
        if (!autoscroll || !playing || animating || currentFeed !== feed || feed.busy) return
        Log.d(TAG, "${feed.platform} video ended, autoscrolling")
        next()
    }

    override fun onFeedReloaded(feed: PlatformFeed) {
        if (feeds[feed.platform] !== feed) return
        forgetHistory(feed.platform)
        feed.resetPosition()
        feed.nextFresh = if (currentEntry?.platform == feed.platform) 1 else 0
    }

    override fun onFeedCrashed(feed: PlatformFeed) {
        if (feeds[feed.platform] === feed) recreateFeed(feed.platform)
    }

    // ---------- actions ----------

    fun like() {
        val feed = currentFeed ?: return
        feed.findControl("like") { control ->
            if (control == null) {
                flash("Couldn't find the like button on this one")
                return@findControl
            }
            feed.tap(control)
            val nowLiked = control.liked != true
            liked = nowLiked
            if (nowLiked) likeBurst++
        }
    }

    /** Uses the site's own repost when it has one on screen, otherwise shares the link. */
    fun repost() {
        val feed = currentFeed ?: return
        feed.findControl("repost") { control ->
            if (control != null) {
                feed.tap(control)
                flash("Reposted on ${feed.platform.shortName}")
            } else {
                feed.videoUrl { url ->
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    activity.startActivity(Intent.createChooser(send, "Repost or share"))
                }
            }
        }
    }

    fun toggleAutoscroll() {
        autoscroll = !autoscroll
        settings.autoscroll = autoscroll
        flash(if (autoscroll) "Autoscroll on: moves on when a video ends" else "Autoscroll off")
    }

    fun toggleSound() {
        sound = !sound
        settings.sound = sound
        feeds.values.forEach { it.setSound(sound) }
    }

    private val clearNotice = Runnable { notice = null }

    private fun flash(message: String) {
        notice = message
        handler.removeCallbacks(clearNotice)
        handler.postDelayed(clearNotice, 2800)
    }

    // ---------- drag & animation ----------

    override fun onDrag(dy: Float) {
        if (animating) return
        val cur = currentFeed ?: return
        val h = root.height.toFloat()
        if (dy < 0) {
            val nextFeed = upcomingFeed() ?: return
            if (nextFeed === cur) return
            showPeek(nextFeed, under = true)
            cur.view.translationY = dy
        } else if (position > 0) {
            val prevFeed = feeds[history[position - 1].platform] ?: return
            cur.view.translationY = 0f
            if (prevFeed === cur) return
            showPeek(prevFeed, under = false)
            prevFeed.view.translationY = -h + dy
        }
    }

    override fun onSwipe(direction: Int) {
        when (direction) {
            1 -> next()
            -1 -> previous()
            else -> settleDrag()
        }
    }

    /** The feed the next swipe will show, deciding (and remembering) the next platform if needed. */
    private fun upcomingFeed(): PlatformFeed? {
        history.getOrNull(position + 1)?.let { return feeds[it.platform] }
        val platform = plannedNext ?: pickNext(currentEntry?.platform)?.also { plannedNext = it }
        return platform?.let { feeds[it] }
    }

    private fun showPeek(feed: PlatformFeed, under: Boolean) {
        if (peek !== feed) peek?.let { it.view.translationZ = Z_HIDDEN; it.view.translationY = 0f }
        peek = feed
        feed.view.translationZ = if (under) Z_UNDER else Z_OVER
        if (under) feed.view.translationY = 0f
    }

    private fun settleDrag() {
        val cur = currentFeed ?: return
        val h = root.height.toFloat()
        cur.view.animate().translationY(0f).setDuration(SETTLE_MS).start()
        val p = peek ?: return
        peek = null
        if (p.view.translationZ == Z_OVER) {
            p.view.animate().translationY(-h).setDuration(SETTLE_MS).withEndAction { stack(cur) }.start()
        } else {
            handler.postDelayed({ if (!animating) stack(cur) }, SETTLE_MS)
        }
    }

    private fun animateSwap(from: PlatformFeed?, to: PlatformFeed, forward: Boolean, done: () -> Unit) {
        val h = root.height.toFloat()
        val dragged = peek === to
        peek = null
        if (from == null || h <= 0f) {
            stack(to)
            done()
            return
        }
        animating = true
        val finish = {
            stack(to)
            animating = false
            done()
        }
        if (forward) {
            to.view.translationY = 0f
            to.view.translationZ = Z_UNDER
            from.view.animate().translationY(-h).setDuration(SWAP_MS)
                .setInterpolator(DecelerateInterpolator()).withEndAction(finish).start()
        } else {
            if (!dragged) to.view.translationY = -h
            to.view.translationZ = Z_OVER
            to.view.animate().translationY(0f).setDuration(SWAP_MS)
                .setInterpolator(DecelerateInterpolator()).withEndAction(finish).start()
        }
    }

    /** Puts [top] in front and every other feed flat behind it. */
    private fun stack(top: PlatformFeed) {
        for (feed in feeds.values) {
            feed.view.animate().cancel()
            feed.view.translationY = 0f
            feed.view.translationZ = if (feed === top) Z_TOP else Z_HIDDEN
        }
        for (feed in feeds.values) if (feed !== top) feed.setFront(false)
        top.setFront(true)
    }

    // Last, so every property above (the Runnables included) is initialized before the feed starts.
    init {
        syncFeeds()
        handler.postDelayed(object : Runnable {
            override fun run() {
                timer.checkpoint()
                handler.postDelayed(this, CHECKPOINT_MS)
            }
        }, CHECKPOINT_MS)
    }

    private companion object {
        const val TAG = "ScrollApp"
        const val Z_HIDDEN = 0f
        const val Z_UNDER = 1f
        const val Z_TOP = 2f
        const val Z_OVER = 3f
        const val SWAP_MS = 240L
        const val SETTLE_MS = 180L
        const val COUNT_AFTER_MS = 2000L
        const val STALL_MS = 12_000L
        const val FIRST_LOAD_MS = 25_000L
        const val CHECK_EVERY_MS = 2_000L
        const val CHECKPOINT_MS = 15_000L
        const val MAX_SKIP_STREAK = 6
        val SKIP_LABELS = mapOf("ad" to "an ad", "suggested" to "a suggested post", "live" to "a LIVE stream")
    }
}
