package com.advero.sdk

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * The "Ads by Advero" disclosure (mirrors the Web SDK badge): the Advero icon
 * on a dark tile in the top-right corner of the creative. [setExpanded] slides
 * the "Ads by Advero" label out to the left of the icon and raises the opacity.
 *
 * Bounds always cover the fully expanded badge (anchored at the right edge);
 * only the current [visibleWidthPx] of it is drawn.
 */
internal class AdveroBadgeDrawable(
    private val icon: Bitmap,
    private val density: Float
) : Drawable() {

    /** Collapsed (icon only) size. */
    val sizePx = Math.round((ICON_DP + PADDING_DP * 2) * density)

    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = 0x59FFFFFF
    }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = LABEL_DP * density
        typeface = Typeface.SANS_SERIF
    }
    private val path = Path()
    private val iconRect = RectF()

    private val labelWidthPx = labelPaint.measureText(LABEL)
    private val labelExtraPx = (labelWidthPx + (LABEL_PAD_START_DP + LABEL_PAD_END_DP) * density)

    /** Width of the fully expanded badge. */
    val expandedWidthPx = sizePx + Math.round(labelExtraPx)

    private var progress = 0f
    private var animator: ValueAnimator? = null

    var expanded = false
        private set

    /** Width currently drawn (between [sizePx] and [expandedWidthPx]). */
    val visibleWidthPx get() = sizePx + Math.round(labelExtraPx * progress)

    /** Currently drawn area, in the coordinates of the drawable's bounds. */
    fun visibleRect(): Rect {
        val b = bounds
        return Rect(b.right - visibleWidthPx, b.top, b.right, b.bottom)
    }

    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, if (value) 1f else 0f).apply {
            duration = ANIMATION_MS
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    /** Stops the running animation; call when the badge is removed from its view. */
    fun release() {
        animator?.cancel()
        animator = null
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val left = b.right - visibleWidthPx
        val r = CORNER_DP * density
        val alpha = IDLE_ALPHA + Math.round((255 - IDLE_ALPHA) * progress)
        background.alpha = alpha
        stroke.alpha = alpha * 0x59 / 255
        iconPaint.alpha = alpha
        labelPaint.alpha = alpha

        path.reset()
        // Only the bottom-left corner is rounded (order: top-left, top-right, bottom-right, bottom-left).
        path.addRoundRect(
            RectF(left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()),
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, r, r),
            Path.Direction.CW
        )
        canvas.drawPath(path, background)
        canvas.drawPath(path, stroke)

        val pad = PADDING_DP * density
        iconRect.set(b.right - sizePx + pad, b.top + pad, b.right - pad, b.bottom - pad)
        canvas.drawBitmap(icon, null, iconRect, iconPaint)

        if (progress > 0f) {
            val textRight = b.right - sizePx + pad - LABEL_PAD_END_DP * density
            val textLeft = textRight - labelWidthPx
            val metrics = labelPaint.fontMetrics
            val baseline = b.top + (sizePx - (metrics.ascent + metrics.descent)) / 2f
            canvas.save()
            canvas.clipRect(left.toFloat() + 1f, b.top.toFloat(), textRight + 1f, b.bottom.toFloat())
            canvas.drawText(LABEL, textLeft, baseline, labelPaint)
            canvas.restore()
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val LABEL = "Ads by Advero"
        const val ICON_DP = 12
        const val PADDING_DP = 3
        const val CORNER_DP = 3
        const val LABEL_DP = 10
        const val LABEL_PAD_START_DP = 3
        const val LABEL_PAD_END_DP = 4
        const val IDLE_ALPHA = 128
        const val ANIMATION_MS = 200L
    }
}
