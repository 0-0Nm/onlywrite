package com.greed.onlywrite

import android.graphics.Paint
import android.text.style.LineHeightSpan

/** 把时间行压矮：正文行高的 1/2 ~ 2/3 */
class StampLineSpan(private val targetPx: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt
    ) {
        if (targetPx <= 0) return
        fm.ascent = -(targetPx * 0.76f).toInt()
        fm.descent = (targetPx * 0.24f).toInt()
        fm.top = fm.ascent
        fm.bottom = fm.descent
    }
}
