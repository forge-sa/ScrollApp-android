package dev.wardar.scrollapp.feed

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Holds the platform WebViews and takes vertical swipes away from them, so one swipe moves
 * the unified feed instead of a single site's feed. Taps, double-taps and sideways swipes
 * still reach the page.
 */
class SwipeLayout(context: Context) : FrameLayout(context) {
    interface Listener {
        /** Finger moved [dy] px since touch-down (negative = up, towards the next video). */
        fun onDrag(dy: Float)

        /** 1 = next, -1 = previous, 0 = not far or fast enough. */
        fun onSwipe(direction: Int)
    }

    var listener: Listener? = null

    /** Off while the page shows a scrollable panel (comments, share sheet). */
    var interceptEnabled = true

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 6f
    private var downX = 0f
    private var downY = 0f
    private var velocity: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!interceptEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().apply { addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dy) > slop * 2 && abs(dy) > abs(dx) * 1.5f) return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.recycle()
                velocity = null
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val tracker = velocity ?: return ev.actionMasked == MotionEvent.ACTION_DOWN
        tracker.addMovement(ev)
        val dy = ev.y - downY
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> listener?.onDrag(dy)
            MotionEvent.ACTION_UP -> {
                tracker.computeCurrentVelocity(1000)
                val vy = tracker.yVelocity
                val far = height * 0.2f
                val direction = when {
                    dy < -far || (vy < -flingVelocity && dy < 0) -> 1
                    dy > far || (vy > flingVelocity && dy > 0) -> -1
                    else -> 0
                }
                release()
                listener?.onSwipe(direction)
            }
            MotionEvent.ACTION_CANCEL -> {
                release()
                listener?.onSwipe(0)
            }
        }
        return true
    }

    private fun release() {
        velocity?.recycle()
        velocity = null
    }
}
