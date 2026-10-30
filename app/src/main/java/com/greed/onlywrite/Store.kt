package com.greed.onlywrite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Archive(val name: String, val ts: Long, val plain: String, val strokes: Int)

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

class Store(ctx: Context) : SQLiteOpenHelper(ctx, "onlywrite.db", null, 3) {

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
        db.execSQL("CREATE TABLE archives(name TEXT PRIMARY KEY, ts INTEGER, plain TEXT, strokes INTEGER)")
        db.execSQL(
            "CREATE TRIGGER arch_no_update BEFORE UPDATE ON archives " +
                "BEGIN SELECT RAISE(ABORT,'append-only'); END"
        )
        db.execSQL(
            "CREATE TRIGGER arch_no_delete BEFORE DELETE ON archives " +
                "BEGIN SELECT RAISE(ABORT,'append-only'); END"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS notes(archive TEXT NOT NULL, s INTEGER, e INTEGER, body TEXT, ts INTEGER)")
        }
        if (old < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS archives(name TEXT PRIMARY KEY, ts INTEGER, plain TEXT, strokes INTEGER)")
            runCatching { db.execSQL("CREATE TRIGGER arch_no_update BEFORE UPDATE ON archives BEGIN SELECT RAISE(ABORT,'append-only'); END") }
            runCatching { db.execSQL("CREATE TRIGGER arch_no_delete BEFORE DELETE ON archives BEGIN SELECT RAISE(ABORT,'append-only'); END") }
        }
    }

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

    fun putArchive(name: String, ts: Long, plain: String, strokes: Int) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO archives(name,ts,plain,strokes) VALUES(?,?,?,?)",
            arrayOf(name, ts, plain, strokes)
        )
    }

    fun allArchives(): List<Archive> {
        val out = ArrayList<Archive>()
        readableDatabase.rawQuery(
            "SELECT name,ts,plain,strokes FROM archives ORDER BY ts ASC", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Archive(c.getString(0), c.getLong(1), c.getString(2) ?: "", c.getInt(3)))
            }
        }
        return out
    }

    fun putNote(archive: String, s: Int, e: Int, body: String) {
        writableDatabase.execSQL(
            "INSERT INTO notes(archive,s,e,body,ts) VALUES(?,?,?,?,?)",
            arrayOf(archive, s, e, body, System.currentTimeMillis())
        )
    }

    fun notesOf(archive: String): List<Triple<Int, Int, String>> {
        val out = ArrayList<Triple<Int, Int, String>>()
        readableDatabase.rawQuery(
            "SELECT s,e,body FROM notes WHERE archive=? ORDER BY ts ASC", arrayOf(archive)
        ).use { c ->
            while (c.moveToNext()) out.add(Triple(c.getInt(0), c.getInt(1), c.getString(2) ?: ""))
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
