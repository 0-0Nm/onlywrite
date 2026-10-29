package com.greed.onlywrite

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var editor: StoneEditText
    private lateinit var count: TextView

    private val doc = "main"
    private val revs = ArrayList<Rev>()
    private var suppress = false
    private var stampChars = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editor = findViewById(R.id.editor)
        count = findViewById(R.id.count)

        store = Store(this)

        // 划掉按钮
        val bar = findViewById<LinearLayout>(R.id.bar)
        val strike = TextView(this).apply {
            text = "划掉"
            setTextColor(0xFF8A6A6A.toInt())
            textSize = 12f
            setPadding(24, 16, 24, 16)
            setOnClickListener { doStrike() }
        }
        bar.addView(strike, 0)

        editor.onRejected = { msg -> toast(msg) }
        editor.onCarve = { ins -> onTyped(ins) }

        loadAll()
    }

    private fun loadAll() {
        revs.clear()
        revs.addAll(store.all(doc))

        val sb = StringBuilder()
        val strikes = ArrayList<IntRange>()
        for (r in revs) {
            when (r.op) {
                "carve" -> sb.insert(minOf(r.pos, sb.length), r.payload)
                "stamp" -> {
                    sb.append('\n'); sb.append(r.payload); sb.append('\n')
                    stampChars += r.payload.length
                }
                "strike" -> strikes.add(r.start until r.end)
            }
        }
        val text = SpannableString(sb.toString())
        for (rg in strikes) {
            if (rg.first in 0 until text.length && rg.last < text.length) {
                text.setSpan(
                    StrikethroughSpan(), rg.first, rg.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        suppress = true
        editor.setText(text)
        editor.setSelection(text.length)
        suppress = false
        stampChars += 0
        updateCount()
    }

    private fun onTyped(ins: String) {
        if (suppress) return
        val pos = editor.selectionStart - ins.length
        addRev("carve", if (pos < 0) editor.text.length - ins.length else pos, ins, 0, 0)

        if (ins.contains('\n')) {
            suppress = true
            val s = Clock.stamp()
            val block = s + "\n\u3000\u3000"
            val at = editor.text.length
            editor.text.append(block)
            addRev("stamp", 0, s, 0, 0)
            editor.setSelection(editor.text.length)
            stampChars += s.length
            suppress = false
        }
        updateCount()
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
        if (a < 0 || b <= a) { toast("先选中要划掉的文字。"); return }
        val sp = editor.text as Spannable
        sp.setSpan(StrikethroughSpan(), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        addRev("strike", 0, "", a, b)
        updateCount()
        toast("已划掉。原文还在，只是划了道线。")
    }

    private fun updateCount() {
        val t = editor.text
        var n = 0
        for (i in 0 until t.length) {
            val c = t[i]
            if (c == '\n' || c == '\u3000') continue
            n++
        }
        count.text = "${(n - stampChars).coerceAtLeast(0)} 字"
    }

    private fun toast(m: String) {
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show()
    }
}
