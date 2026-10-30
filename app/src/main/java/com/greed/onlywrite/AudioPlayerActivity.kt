package com.greed.onlywrite

import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
        pPath.text = path

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
                val file = File(path)
                if (!file.exists()) { state.text = "文件不存在"; return@setOnClickListener }
                mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
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

        findViewById<TextView>(R.id.pBack).setOnClickListener { finish() }
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
