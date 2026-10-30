package com.greed.onlywrite

import android.graphics.Paint
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.graphics.Typeface

/** 让时间行也用用户导入的字体，而不是被写死成 monospace */
class StampFontSpan(private val tf: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(ds: TextPaint) { ds.typeface = tf }
    override fun updateMeasureState(p: TextPaint) { p.typeface = tf }
}
