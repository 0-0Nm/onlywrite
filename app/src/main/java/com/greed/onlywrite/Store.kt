package com.greed.onlywrite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Note(val s: Int, val e: Int, val body: String, val ts: Long)

data class AudioRec(val name: String, val ts: Long, val path: String, val size: Long)

data class Archive(val name: String, val ts: Long, val plain: String, val strokes: Int, val strikes: String = "", val chain: String = "")

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

class Store(ctx: Context) : SQLiteOpenHelper(ctx, "onlywrite.db", null, 6) {

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
        db.execSQL("CREATE TABLE archives(name TEXT PRIMARY KEY, ts INTEGER, plain TEXT, strokes INTEGER, strikes TEXT, chain TEXT)")
        db.execSQL(
            "CREATE TRIGGER arch_no_update BEFORE UPDATE ON archives " +
                "BEGIN SELECT RAISE(ABORT,'append-only'); END"
        )
        db.execSQL(
            "CREATE TRIGGER arch_no_delete BEFORE DELETE ON archives " +
                "BEGIN SELECT RAISE(ABORT,'append-only'); END"
        )
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // 每次打开都做一次结构与列的完整自检：
        // 历史上 runCatching 吞掉过 DDL 异常，版本号却已推进，导致表/列永久缺失且不再升级。
        runCatching { ensureSchema(db) }
    }

    /** 幂等：缺表建表、缺列补列。任何历史坏状态打开即自愈。 */
    private fun ensureSchema(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS revisions(
                 docId TEXT NOT NULL, seq INTEGER NOT NULL, op TEXT NOT NULL,
                 parent TEXT NOT NULL, hash TEXT NOT NULL,
                 pos INTEGER, payload TEXT, s INTEGER, e INTEGER, ts INTEGER,
                 PRIMARY KEY(docId, seq))"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_doc ON revisions(docId, seq)")
        db.execSQL("CREATE TABLE IF NOT EXISTS archives(name TEXT PRIMARY KEY, ts INTEGER, plain TEXT, strokes INTEGER, strikes TEXT, chain TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS audio(name TEXT PRIMARY KEY, ts INTEGER, path TEXT, size INTEGER, doc TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS notes(archive TEXT NOT NULL, s INTEGER, e INTEGER, body TEXT, ts INTEGER)")
        addColumnIfMissing(db, "archives", "strikes", "TEXT")
        addColumnIfMissing(db, "archives", "chain", "TEXT")
        runCatching { db.execSQL("CREATE TRIGGER IF NOT EXISTS no_update BEFORE UPDATE ON revisions BEGIN SELECT RAISE(ABORT,'append-only'); END") }
        runCatching { db.execSQL("CREATE TRIGGER IF NOT EXISTS no_delete BEFORE DELETE ON revisions BEGIN SELECT RAISE(ABORT,'append-only'); END") }
        runCatching { db.execSQL("CREATE TRIGGER IF NOT EXISTS arch_no_update BEFORE UPDATE ON archives BEGIN SELECT RAISE(ABORT,'append-only'); END") }
        runCatching { db.execSQL("CREATE TRIGGER IF NOT EXISTS arch_no_delete BEFORE DELETE ON archives BEGIN SELECT RAISE(ABORT,'append-only'); END") }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, col: String, type: String) {
        var exists = false
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            while (c.moveToNext()) if (c.getString(1) == col) exists = true
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $col $type")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 6) addColumnIfMissing(db, "archives", "chain", "TEXT")
        if (old < 5) addColumnIfMissing(db, "archives", "strikes", "TEXT")
        if (old < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS audio(name TEXT PRIMARY KEY, ts INTEGER, path TEXT, size INTEGER, doc TEXT)")
        }
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

    fun putArchive(name: String, ts: Long, plain: String, strokes: Int, strikes: String = "", chain: String = "") {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO archives(name,ts,plain,strokes,strikes,chain) VALUES(?,?,?,?,?,?)",
            arrayOf(name, ts, plain, strokes, strikes, chain)
        )
    }

    fun allArchives(): List<Archive> {
        val out = ArrayList<Archive>()
        readableDatabase.rawQuery(
            "SELECT name,ts,plain,strokes,strikes,chain FROM archives ORDER BY ts ASC", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Archive(c.getString(0), c.getLong(1), c.getString(2) ?: "", c.getInt(3), c.getString(4) ?: "", c.getString(5) ?: ""))
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

    fun notesOf(archive: String): List<Note> {
        val out = ArrayList<Note>()
        readableDatabase.rawQuery(
            "SELECT s,e,body,ts FROM notes WHERE archive=? ORDER BY ts ASC", arrayOf(archive)
        ).use { c ->
            while (c.moveToNext()) out.add(Note(c.getInt(0), c.getInt(1), c.getString(2) ?: "", c.getLong(3)))
        }
        return out
    }

    fun putAudio(name: String, ts: Long, path: String, size: Long, doc: String) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO audio(name,ts,path,size,doc) VALUES(?,?,?,?,?)",
            arrayOf(name, ts, path, size, doc)
        )
    }

    fun allAudio(): List<AudioRec> {
        val out = ArrayList<AudioRec>()
        readableDatabase.rawQuery("SELECT name,ts,path,size FROM audio ORDER BY ts ASC", null).use { c ->
            while (c.moveToNext()) out.add(AudioRec(c.getString(0), c.getLong(1), c.getString(2) ?: "", c.getLong(3)))
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
