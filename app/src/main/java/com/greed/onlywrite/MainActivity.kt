package com.greed.onlywrite

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.LinearLayout
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
    private lateinit var tglTime: TextView
    private lateinit var sbWrap: FrameLayout
    private lateinit var sbPanel: LinearLayout
    private lateinit var sbList: LinearLayout
    private lateinit var tabWrite: TextView
    private lateinit var tabArch: TextView
    private lateinit var tabRec: TextView

    private var doc = "main"
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
        doc = getSharedPreferences("ow", MODE_PRIVATE).getString("cur_doc", "main") ?: "main"

        editor.onRejected = { m -> t(m) }
        editor.onCaretMoved = { repositionStrike() }
        editor.onCarve = { ins -> if (!suppress) onTyped(ins) }
        editor.setOnScrollChangeListener { _, _, _, _, _ -> repositionStrike() }

        btnStrike.setOnClickListener { doStrike() }
        btnFont.setOnClickListener { pickFont.launch(arrayOf("font/*", "*/*")) }
        btnRec.setOnClickListener { t("拾音：下一步接") }
        tglTime.setOnClickListener { toggleStamps() }
        bindSidebar()

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


    /* ---------------- 侧栏 ---------------- */

    private fun bindSidebar() {
        sbWrap = findViewById(R.id.sbWrap)
        sbPanel = findViewById(R.id.sbPanel)
        sbList = findViewById(R.id.sbList)
        tabWrite = findViewById(R.id.tabWrite)
        tabArch = findViewById(R.id.tabArch)
        tabRec = findViewById(R.id.tabRec)

        findViewById<TextView>(R.id.btnMenu).setOnClickListener { openSidebar() }
        findViewById<View>(R.id.sbScrim).setOnClickListener { closeSidebar() }
        sbPanel.setOnClickListener { }
        tabWrite.setOnClickListener { selectTab(0) }
        tabArch.setOnClickListener { selectTab(1) }
        tabRec.setOnClickListener { selectTab(2) }

        findViewById<TextView>(R.id.sbExport).setOnClickListener {
            val list = store.allArchives()
            if (list.isEmpty()) { t("还没有归档。"); return@setOnClickListener }
            val sb = StringBuilder()
            for (a in list) { sb.append(a.name).append("\n\n").append(a.plain).append("\n\n\n") }
            t("共 " + list.size + " 条归档，导出功能下一步接文件选择器")
            android.app.AlertDialog.Builder(this)
                .setTitle("归档全文 · " + list.size + " 条")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show()
        }
    }

    private fun openSidebar() {
        sbWrap.visibility = View.VISIBLE
        sbPanel.translationX = -dp(320).toFloat()
        sbPanel.animate().translationX(0f).setDuration(240).start()
        selectTab(1)
    }

    private fun closeSidebar() {
        sbPanel.animate().translationX(-dp(320).toFloat()).setDuration(200)
            .withEndAction { sbWrap.visibility = View.GONE }.start()
    }

    private fun selectTab(i: Int) {
        val on = 0xFFE9E6E1.toInt()
        val off = 0xFF5F646C.toInt()
        tabWrite.setTextColor(if (i == 0) on else off)
        tabArch.setTextColor(if (i == 1) on else off)
        tabRec.setTextColor(if (i == 2) on else off)
        renderList(i)
    }

    private fun renderList(tab: Int) {
        sbList.removeAllViews()
        when (tab) {
            0 -> {
                sbList.addView(row("＋ 归档并开新卷") { archiveAndNew() })
                sbList.addView(hint("正在刻录的是一卷新的。点上面那条，封存它，另起一卷。"))
            }
            1 -> {
                val list = store.allArchives()
                if (list.isEmpty()) {
                    sbList.addView(hint("还没有归档。\n写完一卷，到「刻录」点归档。"))
                } else {
                    for (a in list) {
                        val n = a.plain.replace(Regex("\\s"), "").length
                        sbList.addView(row(a.name + "\n" + n + " 字 · " + a.strokes + " 笔") { showArchive(a) })
                    }
                }
            }
            else -> sbList.addView(hint("拾音：下一步接。\n开启后会在光标处刻「寄耳」，结束刻「还心」，音频按时间戳归档。"))
        }
    }

    private fun row(text: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFF8D8981.toInt())
        tv.textSize = 12f
        tv.setLineSpacing(0f, 1.5f)
        tv.setPadding(dp(18), dp(10), dp(18), dp(10))
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun hint(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFF4A4F57.toInt())
        tv.textSize = 11f
        tv.setLineSpacing(0f, 1.7f)
        tv.setPadding(dp(18), dp(14), dp(18), dp(14))
        return tv
    }

    private fun showArchive(a: Archive) {
        android.app.AlertDialog.Builder(this)
            .setTitle(a.name)
            .setMessage(a.plain)
            .setPositiveButton("返回", null)
            .show()
    }

    private fun archiveAndNew() {
        val txt = editor.text?.toString() ?: ""
        if (txt.trim().isEmpty()) { t("还没有字，没什么可归档的。"); return }
        val name = Clock.stamp()
        store.putArchive(name, System.currentTimeMillis(), txt, revs.size)
        doc = "doc-" + System.currentTimeMillis()
        getSharedPreferences("ow", MODE_PRIVATE).edit().putString("cur_doc", doc).apply()
        revs.clear()
        rebuild(keepCaret = false)
        t("已归档：" + name + "\n新的一卷已经铺开。")
        selectTab(1)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
