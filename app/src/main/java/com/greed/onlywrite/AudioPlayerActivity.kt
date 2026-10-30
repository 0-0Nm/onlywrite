package com.greed.onlywrite

import android.graphics.Typeface
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.provider.MediaStore
import androidx.core.content.FileProvider
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class AudioPlayerActivity : AppCompatActivity() {

    private var mp: MediaPlayer? = null
    private var playing = false
    private var path = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        val title = findViewById<TextView>(R.id.pTitle)
        val play = findViewById<TextView>(R.id.pPlay)
        val state = findViewById<TextView>(R.id.pState)
        val pPath = findViewById<TextView>(R.id.pPath)

        val name = intent.getStringExtra("name") ?: ""
        path = intent.getStringExtra("path") ?: ""

        title.text = name
        val human = humanPath(path)
        pPath.text = human

        val f = File(filesDir, "font.ttf")
        if (f.exists()) runCatching {
            val tf = Typeface.createFromFile(f)
            deep(findViewById(android.R.id.content), tf)
        }

        play.setOnClickListener {
            if (playing) {
                mp?.pause()
                playing = false
                play.text = "▪ 播放"
                state.text = "已暂停"
                return@setOnClickListener
            }
            if (mp == null) {
                mp = MediaPlayer().apply {
                    if (path.startsWith("content://")) setDataSource(this@AudioPlayerActivity, android.net.Uri.parse(path))
                    else setDataSource(path)
                    prepare()
                    setOnCompletionListener {
                        playing = false
                        play.text = "▪ 播放"
                        state.text = "播完"
                        it.seekTo(0)
                    }
                }
            }
            mp?.start()
            playing = true
            play.text = "▪ 暂停"
            state.text = "播放中"
        }

        pPath.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("path", humanPath(path)))
            Toast.makeText(this, "路径已复制", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.pOpen).setOnClickListener {
            runCatching {
                val uri = if (path.startsWith("content://")) android.net.Uri.parse(path)
                          else FileProvider.getUriForFile(this, "com.greed.onlywrite.fileprovider", File(path))
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "audio/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(i)
            }.onFailure { Toast.makeText(this, "没有可打开它的应用", Toast.LENGTH_SHORT).show() }
        }

        findViewById<TextView>(R.id.pBack).setOnClickListener { finish() }
    }

    /** content:// 转成人能读的绝对路径；拿不到就按 RELATIVE_PATH 拼 */
    private fun humanPath(p: String): String {
        if (!p.startsWith("content://")) return p
        runCatching {
            val uri = android.net.Uri.parse(p)
            val proj = arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DISPLAY_NAME
            )
            contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val data = c.getString(0)
                    if (!data.isNullOrEmpty()) return data
                    val rel = c.getString(1) ?: ""
                    val disp = c.getString(2) ?: ""
                    if (disp.isNotEmpty()) return "/storage/emulated/0/" + rel + disp
                }
            }
        }
        return p
    }

    private fun deep(v: View, tf: Typeface) {
        if (v is TextView) v.typeface = tf
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) deep(v.getChildAt(i), tf)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { mp?.release() }
        mp = null
    }
}
