package com.greed.onlywrite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Rev(
    val seq: Int,
    val op: String,          // carve / stamp / strike
    val parent: String,
    val hash: String,
    val pos: Int,
    val payload: String,
    val start: Int,
    val end: Int,
    val ts: Long
)

object Hash {
    fun sha(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }
}

object Clock {
    fun stamp(now: Long = System.currentTimeMillis()): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        f.timeZone = TimeZone.getDefault()
        val off = TimeZone.getDefault().getOffset(now) / 60000
        val sign = if (off >= 0) "+" else "-"
        val a = Math.abs(off)
        return f.format(Date(now)) + String.format(" UTC%s%02d:%02d", sign, a / 60, a % 60)
    }
}

class Store(ctx: Context) : SQLiteOpenHelper(ctx, "onlywrite.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE revisions(
                 docId TEXT NOT NULL, seq INTEGER NOT NULL, op TEXT NOT NULL,
                 parent TEXT NOT NULL, hash TEXT NOT NULL,
                 pos INTEGER, payload TEXT, s INTEGER, e INTEGER, ts INTEGER,
                 PRIMARY KEY(docId, seq))"""
        )
        db.execSQL("CREATE INDEX idx_doc ON revisions(docId, seq)")
        // 物理层面禁改禁删
        db.execSQL(
            "CREATE TRIGGER no_update BEFORE UPDATE ON revisions " +
                "BEGIN SELECT RAISE(ABORT,'append-only'); END"
        )
        db.execSQL(
            "CREATE TRIGGER no_delete BEFORE DELETE ON revisions " +
                "BEGIN SELECT RAISE(ABORT,'append-only'); END"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit

    fun all(doc: String): List<Rev> {
        val out = ArrayList<Rev>()
        readableDatabase.rawQuery(
            "SELECT seq,op,parent,hash,pos,payload,s,e,ts FROM revisions " +
                "WHERE docId=? ORDER BY seq ASC", arrayOf(doc)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Rev(
                        c.getInt(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getInt(4), c.getString(5) ?: "", c.getInt(6), c.getInt(7), c.getLong(8)
                    )
                )
            }
        }
        return out
    }

    fun append(doc: String, r: Rev) {
        writableDatabase.execSQL(
            "INSERT INTO revisions(docId,seq,op,parent,hash,pos,payload,s,e,ts) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?)",
            arrayOf(doc, r.seq, r.op, r.parent, r.hash, r.pos, r.payload, r.start, r.end, r.ts)
        )
    }
}
