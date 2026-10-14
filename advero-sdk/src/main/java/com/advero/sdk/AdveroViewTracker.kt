package com.advero.sdk

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View

/**
 * Reports a viewable impression: once [view] has been at least [needRatio] in
 * view for [needMs] without interruption (while its window has focus),
 * [onViewable] fires exactly once with the continuous time and the smallest
 * visible percentage seen during it. The server re-checks every number.
 */
internal class AdveroViewTracker(
    private val view: View,
    private val needRatio: Double,
    private val needMs: Long,
    private val onViewable: (ms: Long, ratio: Int) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val rect = Rect()
    private var started = 0L
    private var intervalMin = 1.0
    private var sent = false
    private var stopped = false

    private val tick = object : Runnable {
        override fun run() {
            if (sent || stopped) return
            evaluate(visibleRatio())
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (!sent && !stopped) {
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            handler.removeCallbacks(tick)
            reset()
        }
    }

    fun start() {
        view.addOnAttachStateChangeListener(attachListener)
        if (view.isAttachedToWindow) {
            handler.post(tick)
        }
    }

    fun stop() {
        stopped = true
        handler.removeCallbacks(tick)
        view.removeOnAttachStateChangeListener(attachListener)
    }

    private fun visibleRatio(): Double {
        val area = view.width.toLong() * view.height
        if (area <= 0 || !view.isShown || !view.hasWindowFocus()) return 0.0
        if (!view.getGlobalVisibleRect(rect)) return 0.0
        return rect.width().toLong() * rect.height() / area.toDouble()
    }

    private fun reset() {
        started = 0L
        intervalMin = 1.0
    }

    private fun evaluate(ratio: Double) {
        if (ratio < needRatio) {
            reset()
            return
        }
        val now = SystemClock.elapsedRealtime()
        intervalMin = minOf(intervalMin, ratio)
        if (started == 0L) started = now
        if (now - started >= needMs) {
            sent = true
            stop()
            onViewable(now - started, Math.floor(intervalMin * 100).toInt())
        }
    }

    private companion object {
        const val POLL_MS = 100L
    }
}
