package com.canlitv.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

data class Channel(val number: Int, val name: String, val logoUrl: String, val streamUrl: String)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelPanel: View
    private lateinit var numPadPanel: View
    private lateinit var infoBanner: View
    private lateinit var linkDialogPanel: View
    private lateinit var etM3uUrl: EditText
    private lateinit var btnSaveUrl: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTotalChannels: TextView
    private lateinit var tvNumpadDisplay: TextView
    private lateinit var tvBannerNumber: TextView
    private lateinit var tvBannerName: TextView
    private lateinit var imgBannerLogo: ImageView

    private val channelList = mutableListOf<Channel>()
    private var currentChannelIndex = 0
    private val digitInputBuffer = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var bannerRunnable: Runnable? = null
    private var digitAutoExecuteRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        channelPanel = findViewById(R.id.channelPanel)
        numPadPanel = findViewById(R.id.numPadPanel)
        infoBanner = findViewById(R.id.infoBanner)
        linkDialogPanel = findViewById(R.id.linkDialogPanel)
        etM3uUrl = findViewById(R.id.etM3uUrl)
        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        recyclerView = findViewById(R.id.recyclerViewChannels)
        tvTotalChannels = findViewById(R.id.tvTotalChannels)
        tvNumpadDisplay = findViewById(R.id.tvNumpadDisplay)
        tvBannerNumber = findViewById(R.id.tvBannerNumber)
        tvBannerName = findViewById(R.id.tvBannerName)
        imgBannerLogo = findViewById(R.id.imgBannerLogo)

        initNumpadButtons()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0")
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

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(applicationContext, "Yayım xətası", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(applicationContext, "Düzgün link daxil edin", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initNumpadButtons() {
        val buttons = mapOf(
            R.id.btn0 to 0, R.id.btn1 to 1, R.id.btn2 to 2, R.id.btn3 to 3,
            R.id.btn4 to 4, R.id.btn5 to 5, R.id.btn6 to 6, R.id.btn7 to 7,
            R.id.btn8 to 8, R.id.btn9 to 9
        )
        for ((id, digit) in buttons) {
            findViewById<Button>(id).setOnClickListener { appendNumpadDigit(digit) }
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
