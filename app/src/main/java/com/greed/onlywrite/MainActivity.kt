package com.greed.onlywrite

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.style.StrikethroughSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var editor: StoneEditText
    private lateinit var count: TextView
    private lateinit var btnStrike: TextView
    private lateinit var btnFont: TextView
    private lateinit var btnRec: TextView

    private val doc = "main"
    private val revs = ArrayList<Rev>()
    private var suppress = false
    private var stampChars = 0
    private var strikeAlpha = 0f

    private val fontDir by lazy { File(filesDir, "font.ttf") }

    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)!!.use { ins ->
                fontDir.outputStream().use { ins.copyTo(it) }
            }
            applyFont()
        }.onFailure { t(it.message ?: "字体导入失败") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editor = findViewById(R.id.editor)
        count = findViewById(R.id.count)
        btnStrike = findViewById(R.id.btnStrike)
        btnFont = findViewById(R.id.btnFont)
        btnRec = findViewById(R.id.btnRec)

        store = Store(this)

        editor.onRejected = { m -> t(m) }
        editor.onCaretMoved = { repositionStrike() }
        editor.onCarve = { ins -> if (!suppress) onTyped(ins) }

        btnStrike.setOnClickListener { doStrike() }
        btnFont.setOnClickListener { pickFont.launch(arrayOf("font/*", "*/*")) }
        btnRec.setOnClickListener { t("拾音：下一步接") }

        editor.setOnScrollChangeListener { _, _, _, _, _ -> repositionStrike() }

        applyFont()
        loadAll()
    }

    private fun applyFont() {
        if (!fontDir.exists()) return
        runCatching {
            val tf = Typeface.createFromFile(fontDir)
            editor.typeface = tf
            listOf<TextView>(
                findViewById(R.id.title), count, btnFont,
                findViewById(R.id.tglTime), btnStrike, btnRec
            ).forEach { it.typeface = tf }
            t("字体已载入")
        }.onFailure { t("字体加载失败：${it.message}") }
    }

    private fun loadAll() {
        revs.clear()
        revs.addAll(store.all(doc))
        val sb = StringBuilder()
        val strikes = ArrayList<IntRange>()
        for (r in revs) {
            when (r.op) {
                "carve" -> sb.insert(minOf(r.pos, sb.length), r.payload)
                "stamp" -> { sb.append('\n'); sb.append(r.payload); sb.append('\n'); stampChars += r.payload.length }
                "strike" -> strikes.add(r.start until r.end)
            }
        }
        val sp = android.text.SpannableString(sb.toString())
        for (rg in strikes) {
            if (rg.first >= 0 && rg.last < sp.length)
                sp.setSpan(StrikethroughSpan(), rg.first, rg.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        suppress = true
        editor.setText(sp)
        editor.setSelection(sp.length)
        suppress = false
        updateCount()
        editor.post { repositionStrike() }
    }

    private fun onTyped(ins: String) {
        val pos = (editor.selectionStart - ins.length).coerceAtLeast(0)
        addRev("carve", pos, ins, 0, 0)
        if (ins.contains('\n')) {
            suppress = true
            val s = Clock.stamp()
            editor.text!!.append(s + "\n\u3000\u3000")
            addRev("stamp", 0, s, 0, 0)
            editor.setSelection(editor.text!!.length)
            stampChars += s.length
            suppress = false
        }
        updateCount()
        editor.post { repositionStrike() }
    }

    private fun addRev(op: String, pos: Int, payload: String, s: Int, e: Int) {
        val seq = revs.size + 1
        val parent = if (revs.isEmpty()) "0".repeat(64) else revs.last().hash
        val ts = System.currentTimeMillis()
        val hash = Hash.sha("$doc|$seq|$parent|$op|${Hash.sha(payload)}|$pos|$s|$e|$ts")
        val r = Rev(seq, op, parent, hash, pos, payload, s, e, ts)
        store.append(doc, r)
        revs.add(r)
    }

    private fun doStrike() {
        val a = editor.selectionStart
        val b = editor.selectionEnd
        if (a < 0 || b <= a) { t("先选中要划掉的文字。"); return }
        (editor.text!! as Spannable).setSpan(StrikethroughSpan(), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        addRev("strike", 0, "", a, b)
        updateCount()
        t("已划掉。原文还在，只是划了道线。")
    }

    /** 把「划掉」挪到当前输入行末尾 —— 横向贴右，纵向跟行 */
    private fun repositionStrike() {
        val lay = editor.layout ?: return
        val n = editor.text?.length ?: 0
        if (n == 0) { btnStrike.visibility = View.GONE; return }
        val pos = editor.selectionStart.coerceIn(0, n)
        val line = lay.getLineForOffset(pos)
        val y = (lay.getLineTop(line) - editor.scrollY + editor.totalPaddingTop).toFloat()
        val lh = (lay.getLineBottom(line) - lay.getLineTop(line)).toFloat()

        btnStrike.visibility = View.VISIBLE
        btnStrike.post {
            val bh = btnStrike.height.toFloat()
            btnStrike.translationY = y + (lh - bh) / 2f
            btnStrike.translationX = (editor.width - btnStrike.width - dp(6)).toFloat()
        }
    }

    private fun updateCount() {
        val t2 = editor.text ?: return
        var n = 0
        for (i in 0 until t2.length) {
            val c = t2[i]
            if (c == '\n' || c == '\u3000') continue
            n++
        }
        count.text = "${(n - stampChars).coerceAtLeast(0)} 字"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun t(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
