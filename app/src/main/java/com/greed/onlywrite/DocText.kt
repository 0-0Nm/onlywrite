package com.greed.onlywrite

/**
 * 纯 Kotlin 重放核心。只有一个坐标空间：canonical。
 * 时间行是"插入到指定位置"的，不是"追加到末尾"的 —— 否则一旦用户先按了回车、
 * 后面又接着打字，重放出来的顺序就和屏幕上的不一样。
 */
data class Rec(
    val seq: Int,
    val op: String,      // carve | stamp | strike
    val pos: Int,
    val payload: String,
    val s: Int,
    val e: Int
)

data class Rendered(
    val text: String,
    val stamps: List<IntRange>,
    val strikes: List<IntRange>
) {
    /** 封存线：最后一条时间行的行尾（含其后的两格缩进）之后 */
    val floor: Int
        get() {
            if (stamps.isEmpty()) return 0
            return (stamps.last().last + 1 + 2).coerceAtMost(text.length)
        }
}

object DocText {

    const val INDENT = "\u3000\u3000"

    fun render(recs: List<Rec>): Rendered {
        val sb = StringBuilder()
        val stamps = ArrayList<IntRange>()

        for (r in recs.sortedBy { it.seq }) {
            when (r.op) {
                "carve" -> {
                    val p = r.pos.coerceIn(0, sb.length)
                    sb.insert(p, r.payload)
                }
                "stamp" -> {
                    val p = r.pos.coerceIn(0, sb.length)
                    sb.insert(p, "\n" + r.payload + "\n")
                    stamps.add((p + 1) until (p + 1 + r.payload.length))
                }
            }
        }

        val len = sb.length
        val strikes = ArrayList<IntRange>()
        for (r in recs) {
            if (r.op != "strike") continue
            val a = r.s.coerceIn(0, len)
            val b = r.e.coerceIn(0, len)
            if (b > a) strikes.add(a until b)
        }
        return Rendered(sb.toString(), stamps, strikes)
    }

    fun safePos(pos: Int, len: Int): Int = pos.coerceIn(0, len)

    fun safeRange(s: Int, e: Int, len: Int): Pair<Int, Int>? {
        val a = s.coerceIn(0, len)
        val b = e.coerceIn(0, len)
        return if (b > a) a to b else null
    }

    fun countChars(text: String, stamps: List<IntRange>): Int {
        val hide = BooleanArray(text.length)
        for (r in stamps) {
            val last = minOf(r.last, text.length - 1)
            for (i in r.first..last) if (i in hide.indices) hide[i] = true
        }
        var n = 0
        for (i in text.indices) {
            val c = text[i]
            if (hide[i] || c == '\n' || c == '\u3000') continue
            n++
        }
        return n
    }
}
