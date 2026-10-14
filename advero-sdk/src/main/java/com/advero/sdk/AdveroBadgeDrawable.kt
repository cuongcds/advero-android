package com.advero.sdk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The "Ads by Advero" disclosure: the Advero icon on a dark tile, drawn in the
 * top-right corner of the creative at half opacity (mirrors the Web SDK badge).
 */
internal class AdveroBadgeDrawable(
    private val icon: Bitmap,
    private val density: Float
) : Drawable() {

    val sizePx = Math.round((ICON_DP + PADDING_DP * 2) * density)

    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = 0x59FFFFFF
    }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val iconRect = RectF()
    private var opacity = IDLE_ALPHA

    init {
        applyOpacity()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = CORNER_DP * density
        path.reset()
        // Only the bottom-left corner is rounded (order: top-left, top-right, bottom-right, bottom-left).
        path.addRoundRect(RectF(b), floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, r, r), Path.Direction.CW)
        canvas.drawPath(path, background)
        canvas.drawPath(path, stroke)
        val pad = PADDING_DP * density
        iconRect.set(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad)
        canvas.drawBitmap(icon, null, iconRect, iconPaint)
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha
        applyOpacity()
    }

    private fun applyOpacity() {
        background.alpha = opacity
        stroke.alpha = opacity * 0x59 / 255
        iconPaint.alpha = opacity
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val ICON_DP = 12
        const val PADDING_DP = 3
        const val CORNER_DP = 3
        const val IDLE_ALPHA = 128
    }
}
