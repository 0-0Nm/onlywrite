package com.greed.onlywrite

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * 把时间行"折叠"成零宽度。
 * 关键：字符仍在缓冲区里 —— 坐标不变，划线不错位，链也没被动过。
 */
class HiddenStampSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        fm?.let {
            it.ascent = 0; it.descent = 0; it.top = 0; it.bottom = 0
        }
        return 0
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        // 不画。零宽、零高。
    }
}
