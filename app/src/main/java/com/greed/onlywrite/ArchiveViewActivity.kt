package com.greed.onlywrite

import android.app.Dialog
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
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchiveViewActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var body: TextView
    private lateinit var title: TextView
    private lateinit var count: TextView
    private var name: String = ""
    private var plain: String = ""
    private var strikes: String = ""
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
        strikes = a.strikes

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
        if (v is ViewGroup) for (i in 0 until v.childCount) deep(v.getChildAt(i), tf)
    }

    private fun render() {
        val sp = SpannableString(plain)

        val re = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} UTC[+-]\\d{2}:\\d{2}")
        for (m in re.findAll(plain)) {
            val a = m.range.first
            val b = m.range.last + 1
            if (b <= sp.length) {
                sp.setSpan(RelativeSizeSpan(0.62f), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(ForegroundColorSpan(0xFF5F5C52.toInt()), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        for (r in parseStrikes(strikes)) {
            val a2 = r.first.coerceIn(0, sp.length)
            val b2 = r.second.coerceIn(0, sp.length)
            if (b2 > a2) sp.setSpan(
                RedStrikeSpan(0xFF6B7078.toInt(), 0xFFB0555A.toInt(), dpF(1.4f)),
                a2, b2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        for (n in store.notesOf(name)) {
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

    /** 悬停卡片：贴在注记文字所在行的下方，允许遮住后面的正文 */
    private fun showNotes(a: Int, b: Int) {
        val list = store.notesOf(name).filter { it.s < b && it.e > a }
        if (list.isEmpty()) return

        val card = buildCard(list)

        popup?.dismiss()
        val pw = PopupWindow(
            card,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(12).toFloat()
        }
        popup = pw

        val layout = body.layout
        val loc = IntArray(2)
        body.getLocationOnScreen(loc)
        val lineY = if (layout != null) {
            val line = layout.getLineForOffset(a.coerceAtMost(layout.text.length))
            body.paddingTop + layout.getLineBottom(line) - body.scrollY
        } else body.height / 3

        val metrics = resources.displayMetrics
        var y = loc[1] + lineY + dp(4)
        val cardH = card.measuredHeight.takeIf { it > 0 } ?: dp(120)
        if (y + cardH > metrics.heightPixels - dp(24)) y = loc[1] + lineY - cardH - dp(8)
        y = y.coerceAtLeast(dp(8))

        val x = (loc[0] + dp(12)).coerceAtMost(metrics.widthPixels - dp(24))
        pw.showAtLocation(body, Gravity.TOP or Gravity.START, x, y)
    }

    private fun buildCard(list: List<Note>): LinearLayout {
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
                card.addView(View(this).apply { setBackgroundColor(0xFF1B1E23.toInt()) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                        .apply { topMargin = dp(12); bottomMargin = dp(12) })
            }
        }
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        return card
    }

    /** 注记输入：不用系统 AlertDialog，改成同款暗底卡片 */
    private fun addNote() {
        val a = body.selectionStart
        val b = body.selectionEnd
        if (a < 0 || b <= a) { toast("先选中要注记的文字。"); return }

        val dlg = Dialog(this)
        val pad = dp(18)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(16), pad, dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF141619.toInt())
                setStroke(dp(1), 0xFF2A2E35.toInt())
                cornerRadius = 0f
            }
        }

        val head = TextView(this).apply {
            text = "注记 · " + plain.substring(a, minOf(b, plain.length)).take(18)
            setTextColor(0xFF8A8271.toInt())
            textSize = 11f
            letterSpacing = 0.16f
            appTf?.let { typeface = it }
        }

        val box = EditText(this).apply {
            hint = "写注记…"
            setTextColor(0xFFE9E6E1.toInt())
            setHintTextColor(0xFF4A4F57.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 16f
            setPadding(0, dp(14), 0, dp(14))
            appTf?.let { typeface = it }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancel = TextView(this).apply {
            text = "算了"
            setTextColor(0xFF5F646C.toInt())
            textSize = 12f
            letterSpacing = 0.2f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            appTf?.let { typeface = it }
        }
        val ok = TextView(this).apply {
            text = "收下"
            setTextColor(0xFF8A8271.toInt())
            textSize = 12f
            letterSpacing = 0.2f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            appTf?.let { typeface = it }
        }
        bar.addView(cancel)
        bar.addView(ok)

        root.addView(head)
        root.addView(box)
        root.addView(bar)
        dlg.setContentView(root)

        cancel.setOnClickListener { dlg.dismiss() }
        ok.setOnClickListener {
            val txt = box.text.toString().trim()
            if (txt.isEmpty()) { toast("空注记不留。"); return@setOnClickListener }
            store.putNote(name, a, b, txt)
            render()
            dlg.dismiss()
            toast("已注记。点黄底处可查看。")
        }

        box.post { box.requestFocus() }
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.86f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dlg.show()
    }

    private fun parseStrikes(s: String): List<Pair<Int, Int>> {
        if (s.isEmpty()) return emptyList()
        return s.split(";").mapNotNull {
            val p = it.split(":")
            if (p.size == 2) p[0].toIntOrNull()?.let { x -> p[1].toIntOrNull()?.let { y -> x to y } } else null
        }
    }

    private fun dpF(v: Float) = v * resources.displayMetrics.density

    private fun fmt(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(ts))

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
