package com.greed.onlywrite

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * 已归档卷的只读查看界面。
 * 与刻录同一套视觉，但：
 *  · 底栏没有拾音，只有注记
 *  · 正文不可编辑、不可划掉
 *  · 可选中文字添加注记
 */
class ArchiveViewActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var body: TextView
    private lateinit var title: TextView
    private lateinit var count: TextView
    private var name: String = ""
    private var plain: String = ""
    private var appTf: Typeface? = null

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
        // 注记：淡黄底 + 下划线
        for ((s, e, _) in store.notesOf(name)) {
            val a = s.coerceIn(0, sp.length)
            val b = e.coerceIn(0, sp.length)
            if (b > a) {
                sp.setSpan(BackgroundColorSpan(0x332F2A1E), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(UnderlineSpan(), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        body.setText(sp)
    }

    private fun addNote() {
        val a = body.selectionStart
        val b = body.selectionEnd
        if (a < 0 || b <= a) { toast("先选中要注记的文字。"); return }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = EditText(this).apply {
            hint = "写注记…"
            setTextColor(0xFFE9E6E1)
            setHintTextColor(0xFF4A4F57)
            setBackgroundColor(0x00000000)
            setPadding(pad, pad, pad, pad)
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
                toast("已注记。")
            }
            .setNegativeButton("算了", null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
