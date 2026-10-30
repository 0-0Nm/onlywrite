package com.greed.onlywrite

/**
 * 纯 Kotlin 的文本重放核心。不依赖任何 Android 类，可在 JVM 上直接单测。
 *
 * 设计要点：
 * - 只有一个坐标空间：canonical。时间行永远在文本里，隐藏由 UI 层的 span 负责。
 * - 所有下标在读取时夹紧，任何历史脏数据都不会抛异常。
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
    val stamps: List<IntRange>,    // canonical 坐标，时间行文本区间
    val strikes: List<IntRange>    // canonical 坐标，划线区间
)

object DocText {

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
                    sb.append('\n')
                    val st = sb.length
                    sb.append(r.payload)
                    stamps.add(st until sb.length)
                    sb.append('\n')
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

    /** 写入前的边界校验：越界即拒绝，坏数据进不了库 */
    fun safePos(pos: Int, len: Int): Int = pos.coerceIn(0, len)

    fun safeRange(s: Int, e: Int, len: Int): Pair<Int, Int>? {
        val a = s.coerceIn(0, len)
        val b = e.coerceIn(0, len)
        return if (b > a) a to b else null
    }

    fun countChars(text: String, stamps: List<IntRange>): Int {
        var n = 0
        val hide = BooleanArray(text.length)
        for (r in stamps) for (i in r.first until minOf(r.last, text.length)) hide[i] = true
        for (i in text.indices) {
            val c = text[i]
            if (hide[i] || c == '\n' || c == '\u3000') continue
            n++
        }
        return n
    }
}
