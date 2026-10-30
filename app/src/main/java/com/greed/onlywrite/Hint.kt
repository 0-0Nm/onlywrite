package com.greed.onlywrite

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * 提示的统一出口。
 *
 * 标准版：系统 Toast（浮在屏幕中下方，自动消失）。
 * Delta 版：模仿 HTML 原型的 #status —— **布局内常驻的一块文本区**，
 *          贴在底栏上方，随内容撑高（最高约 120dp 后自身可滚），
 *          不浮起、不遮挡正文，一直留着上一条消息，直到下一条覆盖。
 */
object Hint {

    private fun tv(a: Activity): TextView? = a.findViewById(R.id.statusLine)
    private fun wrap(a: Activity): ScrollView? = a.findViewById(R.id.statusWrap)

    fun show(a: Activity, msg: String) {
        if (!BuildConfig.DELTA) {
            Toast.makeText(a, msg, Toast.LENGTH_SHORT).show()
            return
        }
        a.runOnUiThread { bar(a, msg) }
    }

    /** Delta 版启动时先落一句，和 HTML 的初始文案对齐 */
    fun idle(a: Activity, msg: String) = show(a, msg)

    private fun bar(a: Activity, msg: String) {
        val t = tv(a) ?: run {
            Toast.makeText(a, msg, Toast.LENGTH_SHORT).show()
            return
        }
        val w = wrap(a) ?: return
        a.findViewById<android.view.View>(R.id.statusDivider)?.visibility = android.view.View.VISIBLE

        val f = File(a.filesDir, "font.ttf")
        if (f.exists()) runCatching { t.typeface = Typeface.createFromFile(f) }

        w.visibility = View.VISIBLE
        t.text = msg
        t.setTextColor(0xFF6B7078.toInt())
        w.post { w.fullScroll(View.FOCUS_DOWN) }
    }
}
