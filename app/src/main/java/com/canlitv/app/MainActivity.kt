package com.canlitv.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.*
import java.io.IOException

data class Channel(val name: String, val logoUrl: String, val streamUrl: String)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelPanel: View
    private lateinit var linkDialogPanel: View
    private lateinit var etM3uUrl: EditText
    private lateinit var btnSaveUrl: Button
    private lateinit var recyclerView: RecyclerView
    private val channelList = mutableListOf<Channel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        channelPanel = findViewById(R.id.channelPanel)
        linkDialogPanel = findViewById(R.id.linkDialogPanel)
        etM3uUrl = findViewById(R.id.etM3uUrl)
        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        recyclerView = findViewById(R.id.recyclerViewChannels)

        // Bütün HTTP/HTTPS və IPTV axınlarını qəbul edən xüsusi şəbəkə konfiqurasiyası
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        playerView.player = player
        playerView.useController = false

        // Xəta və ya yüklənmə vəziyyətini izləmək
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@MainActivity, "Kanal açılmadı: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("M3U_URL", "") ?: ""

        if (savedUrl.isNotEmpty()) {
            fetchM3U(savedUrl)
        } else {
            showLinkDialog()
        }

        btnSaveUrl.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                prefs.edit().putString("M3U_URL", url).apply()
                linkDialogPanel.visibility = View.GONE
                fetchM3U(url)
            } else {
                Toast.makeText(this, "Düzgün m3u linki daxil edin!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLinkDialog() {
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        etM3uUrl.setText(prefs.getString("M3U_URL", ""))
        linkDialogPanel.visibility = View.VISIBLE
        etM3uUrl.requestFocus()
    }

    private fun fetchM3U(url: String) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Pleylist yüklə
