package com.greed.onlywrite

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 已归档卷的只读查看界面。
 * 注记不可增删改历史，但可以对同一段文字反复注记；
 * 点注记处弹出悬浮卡片，列出这一段上全部注记。
 */
class ArchiveViewActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var body: TextView
    private lateinit var title: TextView
    private lateinit var count: TextView
    private var name: String = ""
    private var plain: String = ""
    private var appTf: Typeface? = null
    private var popup: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive)

        store = Store(this)
        body = findViewById(R.id.aBody)
        title = findViewById(R.id.aTitle)
        count = findViewById(R.id.aCount)

        name = intent.getStringExtra("name") ?: ""
        val a = store.allArchives().firstOrNull { it.name == name }
        if (a == null) { finish(); return }
        plain = a.plain

        title.text = name
        count.text = plain.replace(Regex("\\s"), "").length.toString() + " 字"

        loadFont()
        render()

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnNote).setOnClickListener { addNote() }
    }

    private fun loadFont() {
        val f = File(filesDir, "font.ttf")
        if (!f.exists()) return
        runCatching {
            val tf = Typeface.createFromFile(f)
            appTf = tf
            deep(findViewById(android.R.id.content), tf)
        }
    }

    private fun deep(v: View, tf: Typeface) {
        if (v is TextView) v.typeface = tf
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) deep(v.getChildAt(i), tf)
    }

    private fun render() {
        val sp = SpannableString(plain)

        // 时间行：小一号、暗灰
        val re = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} UTC[+-]\\d{2}:\\d{2}")
        for (m in re.findAll(plain)) {
            val a = m.range.first
            val b = m.range.last + 1
            if (b <= sp.length) {
                sp.setSpan(RelativeSizeSpan(0.62f), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(ForegroundColorSpan(0xFF5F5C52.toInt()), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // 注记：极淡底色 + 细下划线 + 可点
        val notes = store.notesOf(name)
        for (n in notes) {
            val a = n.s.coerceIn(0, sp.length)
            val b = n.e.coerceIn(0, sp.length)
            if (b > a) {
                sp.setSpan(BackgroundColorSpan(0x2E2F2A1E), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(UnderlineSpan(), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(object : ClickableSpan() {
                    override fun onClick(w: View) { showNotes(a, b) }
                }, a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        body.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        body.setText(sp, TextView.BufferType.SPANNABLE)
    }

    /** 悬浮卡片：列出这一段上的全部注记 */
    private fun showNotes(a: Int, b: Int) {
        val list = store.notesOf(name).filter { it.s < b && it.e > a }
        if (list.isEmpty()) return

        val pad = dp(16)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(14), pad, dp(14))
            background = GradientDrawable().apply {
                setColor(0xFF141619.toInt())
                setStroke(dp(1), 0xFF2A2E35.toInt())
                cornerRadius = 0f
            }
        }

        for ((i, n) in list.withIndex()) {
            val t1 = TextView(this).apply {
                text = n.body
                setTextColor(0xFFE9E6E1.toInt())
                textSize = 15f
                setLineSpacing(0f, 1.6f)
                appTf?.let { typeface = it }
            }
            val t2 = TextView(this).apply {
                text = fmt(n.ts)
                setTextColor(0xFF5F5C52.toInt())
                textSize = 10f
                setPadding(0, dp(4), 0, 0)
                appTf?.let { typeface = it }
            }
            card.addView(t1)
            card.addView(t2)
            if (i != list.size - 1) {
                val d = View(this).apply {
                    setBackgroundColor(0xFF1B1E23.toInt())
                }
                card.addView(d, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(12); bottomMargin = dp(12) })
            }
        }

        popup?.dismiss()
        popup = PopupWindow(
            card,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }
        popup?.showAtLocation(body, Gravity.CENTER, 0, 0)
    }

    private fun fmt(ts: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        return f.format(Date(ts))
    }

    private fun addNote() {
        val a = body.selectionStart
        val b = body.selectionEnd
        if (a < 0 || b <= a) { toast("先选中要注记的文字。"); return }

        val pad = dp(16)
        val box = EditText(this).apply {
            hint = "写注记…"
            setTextColor(0xFFE9E6E1.toInt())
            setHintTextColor(0xFF4A4F57.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(pad, pad, pad, pad)
            appTf?.let { typeface = it }
        }
        val wrap = FrameLayout(this).apply { addView(box) }

        AlertDialog.Builder(this)
            .setTitle("注记 · " + plain.substring(a, minOf(b, plain.length)).take(18))
            .setView(wrap)
            .setPositiveButton("收下") { _, _ ->
                val txt = box.text.toString().trim()
                if (txt.isEmpty()) { toast("空注记不留。"); return@setPositiveButton }
                store.putNote(name, a, b, txt)
                render()
                toast("已注记。点黄底处可查看。")
            }
            .setNegativeButton("算了", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
