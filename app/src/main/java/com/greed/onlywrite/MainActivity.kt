package com.greed.onlywrite

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.MediaRecorder
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
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

    private var lastStamps: List<IntRange> = emptyList()
    private var floor = 0
    private var appTf: Typeface? = null
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var recName: String = ""

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
        editor.onCaretMoved = { if (editor.selectionStart < floor) editor.post { clampCaret() } else repositionStrike() }
        editor.onInserted = { start, ins -> if (!suppress) onInserted(start, ins) }
        editor.setOnScrollChangeListener { _, _, _, _, _ -> repositionStrike() }

        btnStrike.setOnClickListener { doStrike() }
        btnFont.setOnClickListener { pickFont.launch(arrayOf("font/*", "*/*")) }
        btnRec.setOnClickListener { toggleRec() }
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

    private fun toRecs(): List<Rec> = revs.map { Rec(it.seq, it.op, it.pos, it.payload, it.start, it.end) }

    private fun clampCaret() {
        if (editor.selectionStart < floor) editor.setSelection(floor.coerceAtMost(editor.text?.length ?: 0))
    }

    private fun rebuild(keepCaret: Boolean) {
        val oldCaret = editor.selectionStart
        val rendered = DocText.render(toRecs())
        lastStamps = rendered.stamps
        floor = rendered.floor

        val sp = android.text.SpannableString(rendered.text)
        for (r in rendered.stamps) {
            if (r.first < 0 || r.last + 1 > sp.length) continue
            if (!showStamps) {
                sp.setSpan(HiddenStampSpan(), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                // 与网页版一致：小一号、暗灰、等宽
                sp.setSpan(RelativeSizeSpan(0.68f), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(ForegroundColorSpan(0xFF5F5C52.toInt()), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val target = (editor.textSize * 0.60f).toInt()
                sp.setSpan(StampLineSpan(target), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val tf = appTf
                if (tf != null) sp.setSpan(StampFontSpan(tf), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                else sp.setSpan(TypefaceSpan("monospace"), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        for (r in rendered.strikes) {
            if (r.last + 1 <= sp.length) {
                sp.setSpan(
                    RedStrikeSpan(0xFF6B7078.toInt(), 0xFFB0555A.toInt(), dpF(1.4f)),
                    r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 拾音标记单独着色
        val markRe = Regex("「寄耳」|「还心」")
        for (m in markRe.findAll(rendered.text)) {
            val a = m.range.first
            val b = m.range.last + 1
            if (b <= sp.length) sp.setSpan(
                ForegroundColorSpan(0xFFB08D57.toInt()), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        suppress = true
        editor.recording = false
        editor.setText(sp)
        val c = if (keepCaret) oldCaret.coerceIn(0, sp.length) else sp.length
        editor.setSelection(c)
        editor.recording = true
        suppress = false
        supdateCount()
        editor.post { repositionStrike() }
    }

    private fun supdateCount() {
        val txt = editor.text?.toString() ?: return
        count.text = DocText.countChars(txt, lastStamps).toString() + " 字"
    }

    /* ---------------- 写 ---------------- */

    private fun onInserted(start: Int, ins: String) {
        val nl = ins.indexOf('\n')
        if (nl < 0) {
            addRev("carve", start, ins, 0, 0)
            supdateCount()
            editor.post { repositionStrike() }
            return
        }

        val pre = ins.substring(0, nl)
        val post = ins.substring(nl + 1)
        if (pre.isNotEmpty()) addRev("carve", start, pre, 0, 0)

        val stampPos = start + pre.length
        val s = Clock.stamp()
        val block = "\n" + s + "\n" + DocText.INDENT

        suppress = true
        editor.recording = false
        editor.text?.replace(stampPos, stampPos + 1, block)
        editor.recording = true
        suppress = false

        addRev("stamp", stampPos, s, 0, 0)
        addRev("carve", stampPos + s.length + 2, DocText.INDENT, 0, 0)
        if (post.isNotEmpty()) addRev("carve", stampPos + s.length + 4, post, 0, 0)

        floor = stampPos + s.length + 4
        val caret = (floor + post.length).coerceAtMost(editor.text?.length ?: 0)
        rebuild(keepCaret = false)
        editor.setSelection(caret.coerceAtMost(editor.text?.length ?: 0))
        supdateCount()
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
        val len = editor.text?.length ?: 0
        val r = DocText.safeRange(editor.selectionStart, editor.selectionEnd, len)
        if (r == null) { t("先选中要划掉的文字。"); return }
        if (r.first < floor) { t("这一行已经封存了，不能再划。"); return }
        val (a, b) = r
        val sp = editor.text as? Spannable
        if (sp != null) {
            sp.setSpan(
                RedStrikeSpan(0xFF6B7078.toInt(), 0xFFB0555A.toInt(), dpF(1.4f)),
                a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        addRev("strike", 0, "", a, b)
        supdateCount()
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

    /* ---------------- 字体 ---------------- */

    private fun applyFont() {
        if (!fontDir.exists()) return
        runCatching {
            val tf = Typeface.createFromFile(fontDir)
            appTf = tf
            applyDeep(findViewById(android.R.id.content), tf)
            rebuild(keepCaret = true)
            t("字体已载入")
        }.onFailure { t("字体加载失败：${it.message}") }
    }

    private fun applyDeep(v: android.view.View, tf: Typeface) {
        if (v is TextView) v.typeface = tf
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) applyDeep(v.getChildAt(i), tf)
        }
    }


    /* ---------------- 拾音 ---------------- */

    private val permAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec() else t("没有麦克风权限，拾音开不了。")
    }

    private fun toggleRec() {
        if (recorder != null) stopRec() else startRec()
    }

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permAudio.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        try {
            val dir = File(filesDir, "records").apply { mkdirs() }
            val nm = Clock.stamp()
            val f = File(dir, nm.replace(Regex("[^0-9A-Za-z]+"), "-") + ".mp4")
            val mr = MediaRecorder()
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96000)
            mr.setAudioSamplingRate(44100)
            mr.setOutputFile(f.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            recFile = f
            recName = nm
            insertMarker("寄耳")
            t("拾音中。再点一次结束。")
        } catch (e: Throwable) {
            runCatching { recorder?.release() }
            recorder = null
            t("拾音打不开：" + (e.message ?: "未知"))
        }
    }

    private fun stopRec() {
        insertMarker("还心")
        val f = recFile
        val nm = recName
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        if (f != null && f.exists()) {
            store.putAudio(nm, System.currentTimeMillis(), f.absolutePath, f.length(), doc)
            t("已归档拾音：" + f.name)
        } else {
            t("这一段没录到东西。")
        }
        selectTab(2)
    }

    /** 在光标处刻下标记，带书名号，与普通输入等价入链 */
    private fun insertMarker(text: String) {
        val s = "「" + text + "」"
        val len = editor.text?.length ?: 0
        val pos = DocText.safePos(editor.selectionStart, len)
        suppress = true
        editor.recording = false
        editor.text?.insert(pos, s)
        editor.recording = true
        suppress = false
        addRev("carve", pos, s, 0, 0)
        rebuild(keepCaret = false)
        editor.setSelection((pos + s.length).coerceAtMost(editor.text?.length ?: 0))
        supdateCount()
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
        appTf?.let { applyDeep(sbPanel, it) }
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
            else -> {
                val list = store.allAudio()
                if (list.isEmpty()) {
                    sbList.addView(hint("还没有拾音。\n点底栏「▪拾音」开始，结束时会刻下「还心」并归档。"))
                } else {
                    for (a in list) {
                        val kb = (a.size / 1024).toString() + " KB"
                        sbList.addView(row(a.name + "\n" + kb) {
                            val i = android.content.Intent(this, AudioPlayerActivity::class.java)
                            i.putExtra("name", a.name)
                            i.putExtra("path", a.path)
                            startActivity(i)
                        })
                    }
                }
            }
        }
    }

    private fun row(text: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFF8D8981.toInt())
        tv.textSize = 12f
        tv.setLineSpacing(0f, 1.5f)
        tv.setPadding(dp(18), dp(10), dp(18), dp(10))
        appTf?.let { tv.typeface = it }
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
        appTf?.let { tv.typeface = it }
        return tv
    }

    private fun showArchive(a: Archive) {
        val i = android.content.Intent(this, ArchiveViewActivity::class.java)
        i.putExtra("name", a.name)
        startActivity(i)
        closeSidebar()
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
    private fun dpF(v: Float) = v * resources.displayMetrics.density
    private fun t(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
