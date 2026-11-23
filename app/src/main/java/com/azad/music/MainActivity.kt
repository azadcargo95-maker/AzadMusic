package com.azad.music

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var downloadsList: LinearLayout
    private lateinit var downloadManager: DownloadManager
    private val prefs by lazy { getSharedPreferences("azad_downloads", Context.MODE_PRIVATE) }
    private val ids = mutableListOf<Long>()
    private var player: MediaPlayer? = null
    private var playingId: Long? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refreshTask = object : Runnable {
        override fun run() {
            renderDownloads()
            handler.postDelayed(this, 1200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        loadIds()
        buildUi()
        renderDownloads()
        handler.post(refreshTask)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(bg: Int, radius: Int) = GradientDrawable().apply {
        setColor(bg); cornerRadius = dp(radius).toFloat()
    }
    private fun c(hex: String) = Color.parseColor(hex)

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            setBackgroundColor(c("#101116"))
        }
        val header = TextView(this).apply {
            text = "AZAD AUDIO"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); letterSpacing = 0.04f
        }
        root.addView(header)
        val subtitle = TextView(this).apply {
            text = "Download audio you have permission to save"
            textSize = 13f; setTextColor(c("#AAA6B5"))
            setPadding(0, dp(6), 0, dp(18))
        }
        root.addView(subtitle)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(c("#24222D"), 18)
        }
        val label = TextView(this).apply {
            text = "DIRECT AUDIO LINK"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(c("#C4B5FD")); setPadding(0, 0, 0, dp(8))
        }
        card.addView(label)
        urlInput = EditText(this).apply {
            hint = "Paste MP3, M4A, WAV URL…"
            setHintTextColor(c("#777381")); setTextColor(Color.WHITE)
            textSize = 14f; setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(c("#17151E"), 12)
        }
        card.addView(urlInput, LinearLayout.LayoutParams(-1, dp(48)))
        val downloadBtn = TextView(this).apply {
            text = "⬇   DOWNLOAD AUDIO"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(c("#7C3AED"), 14)
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        card.addView(downloadBtn, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        downloadBtn.setOnClickListener { startDownload() }
        root.addView(card)

        val section = TextView(this).apply {
            text = "MY DOWNLOADS"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(c("#C4B5FD")); setPadding(0, dp(22), 0, dp(10))
        }
        root.addView(section)
        val scroll = ScrollView(this)
        downloadsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(downloadsList)
        root.addView(scroll, LinearLayout.LayoutParams(0, 0, 1f))
        val footer = TextView(this).apply {
            text = "Only download files you own or are authorized to save."
            textSize = 11f; setTextColor(c("#777381")); gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(footer)
        setContentView(root)
    }

    private fun startDownload() {
        val text = urlInput.text.toString().trim()
        val uri = try { Uri.parse(text) } catch (_: Exception) { null }
        if (uri == null || (uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) {
            toast("Enter a valid direct HTTP/HTTPS audio link")
            return
        }
        val filename = safeFilename(uri.lastPathSegment ?: ("audio_" + System.currentTimeMillis() + ".mp3"))
        try {
            val request = DownloadManager.Request(uri)
                .setTitle(filename)
                .setDescription("Downloading with Azad Audio")
                .setMimeType("audio/*")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AzadAudio/" + filename)
            val id = downloadManager.enqueue(request)
            ids.add(id)
            saveIds()
            urlInput.setText("")
            renderDownloads()
            toast("Download added")
        } catch (e: Exception) {
            toast("Could not start download: " + (e.message ?: "unknown error"))
        }
    }

    private fun safeFilename(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(100)
        return if (cleaned.isBlank()) "audio_" + System.currentTimeMillis() + ".mp3" else cleaned
    }

    private data class DownloadInfo(val id: Long, val title: String, val status: Int, val downloaded: Long, val total: Long)

    private fun info(id: Long): DownloadInfo? {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Audio"
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadInfo(id, title, status, downloaded, total)
        }
    }

    private fun renderDownloads() {
        downloadsList.removeAllViews()
        if (ids.isEmpty()) {
            downloadsList.addView(TextView(this).apply {
                text = "No downloads yet. Paste a direct audio link above."
                textSize = 14f; setTextColor(c("#8F8B9B")); setPadding(dp(8), dp(18), dp(8), dp(18))
            })
            return
        }
        for (id in ids.toList().reversed()) {
            val data = info(id)
            if (data == null) {
                ids.remove(id); saveIds(); continue
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = rounded(c("#201E28"), 14)
            }
            item.addView(TextView(this).apply {
                text = data.title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            })
            val status = TextView(this).apply {
                textSize = 12f; setTextColor(c("#AAA6B5")); setPadding(0, dp(5), 0, dp(6))
                text = when (data.status) {
                    DownloadManager.STATUS_PENDING -> "Waiting…"
                    DownloadManager.STATUS_RUNNING -> if (data.total > 0) "Downloading " + (data.downloaded * 100 / data.total) + "% • " + formatBytes(data.downloaded) + " / " + formatBytes(data.total) else "Downloading " + formatBytes(data.downloaded)
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    DownloadManager.STATUS_SUCCESSFUL -> "Downloaded • " + formatBytes(data.total)
                    DownloadManager.STATUS_FAILED -> "Download failed (code " + failureReason(id) + ")"
                    else -> "Status unavailable"
                }
            }
            item.addView(status)
            if (data.status == DownloadManager.STATUS_RUNNING && data.total > 0) {
                item.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100; progress = (data.downloaded * 100 / data.total).toInt()
                }, LinearLayout.LayoutParams(-1, dp(4)))
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            if (data.status == DownloadManager.STATUS_SUCCESSFUL) {
                actions.addView(smallButton("▶ PLAY") { playFile(id) })
                actions.addView(smallButton("SHARE") { shareFile(id) })
            } else if (data.status == DownloadManager.STATUS_RUNNING || data.status == DownloadManager.STATUS_PENDING || data.status == DownloadManager.STATUS_PAUSED) {
                actions.addView(smallButton("CANCEL") { downloadManager.remove(id); ids.remove(id); saveIds(); renderDownloads() })
            } else if (data.status == DownloadManager.STATUS_FAILED) {
                actions.addView(smallButton("REMOVE") { downloadManager.remove(id); ids.remove(id); saveIds(); renderDownloads() })
            }
            actions.addView(smallButton("DELETE") { downloadManager.remove(id); ids.remove(id); if (playingId == id) stopPlayer(); saveIds(); renderDownloads() })
            item.addView(actions)
            downloadsList.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
    }

    private fun failureReason(id: Long): Int {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id)) ?: return -1
        cursor.use {
            if (it.moveToFirst()) return it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        }
        return -1
    }

    private fun smallButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(c("#49336B"), 10)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(5); topMargin = dp(5) }
        setOnClickListener { action() }
    }

    private fun playFile(id: Long) {
        val uri = downloadManager.getUriForDownloadedFile(id)
        if (uri == null) { toast("File is not ready"); return }
        stopPlayer()
        try {
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener { it.start(); playingId = id; toast("Playing audio") }
                setOnCompletionListener { stopPlayer() }
                setOnErrorListener { _, _, _ -> stopPlayer(); toast("Unable to play this audio"); true }
                prepareAsync()
            }
        } catch (_: Exception) { toast("Unable to play this file") }
    }

    private fun shareFile(id: Long) {
        val uri = downloadManager.getUriForDownloadedFile(id) ?: run { toast("File not ready"); return }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share audio"))
    }

    private fun stopPlayer() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release(); player = null; playingId = null
    }

    private fun loadIds() {
        val saved = prefs.getString("ids", "") ?: ""
        if (saved.isNotBlank()) saved.split(",").forEach { it.toLongOrNull()?.let(ids::add) }
    }
    private fun saveIds() { prefs.edit().putString("ids", ids.joinToString(",")).apply() }
    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "Size unknown"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        return String.format("%.1f MB", kb / 1024.0)
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        stopPlayer()
        super.onDestroy()
    }
}
