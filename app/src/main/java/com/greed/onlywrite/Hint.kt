package com.greed.onlywrite

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * 轻提示的统一出口。
 *
 * 标准版：系统 Toast。
 * Delta 版：不用系统 Toast，改成底栏样式的一条提示，贴在页面底部，
 *          和 HTML 原型里那条状态栏长得一样 —— 暗底、1px 描边、零圆角、字距拉开。
 */
object Hint {

    private var showing: TextView? = null

    fun show(a: Activity, msg: String) {
        if (!BuildConfig.DELTA) {
            Toast.makeText(a, msg, Toast.LENGTH_SHORT).show()
            return
        }
        a.runOnUiThread { bar(a, msg) }
    }

    private fun bar(a: Activity, msg: String) {
        val root = a.window?.decorView as? ViewGroup ?: return
        showing?.let { old -> (old.parent as? ViewGroup)?.removeView(old) }

        val pad = dp(a, 14)
        val tv = TextView(a).apply {
            text = msg
            setTextColor(0xFF9A968D.toInt())
            textSize = 11.5f
            letterSpacing = 0.12f
            setPadding(pad, dp(a, 10), pad, dp(a, 10))
            maxLines = 3
            background = GradientDrawable().apply {
                setColor(0xF0131518.toInt())
                setStroke(dp(a, 1), 0xFF2A2E35.toInt())
                cornerRadius = 0f
            }
            val f = File(a.filesDir, "font.ttf")
            if (f.exists()) runCatching { typeface = Typeface.createFromFile(f) }
        }

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(a, 10)
            rightMargin = dp(a, 10)
            bottomMargin = dp(a, 58)
        }

        root.addView(tv, lp)
        showing = tv
        tv.alpha = 0f
        tv.animate().alpha(1f).setDuration(110).start()
        tv.postDelayed({
            tv.animate().alpha(0f).setDuration(220).withEndAction {
                (tv.parent as? ViewGroup)?.removeView(tv)
                if (showing === tv) showing = null
            }.start()
        }, 1900)
    }

    private fun dp(a: Activity, v: Int) = (v * a.resources.displayMetrics.density).toInt()
}
