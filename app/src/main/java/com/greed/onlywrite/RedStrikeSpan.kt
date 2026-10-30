package com.greed.onlywrite

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import kotlin.math.ceil

/**
 * 划掉的文字：字保持暗灰，横线单独上色。
 * Android 的 StrikethroughSpan 用的是文字颜色，没法分开，所以自己画。
 */
class RedStrikeSpan(
    private val textColor: Int,
    private val lineColor: Int,
    private val lineWidthPx: Float
) : ReplacementSpan() {

    private var measured = 0f

    override fun getSize(
        paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        measured = paint.measureText(text, start, end)
        return ceil(measured.toDouble()).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val oldColor = paint.color
        val oldStrike = paint.isStrikeThruText
        paint.isStrikeThruText = false
        paint.color = textColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
        paint.color = oldColor
        paint.isStrikeThruText = oldStrike

        val p = Paint(paint)
        p.color = lineColor
        p.strokeWidth = lineWidthPx
        p.style = Paint.Style.FILL
        val midY = y - (paint.ascent() + paint.descent()) / 2f
        if (measured > 0f) canvas.drawRect(x, midY - lineWidthPx / 2f, x + measured, midY + lineWidthPx / 2f, p)
    }
}
