package com.greed.onlywrite

import android.graphics.Paint
import android.text.style.LineHeightSpan

/** 把一行的高度压成 0 —— 配合 HiddenStampSpan 让隐藏的时间行彻底不占位 */
class ZeroHeightSpan : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt
    ) {
        fm.ascent = 0
        fm.descent = 0
        fm.top = 0
        fm.bottom = 0
        fm.leading = 0
    }
}
