package com.azad.music

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import android.view.View

class MainActivity : Activity() {

    data class Song(
        val id: Long,
        val title: String,
        val artist: String,
        val uri: Uri,
        val duration: Long
    )

    private val songs = mutableListOf<Song>()
    private var player: MediaPlayer? = null
    private var current = -1
    private var shuffle = false
    private var repeat = false

    private lateinit var list: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var playButton: Button
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            player?.let {
                if (it.isPlaying) {
                    seek.progress = it.currentPosition
                    timeText.text =
                        "${fmt(it.currentPosition)} / ${fmt(it.duration)}"
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestAudioPermission()
        handler.post(tick)
    }

    private fun buildUi() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 20, 22, 16)
            setBackgroundColor(Color.rgb(16, 17, 27))
        }

        val header = TextView(this).apply {
            text = "AZAD MUSIC  ♪"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, 1)
            setPadding(0, 8, 0, 16)
        }
        root.addView(header)

        val sub = TextView(this).apply {
            text = "Your music, your mood."
            textSize = 14f
            setTextColor(Color.rgb(165, 165, 185))
            setPadding(0, 0, 0, 18)
        }
        root.addView(sub)

        val scroll = ScrollView(this)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 14)
            setBackgroundColor(Color.rgb(32, 33, 48))
        }

        titleText = TextView(this).apply {
            text = "Choose a song"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, 1)
        }
        panel.addView(titleText)

        artistText = TextView(this).apply {
            text = "Local music library"
            textSize = 14f
            setTextColor(Color.rgb(170, 170, 190))
            setPadding(0, 5, 0, 8)
        }
        panel.addView(artistText)

        seek = SeekBar(this)
        panel.addView(seek)

        timeText = TextView(this).apply {
            text = "0:00 / 0:00"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        panel.addView(timeText)

        seek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    s: SeekBar,
                    p: Int,
                    u: Boolean
                ) {}

                override fun onStartTrackingTouch(s: SeekBar) {}

                override fun onStopTrackingTouch(s: SeekBar) {
                    player?.seekTo(s.progress)
                }
            }
        )

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }

        val shuffleBtn = button("SHUFFLE")
        shuffleBtn.setOnClickListener {
            shuffle = !shuffle
            shuffleBtn.text =
                if (shuffle) "SHUFFLE ON" else "SHUFFLE"
        }

        val prevBtn = button("⏮")
        prevBtn.setOnClickListener {
            previous()
        }

        playButton = button("▶")
        playButton.textSize = 22f
        playButton.setOnClickListener {
            toggle()
        }

        val nextBtn = button("⏭")
        nextBtn.setOnClickListener {
            next()
        }

        val repeatBtn = button("REPEAT")
        repeatBtn.setOnClickListener {
            repeat = !repeat
            repeatBtn.text =
                if (repeat) "REPEAT ON" else "REPEAT"
        }

        listOf(
            shuffleBtn,
            prevBtn,
            playButton,
            nextBtn,
            repeatBtn
        ).forEach {
            controls.addView(it)
        }

        panel.addView(controls)
        root.addView(panel)

        setContentView(root)
    }

    private fun button(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 55, 130))
            isAllCaps = false
        }
    }

    private fun requestAudioPermission() {

        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            requestPermissions(arrayOf(perm), 10)
        }
    }

    override fun onRequestPermissionsResult(
        r: Int,
        p: Array<out String>,
        g: IntArray
    ) {
        super.onRequestPermissionsResult(r, p, g)

        if (r == 10 &&
            g.isNotEmpty() &&
            g[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadSongs()
        } else {
            Toast.makeText(
                this,
                "Allow music access to show your songs",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadSongs() {

        songs.clear()

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sort =
            "${MediaStore.Audio.Media.TITLE} ASC"

        val c: Cursor? = contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sort
        )

        c?.use {
            while (it.moveToNext()) {

                val id = it.getLong(0)

                songs.add(
                    Song(
                        id,
                        it.getString(1) ?: "Unknown song",
                        it.getString(2) ?: "Unknown artist",
                        ContentUris.withAppendedId(collection, id),
                        it.getLong(3)
                    )
                )
            }
        }

        renderSongs()
    }

    private fun renderSongs() {

        list.removeAllViews()

        if (songs.isEmpty()) {

            list.addView(
                TextView(this).apply {
                    text = "No audio files found on this device."
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(24, 24, 24, 24)
                }
            )

            return
        }

        songs.forEachIndexed { i, s ->

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)

                setBackgroundColor(
                    if (i % 2 == 0)
                        Color.rgb(24, 25, 37)
                    else
                        Color.rgb(19, 20, 30)
                )
            }

            val songTitle = TextView(this).apply {
                text = "♫  ${s.title}"
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            val songArtist = TextView(this).apply {
                text = "${s.artist}   •   ${fmt(s.duration)}"
                textSize = 12f
                setTextColor(Color.rgb(165, 165, 180))
            }

            row.addView(songTitle)
            row.addView(songArtist)

            row.setOnClickListener {
                play(i)
            }

            list.addView(row)
        }
    }

    private fun play(index: Int) {

        if (index !in songs.indices) return

        current = index

        player?.release()
        player = null

        player = MediaPlayer().apply {

            setDataSource(
                this@MainActivity,
                songs[index].uri
            )

            setOnPreparedListener {

                it.start()

                playButton.text = "❚❚"

                seek.max = it.duration

                titleText.text = songs[index].title
                artistText.text = songs[index].artist

                timeText.text =
                    "0:00 / ${fmt(it.duration)}"
            }

            setOnCompletionListener {

                if (repeat) {
                    play(current)
                } else {
                    next()
                }
            }

            prepareAsync()
        }
    }

    private fun toggle() {

        val p = player

        if (p == null) {

            if (songs.isNotEmpty()) {
                play(0)
            }

        } else if (p.isPlaying) {

            p.pause()
            playButton.text = "▶"

        } else {

            p.start()
            playButton.text = "❚❚"
        }
    }

    private fun next() {

        if (songs.isEmpty()) return

        val nextIndex = if (shuffle) {
            songs.indices.random()
        } else {
            (current + 1) % songs.size
        }

        play(nextIndex)
    }

    private fun previous() {

        if (songs.isEmpty()) return

        val previousIndex = if (current <= 0) {
            songs.lastIndex
        } else {
            current - 1
        }

        play(previousIndex)
    }

    private fun fmt(ms: Long): String {

        val s = ms / 1000

        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    override fun onDestroy() {

        handler.removeCallbacks(tick)

        player?.release()
        player = null

        super.onDestroy()
    }
}
