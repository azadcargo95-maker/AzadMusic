package com.azad.music

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    data class Song(val id: Long, val title: String, val artist: String, val album: String, val uri: Uri, val duration: Long)

    private val songs = mutableListOf<Song>()
    private var shownSongs = listOf<Song>()
    private var player: MediaPlayer? = null
    private var current = -1
    private var shuffle = false
    private var repeat = false
    private var activeTab = "Songs"
    private lateinit var songList: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var playButton: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var search: EditText
    private lateinit var tabs: LinearLayout
    private lateinit var miniPlayer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && current in songs.indices) {
                try {
                    if (p.isPlaying) {
                        seek.progress = p.currentPosition
                        timeText.text = "${formatTime(p.currentPosition.toLong())}  /  ${formatTime(p.duration.toLong())}"
                    }
                } catch (_: Exception) {}
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        askPermission()
        handler.post(tick)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(hex: String) = Color.parseColor(hex)
    private fun rounded(bg: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bg); cornerRadius = dp(radius).toFloat()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
            setBackgroundColor(color("#101116"))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "AZAD MUSIC"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); letterSpacing = 0.04f
        }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = textButton("↻", 26, "#A78BFA")
        refresh.setOnClickListener { loadSongs() }
        top.addView(refresh)
        root.addView(top)

        val tagline = TextView(this).apply {
            text = "Your music. Your moment."; textSize = 13f
            setTextColor(color("#A6A3B2")); setPadding(0, dp(5), 0, dp(16))
        }
        root.addView(tagline)

        search = EditText(this).apply {
            hint = "Search songs, artists, albums"
            setHintTextColor(color("#85818F")); setTextColor(Color.WHITE)
            textSize = 14f; setSingleLine(true)
            setPadding(dp(16), dp(4), dp(12), dp(4))
            background = rounded(color("#24222B"), 16)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderList() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        tabs = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(12)) }
        root.addView(tabs)
        updateTabs()

        val scroll = ScrollView(this).apply { isFillViewport = false }
        songList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(songList)
        root.addView(scroll, LinearLayout.LayoutParams(0, 0, 1f))

        miniPlayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = rounded(color("#292632"), 18)
        }
        val songInfo = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val cover = TextView(this).apply {
            text = "♫"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(color("#6D48B8"), 12)
        }
        songInfo.addView(cover, LinearLayout.LayoutParams(dp(48), dp(48)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        titleText = TextView(this).apply { text = "Pick a song"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines = 1 }
        artistText = TextView(this).apply { text = "Your device library"; textSize = 12f; setTextColor(color("#B8B3C4")); maxLines = 1 }
        labels.addView(titleText); labels.addView(artistText)
        songInfo.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        playButton = textButton("▶", 25, "#FFFFFF")
        playButton.setOnClickListener { togglePlayback() }
        songInfo.addView(playButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        miniPlayer.addView(songInfo)

        seek = SeekBar(this).apply { max = 1000; progress = 0 }
        miniPlayer.addView(seek, LinearLayout.LayoutParams(-1, dp(24)))
        timeText = TextView(this).apply { text = "0:00 / 0:00"; textSize = 11f; setTextColor(color("#B8B3C4")) }
        miniPlayer.addView(timeText)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) }
        val shuffleBtn = textButton("⤨", 22, "#C5B6F5")
        shuffleBtn.setOnClickListener { shuffle = !shuffle; shuffleBtn.setTextColor(if (shuffle) color("#C4A8FF") else color("#B8B3C4")) }
        val prevBtn = textButton("⏮", 22, "#FFFFFF"); prevBtn.setOnClickListener { previous() }
        val nextBtn = textButton("⏭", 22, "#FFFFFF"); nextBtn.setOnClickListener { nextSong() }
        val repeatBtn = textButton("⟳", 22, "#B8B3C4")
        repeatBtn.setOnClickListener { repeat = !repeat; repeatBtn.setTextColor(if (repeat) color("#C4A8FF") else color("#B8B3C4")) }
        listOf(shuffleBtn, prevBtn, nextBtn, repeatBtn).forEach { controls.addView(it, LinearLayout.LayoutParams(dp(54), dp(40))) }
        miniPlayer.addView(controls)
        root.addView(miniPlayer, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { player?.let { if (it.duration > 0) it.seekTo((s.progress * it.duration) / 1000) } }
        })
    }

    private fun textButton(label: String, size: Int, tint: String): TextView = TextView(this).apply {
        text = label; textSize = size.toFloat(); gravity = Gravity.CENTER; setTextColor(color(tint))
        setPadding(dp(4), dp(2), dp(4), dp(2))
    }

    private fun updateTabs() {
        tabs.removeAllViews()
        listOf("Songs", "Albums", "Artists").forEach { name ->
            val btn = TextView(this).apply {
                text = name; textSize = 14f; gravity = Gravity.CENTER
                typeface = if (activeTab == name) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (activeTab == name) color("#C4A8FF") else color("#918D9C"))
                setPadding(dp(15), dp(7), dp(15), dp(7))
                setOnClickListener { activeTab = name; updateTabs(); renderList() }
            }
            tabs.addView(btn)
        }
    }

    private fun askPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) loadSongs()
        else requestPermissions(arrayOf(permission), 101)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 101 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs()
        else Toast.makeText(this, "Music access is needed to read your songs.", Toast.LENGTH_LONG).show()
    }

    private fun loadSongs() {
        songs.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor: Cursor? = contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                songs.add(Song(id, it.getString(1) ?: "Unknown title", it.getString(2) ?: "Unknown artist", it.getString(3) ?: "Unknown album", ContentUris.withAppendedId(uri, id), it.getLong(4)))
            }
        }
        renderList()
    }

    private fun renderList() {
        songList.removeAllViews()
        val q = search.text?.toString()?.trim()?.lowercase().orEmpty()
        shownSongs = when (activeTab) {
            "Albums" -> songs.filter { it.album.lowercase().contains(q) || it.artist.lowercase().contains(q) }
            "Artists" -> songs.filter { it.artist.lowercase().contains(q) || it.title.lowercase().contains(q) }
            else -> songs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
        }
        if (activeTab != "Songs") {
            val unique = if (activeTab == "Albums") shownSongs.distinctBy { it.album + it.artist } else shownSongs.distinctBy { it.artist }
            unique.forEach { item ->
                val row = songRow(item, if (activeTab == "Albums") item.album else item.artist, if (activeTab == "Albums") item.artist else "${songs.count { s -> s.artist == item.artist }} tracks")
                row.setOnClickListener { playSong(songs.indexOf(item)) }
                songList.addView(row)
            }
        } else {
            shownSongs.forEach { item ->
                val row = songRow(item, item.artist, "${formatTime(item.duration)}  •  ${item.album}")
                row.setOnClickListener { playSong(songs.indexOf(item)) }
                songList.addView(row)
            }
        }
        if (shownSongs.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (songs.isEmpty()) "No songs found on this device. Add music files and refresh." else "No matches found."
                textSize = 14f; setTextColor(color("#AAA6B5")); gravity = Gravity.CENTER; setPadding(dp(24), dp(40), dp(24), dp(40))
            }
            songList.addView(empty)
        }
    }

    private fun songRow(song: Song, line2: String, line3: String): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(5), dp(10), dp(5), dp(10)) }
        val icon = TextView(this).apply {
            text = "♫"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(color("#C4A8FF"))
            background = rounded(color("#272330"), 12)
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(5), 0) }
        val title = TextView(this).apply { text = song.title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines = 1 }
        val artist = TextView(this).apply { text = line2; textSize = 12f; setTextColor(color("#B4AFBD")); maxLines = 1 }
        val detail = TextView(this).apply { text = line3; textSize = 11f; setTextColor(color("#777381")); maxLines = 1 }
        textCol.addView(title); textCol.addView(artist); textCol.addView(detail)
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        val more = TextView(this).apply { text = "⋮"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(color("#A6A2AF")) }
        row.addView(more, LinearLayout.LayoutParams(dp(32), dp(48)))
        return row
    }

    private fun playSong(index: Int) {
        if (index !in songs.indices) return
        current = index
        player?.release()
        player = MediaPlayer()
        try {
            player?.setDataSource(this, songs[index].uri)
            player?.setOnPreparedListener { mp ->
                mp.start()
                playButton.text = "❚❚"
                seek.progress = 0
                titleText.text = songs[index].title
                artistText.text = songs[index].artist
                timeText.text = "0:00 / ${formatTime(mp.duration.toLong())}"
            }
            player?.setOnCompletionListener { if (repeat) playSong(current) else nextSong() }
            player?.setOnErrorListener { _, _, _ -> Toast.makeText(this, "Cannot play this audio file", Toast.LENGTH_SHORT).show(); true }
            player?.prepareAsync()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play this song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayback() {
        val p = player
        if (p == null) { if (songs.isNotEmpty()) playSong(0) }
        else try {
            if (p.isPlaying) { p.pause(); playButton.text = "▶" }
            else { p.start(); playButton.text = "❚❚" }
        } catch (_: Exception) { if (current in songs.indices) playSong(current) }
    }

    private fun nextSong() {
        if (songs.isEmpty()) return
        val index = if (shuffle && songs.size > 1) (songs.indices.filter { it != current }.random()) else (current + 1) % songs.size
        playSong(index)
    }
    private fun previous() {
        if (songs.isEmpty()) return
        val index = if (current <= 0) songs.lastIndex else current - 1
        playSong(index)
    }
    private fun formatTime(ms: Long): String { val sec = ms / 1000; return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}" }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        player?.release(); player = null
        super.onDestroy()
    }
}
