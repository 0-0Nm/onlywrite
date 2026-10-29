package com.greed.onlywrite

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.View
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
    private lateinit var tglTime: TextView

    private val doc = "main"
    private val revs = ArrayList<Rev>()
    private var suppress = false
    private var stampChars = 0
    private var showStamps = true

    /** canonical 下标 → 显示下标（隐藏时间行时会错位，靠它换算） */
    private var map = IntArray(0)

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
        tglTime = findViewById(R.id.tglTime)

        store = Store(this)

        showStamps = getSharedPreferences("ow", MODE_PRIVATE).getBoolean("show_stamps", true)

        editor.onRejected = { m -> t(m) }
        editor.onCaretMoved = { repositionStrike() }
        editor.onCarve = { ins -> if (!suppress) onTyped(ins) }
        editor.setOnScrollChangeListener { _, _, _, _, _ -> repositionStrike() }

        btnStrike.setOnClickListener { doStrike() }
        btnFont.setOnClickListener { pickFont.launch(arrayOf("font/*", "*/*")) }
        btnRec.setOnClickListener { t("拾音：下一步接") }
        tglTime.setOnClickListener { toggleStamps() }

        paintToggle()
        applyFont()
        loadAll()
    }

    /* ---------------- 时间行显隐 ---------------- */

    private fun toggleStamps() {
        showStamps = !showStamps
        getSharedPreferences("ow", MODE_PRIVATE).edit().putBoolean("show_stamps", showStamps).apply()
        paintToggle()
        rebuild(keepCaret = true)
        t(if (showStamps) "已显示时间行" else "已隐藏时间行（记录仍在链上）")
    }

    private fun paintToggle() {
        tglTime.setTextColor(if (showStamps) 0xFF8A8271.toInt() else 0xFF41464D.toInt())
    }

    /* ---------------- 渲染 ---------------- */

    private fun loadAll() {
        try {
            revs.clear()
            revs.addAll(store.all(doc))
            rebuild(keepCaret = false)
        } catch (e: Throwable) {
            // 宁可空着起来，也不要闪退
            revs.clear()
            suppress = true
            editor.setText("")
            suppress = false
            t("读取档案出错：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun rebuild(keepCaret: Boolean) {
        val oldCaret = editor.selectionStart

        val canon = StringBuilder()
        val stampRanges = ArrayList<IntRange>()
        stampChars = 0

        for (r in revs) {
            when (r.op) {
                "carve" -> {
                    val p = r.pos.coerceIn(0, canon.length)
                    canon.insert(p, r.payload)
                }
                "stamp" -> {
                    canon.append('\n')
                    val st = canon.length
                    canon.append(r.payload)
                    stampRanges.add(st until canon.length)
                    stampChars += r.payload.length
                    canon.append('\n')
                }
            }
        }

        val cs = canon.toString()
        val hidden = BooleanArray(cs.length)
        if (!showStamps) {
            for (rg in stampRanges) for (i in rg.first until minOf(rg.last, cs.length)) hidden[i] = true
        }

        val disp = StringBuilder()
        val idxToDisp = IntArray(cs.length + 1)
        for (i in 0..cs.length) {
            idxToDisp[i] = disp.length
            if (i < cs.length && !hidden[i]) disp.append(cs[i])
        }
        map = idxToDisp

        val sp = SpannableString(disp)
        for (r in revs) {
            if (r.op != "strike") continue
            val a = if (r.start in idxToDisp.indices) idxToDisp[r.start] else disp.length
            val b = if (r.end in idxToDisp.indices) idxToDisp[r.end] else disp.length
            if (b > a && a >= 0 && b <= sp.length)
                sp.setSpan(StrikethroughSpan(), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        suppress = true
        editor.setText(sp)
        val c = if (keepCaret) oldCaret.coerceIn(0, sp.length) else sp.length
        editor.setSelection(c)
        suppress = false
        updateCount()
        editor.post { repositionStrike() }
    }

    private fun dispToCanon(d: Int): Int {
        var best = 0
        for (i in map.indices) if (map[i] <= d) best = i
        return best
    }

    /* ---------------- 写 ---------------- */

    private fun onTyped(ins: String) {
        val pos = (editor.selectionStart - ins.length).coerceAtLeast(0)
        addRev("carve", dispToCanon(pos).coerceAtLeast(0), ins, 0, 0)
        if (ins.contains('\n')) {
            suppress = true
            val s = Clock.stamp()
            editor.text!!.append(s + "\n\u3000\u3000")
            addRev("stamp", 0, s, 0, 0)
            editor.setSelection(editor.text!!.length)
            stampChars += if (showStamps) s.length else 0
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
        val ca = dispToCanon(a)
        val cb = dispToCanon(b)
        (editor.text!! as Spannable).setSpan(StrikethroughSpan(), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        addRev("strike", 0, "", ca, cb)
        updateCount()
        t("已划掉。原文还在，只是划了道线。")
    }

    /* ---------------- 划掉按钮跟随 ---------------- */

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

    /* ---------------- 字体 ---------------- */

    private fun applyFont() {
        if (!fontDir.exists()) return
        runCatching {
            val tf = Typeface.createFromFile(fontDir)
            editor.typeface = tf
            listOf(findViewById<TextView>(R.id.title), count, btnFont, tglTime, btnStrike, btnRec)
                .forEach { it.typeface = tf }
            t("字体已载入")
        }.onFailure { t("字体加载失败：${it.message}") }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
