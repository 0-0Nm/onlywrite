package com.greed.onlywrite

/**
 * 导出渲染：纯 Kotlin，无 Android 依赖，可单元测试。
 *
 * 约定：
 *  - 划掉的区间在 TXT 里写成 ⟦n⟧…⟦n⟧，n 为本篇内序号，成对且唯一
 *  - JSON 里 strikes 是 [{s,e,text}] 数组，给机器读
 */
object Export {

    fun parseRanges(strikes: String): List<Pair<Int, Int>> {
        if (strikes.isEmpty()) return emptyList()
        return strikes.split(";").mapNotNull {
            val p = it.split(":")
            if (p.size == 2) p[0].toIntOrNull()?.let { a -> p[1].toIntOrNull()?.let { b -> a to b } } else null
        }.filter { it.second > it.first }.sortedBy { it.first }.distinct()
    }

    /** 每段划线用 ⟦n⟧ … ⟦n⟧ 包住 */
    fun marked(body: String, strikes: String): String {
        val ranges = parseRanges(strikes)
        if (ranges.isEmpty()) return body

        val sb = StringBuilder()
        var i = 0
        var idx = 0
        while (i < body.length) {
            while (idx < ranges.size && ranges[idx].first < i) idx++
            val r = if (idx < ranges.size) ranges[idx] else null
            if (r != null && r.first == i) {
                val b = r.second.coerceAtMost(body.length)
                val tag = "\u27e6" + (idx + 1) + "\u27e7"
                sb.append(tag).append(body, r.first, b).append(tag)
                i = b
                idx++
            } else {
                sb.append(body[i])
                i++
            }
        }
        return sb.toString()
    }

    fun jsonEscape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    fun strikesJson(body: String, strikes: String): String {
        val ranges = parseRanges(strikes)
        if (ranges.isEmpty()) return "[]"
        return ranges.joinToString(",", "[", "]") { (a, b) ->
            val seg = body.substring(a, b.coerceAtMost(body.length))
            "{\"s\":" + a + ",\"e\":" + b + ",\"text\":\"" + jsonEscape(seg) + "\"}"
        }
    }

    /** 归档整条链：给第三方离线复算用 */
    fun chainJson(revisions: List<Rev>, plain: String, strikes: String, docId: String, ts: Long): String {
        val sb = StringBuilder()
        sb.append("{\"docId\":\"").append(jsonEscape(docId)).append("\"")
        sb.append(",\"archivedAt\":").append(ts)
        sb.append(",\"chainHead\":\"").append(revisions.lastOrNull()?.hash ?: "0".repeat(64)).append("\"")
        sb.append(",\"strikes\":").append(strikesJson(plain, strikes))
        sb.append(",\"revisions\":[")
        revisions.forEachIndexed { i, r ->
            if (i > 0) sb.append(",")
            sb.append("{\"seq\":").append(r.seq)
            sb.append(",\"op\":\"").append(r.op).append("\"")
            sb.append(",\"parent\":\"").append(r.parent).append("\"")
            sb.append(",\"hash\":\"").append(r.hash).append("\"")
            sb.append(",\"pos\":").append(r.pos)
            sb.append(",\"payload\":\"").append(jsonEscape(r.payload)).append("\"")
            sb.append(",\"s\":").append(r.start)
            sb.append(",\"e\":").append(r.end)
            sb.append(",\"ts\":").append(r.ts).append("}")
        }
        sb.append("]}")
        return sb.toString()
    }

    /** 校验串：第三方用同样算法能复算出同样的 hash */
    fun recordHash(doc: String, seq: Int, parent: String, op: String, payload: String, pos: Int, s: Int, e: Int, ts: Long): String =
        Hash.sha("$doc|$seq|$parent|$op|${Hash.sha(payload)}|$pos|$s|$e|$ts")
}
