package com.canlitv.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Xml
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class Channel(
    val number: Int,
    val name: String,
    var logoUrl: String,
    val streamUrl: String,
    val category: String,
    val tvgId: String = ""
)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    private lateinit var channelPanel: View
    private lateinit var categoryPanel: View
    private lateinit var numPadPanel: View
    private lateinit var infoBanner: View
    private lateinit var linkDialogPanel: View

    private lateinit var etM3uUrl: EditText
    private lateinit var etEpgUrl: EditText
    private lateinit var btnSaveM3u: Button
    private lateinit var btnRefreshM3u: Button
    private lateinit var btnSaveEpg: Button
    private lateinit var btnRefreshEpg: Button
    private lateinit var btnCloseSettings: Button
    private lateinit var btnOpenSettings: View

    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var tvNumpadDisplay: TextView
    private lateinit var tvBannerNumber: TextView
    private var imgBannerLogo: ImageView? = null
    private lateinit var tvBannerName: TextView
    private lateinit var tvBannerCategory: TextView

    private lateinit var syncStatusCard: LinearLayout
    private lateinit var tvSyncChannels: TextView
    private lateinit var tvSyncLogos: TextView
    private lateinit var tvSyncPrograms: TextView
    private lateinit var tvSyncSpeed: TextView

    private val allChannels = mutableListOf<Channel>()
    private val displayedChannels = mutableListOf<Channel>()
    private val categories = mutableListOf<String>()
    private var selectedCategory = "Все"
    private val favoriteUrls = mutableSetOf<String>()

    private val currentProgramsMap = ConcurrentHashMap<String, String>()
    private val epgLogosMap = ConcurrentHashMap<String, String>()

    private var currentPlayingIndex = -1
    private var previousPlayingIndex = -1

    private var backPressCount = 0
    private var resetBackPressRunnable: Runnable? = null

    private var okPressCount = 0
    private var resetOkPressRunnable: Runnable? = null

    private lateinit var rowDefaultBg: GradientDrawable
    private lateinit var rowFocusedBg: GradientDrawable
    private lateinit var defaultBtnBg: GradientDrawable
    private lateinit var focusedBtnBg: GradientDrawable

    private val handler = Handler(Looper.getMainLooper())
    private val digitInputBuffer = StringBuilder()
    private var bannerRunnable: Runnable? = null
    private var digitAutoExecuteRunnable: Runnable? = null

    @Volatile
    private var isM3uLoading = false
    @Volatile
    private var isEpgLoading = false
    private val loadingLock = Any()

    // Увеличенные сетевые таймауты для надежного скачивания больших EPG файлов
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var detectedTimeZone: TimeZone = TimeZone.getDefault()
    private lateinit var bandwidthMeter: DefaultBandwidthMeter

    private val bufferingWatchdog = Runnable {
        if (player?.playbackState == Player.STATE_BUFFERING && !isFinishing) {
            player?.seekToDefaultPosition()
            player?.prepare()
            player?.play()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_main)

        bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        rowDefaultBg = GradientDrawable().apply {
            setColor(Color.parseColor("#33151A22"))
            cornerRadius = 12f
        }
        rowFocusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#4D2979FF"))
            cornerRadius = 12f
            setStroke(2, Color.parseColor("#B3FFFFFF"))
        }

        defaultBtnBg = GradientDrawable().apply {
            setColor(Color.parseColor("#551C222E"))
            cornerRadius = 12f
            setStroke(2, Color.parseColor("#40FFFFFF"))
        }
        focusedBtnBg = GradientDrawable().apply {
            setColor(Color.parseColor("#2979FF"))
            cornerRadius = 12f
            setStroke(2, Color.parseColor("#FFFFFF"))
        }

        playerView = findViewById(R.id.playerView)
        channelPanel = findViewById(R.id.channelPanel)
        categoryPanel = findViewById(R.id.categoryPanel)
        numPadPanel = findViewById(R.id.numPadPanel)
        infoBanner = findViewById(R.id.infoBanner)
        linkDialogPanel = findViewById(R.id.linkDialogPanel)

        etM3uUrl = findViewById(R.id.etM3uUrl)
        etEpgUrl = findViewById(R.id.etEpgUrl)
        btnSaveM3u = findViewById(R.id.btnSaveM3u)
        btnRefreshM3u = findViewById(R.id.btnRefreshM3u)
        btnSaveEpg = findViewById(R.id.btnSaveEpg)
        btnRefreshEpg = findViewById(R.id.btnRefreshEpg)
        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        recyclerViewChannels = findViewById(R.id.recyclerViewChannels)
        recyclerViewCategories = findViewById(R.id.recyclerViewCategories)
        tvNumpadDisplay = findViewById(R.id.tvNumpadDisplay)
        tvBannerNumber = findViewById(R.id.tvBannerNumber)
        imgBannerLogo = findViewById(R.id.imgBannerLogo)
        tvBannerName = findViewById(R.id.tvBannerName)
        tvBannerCategory = findViewById(R.id.tvBannerCategory)

        syncStatusCard = findViewById(R.id.syncStatusCard)
        tvSyncChannels = findViewById(R.id.tvSyncChannels)
        tvSyncLogos = findViewById(R.id.tvSyncLogos)
        tvSyncPrograms = findViewById(R.id.tvSyncPrograms)

        val speedFromId = findViewById<TextView?>(R.id.tvSyncSpeed)
        if (speedFromId != null) {
            tvSyncSpeed = speedFromId
        } else {
            tvSyncSpeed = TextView(this).apply {
                text = "Скорость: 0.00 Мбит/с"
                setTextColor(Color.parseColor("#00E676"))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            syncStatusCard.addView(tvSyncSpeed)
        }

        syncStatusCard.background = GradientDrawable().apply {
            setColor(Color.parseColor("#880A0E17"))
            cornerRadius = 12f
            setStroke(1, Color.parseColor("#40FFFFFF"))
        }
        syncStatusCard.visibility = View.GONE

        infoBanner.background = GradientDrawable().apply {
            setColor(Color.parseColor("#E60B0F19"))
            cornerRadius = 16f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }

        numPadPanel.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F00B0F19"))
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#40FFFFFF"))
        }

        findViewById<View?>(R.id.settingsContainerBox)?.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F7121722"))
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#40FFFFFF"))
        }

        detectTimezoneByIp()
        loadFavorites()
        initNumpadButtons()
        initSettingsPanelFocus()

        btnOpenSettings.background = defaultBtnBg
        btnOpenSettings.setOnClickListener {
            closeAllPanels()
            showSettingsDialog()
        }

        btnOpenSettings.setOnFocusChangeListener { _, hasFocus ->
            val layout = btnOpenSettings as? LinearLayout
            val icon = layout?.getChildAt(0) as? TextView
            val text = layout?.getChildAt(1) as? TextView
            if (hasFocus) {
                btnOpenSettings.background = focusedBtnBg
                icon?.setTextColor(Color.WHITE)
                text?.setTextColor(Color.WHITE)
            } else {
                btnOpenSettings.background = defaultBtnBg
                icon?.setTextColor(Color.parseColor("#B0BEC5"))
                text?.setTextColor(Color.parseColor("#ECEFF1"))
            }
        }

        btnOpenSettings.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    btnOpenSettings.performClick()
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    val catIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
                    recyclerViewCategories.post {
                        recyclerViewCategories.scrollToPosition(catIndex)
                        val vh = recyclerViewCategories.findViewHolderForAdapterPosition(catIndex)
                        if (vh != null) vh.itemView.requestFocus() else recyclerViewCategories.requestFocus()
                    }
                    return@setOnKeyListener true
                }
            }
            false
        }

        initPlayer()

        recyclerViewChannels.layoutManager = LinearLayoutManager(this)
        recyclerViewChannels.setHasFixedSize(true)
        recyclerViewChannels.setItemViewCacheSize(20)
        recyclerViewChannels.itemAnimator = null

        recyclerViewChannels.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (!isFinishing && !isDestroyed) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        Glide.with(this@MainActivity).resumeRequests()
                    } else {
                        Glide.with(this@MainActivity).pauseRequests()
                    }
                }
            }
        })

        recyclerViewCategories.layoutManager = LinearLayoutManager(this)
        recyclerViewCategories.setHasFixedSize(true)
        recyclerViewCategories.itemAnimator = null

        btnSaveM3u.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                prefs.edit().putString("M3U_URL", url).apply()
                closeAllPanels()
                currentPlayingIndex = -1
                fetchM3U(url, autoPlay = true)
            } else {
                Toast.makeText(applicationContext, "Введите правильный URL M3U", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshM3u.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                closeAllPanels()
                fetchM3U(url, autoPlay = false)
            } else {
                Toast.makeText(applicationContext, "Ссылка M3U не введена", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                prefs.edit().putString("EPG_URL", epgUrl).apply()
                closeAllPanels()
                deleteLocalEpgCache()
                downloadAndParseEpg(epgUrl)
            } else {
                Toast.makeText(applicationContext, "Введите правильный URL EPG", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                closeAllPanels()
                deleteLocalEpgCache()
                downloadAndParseEpg(epgUrl)
            } else {
                Toast.makeText(applicationContext, "Ссылка EPG не введена", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseSettings.setOnClickListener {
            linkDialogPanel.visibility = View.GONE
        }
    }

    private fun closeAllPanels() {
        categoryPanel.visibility = View.GONE
        channelPanel.visibility = View.GONE
        numPadPanel.visibility = View.GONE
        linkDialogPanel.visibility = View.GONE
        infoBanner.visibility = View.GONE
        bannerRunnable?.let { handler.removeCallbacks(it) }
        digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
        digitInputBuffer.clear()
        if (!isFinishing && !isDestroyed) {
            Glide.with(this).resumeRequests()
        }
    }

    private fun detectTimezoneByIp() {
        Thread {
            try {
                val req = Request.Builder().url("http://ip-api.com/json/").build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful && body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val tzStr = json.optString("timezone")
                    if (tzStr.isNotEmpty()) {
                        detectedTimeZone = TimeZone.getTimeZone(tzStr)
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun initPlayer() {
        if (player == null) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
                .setTransferListener(bandwidthMeter)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000,
                    50000,
                    2500,
                    5000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                .setEnableDecoderFallback(true)

            player = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter)
                .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                .build()

            player?.repeatMode = Player.REPEAT_MODE_OFF

            playerView.player = player
            playerView.useController = false

            bandwidthMeter.addEventListener(handler) { _, _, bitrate ->
                if (syncStatusCard.visibility == View.VISIBLE) {
                    val speedMbps = bitrate / 1_000_000.0
                    val speedKbps = bitrate / 1_000.0
                    tvSyncSpeed.text = if (speedMbps >= 1.0) {
                        String.format(Locale.US, "Скорость: %.2f Мбит/с", speedMbps)
                    } else {
                        String.format(Locale.US, "Скорость: %.0f кбит/с", speedKbps)
                    }
                }
            }

            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        player?.seekToDefaultPosition()
                        player?.prepare()
                        player?.play()
                    } else {
                        player?.stop()
                        handler.postDelayed({
                            if (!isFinishing && currentPlayingIndex in allChannels.indices) {
                                val currentChannel = allChannels[currentPlayingIndex]
                                player?.setMediaItem(MediaItem.fromUri(Uri.parse(currentChannel.streamUrl)))
                                player?.prepare()
                                player?.playWhenReady = true
                            }
                        }, 2000)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        handler.removeCallbacks(bufferingWatchdog)
                        handler.postDelayed(bufferingWatchdog, 10000)
                    } else {
                        handler.removeCallbacks(bufferingWatchdog)
                    }
                }
            })
        }
    }

    private fun playChannelByIndex(channelIndex: Int) {
        if (channelIndex !in allChannels.indices) return

        if (currentPlayingIndex != -1 && currentPlayingIndex != channelIndex) {
            previousPlayingIndex = currentPlayingIndex
        }

        currentPlayingIndex = channelIndex
        val channel = allChannels[channelIndex]

        getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
            .edit()
            .putString("LAST_CHANNEL_URL", channel.streamUrl)
            .apply()

        initPlayer()

        handler.removeCallbacks(bufferingWatchdog)
        player?.stop()
        player?.clearMediaItems()
        player?.setMediaItem(MediaItem.fromUri(Uri.parse(channel.streamUrl)))
        player?.prepare()
        player?.playWhenReady = true

        showInfoBanner(channel)
    }

    private fun initSettingsPanelFocus() {
        val inputs = listOf(etM3uUrl, etEpgUrl)
        for (input in inputs) {
            input.backgroundTintList = null
            val defaultEditBg = GradientDrawable().apply {
                setColor(Color.parseColor("#0B0E17"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#44FFFFFF"))
            }
            val focusedEditBg = GradientDrawable().apply {
                setColor(Color.parseColor("#0B0E17"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#2979FF"))
            }
            input.background = defaultEditBg
            input.setTextColor(Color.WHITE)
            input.setHintTextColor(Color.parseColor("#78909C"))
            input.setOnFocusChangeListener { _, hasFocus ->
                input.background = if (hasFocus) focusedEditBg else defaultEditBg
            }
        }

        val dialogButtons = listOf(btnSaveM3u, btnRefreshM3u, btnSaveEpg, btnRefreshEpg, btnCloseSettings)
        for (btn in dialogButtons) {
            btn.backgroundTintList = null
            btn.background = defaultBtnBg
            btn.setTextColor(Color.parseColor("#ECEFF1"))
            btn.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    btn.background = focusedBtnBg
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.background = defaultBtnBg
                    btn.setTextColor(Color.parseColor("#ECEFF1"))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        initPlayer()

        if (allChannels.isEmpty()) {
            val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
            val savedM3u = prefs.getString("M3U_URL", "") ?: ""

            if (savedM3u.isNotEmpty()) {
                fetchM3U(savedM3u, autoPlay = true)
            } else {
                showSettingsDialog()
            }
        } else {
            player?.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(bufferingWatchdog)
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(bufferingWatchdog)
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(bufferingWatchdog)
        player?.release()
        player = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun loadFavorites() {
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        favoriteUrls.clear()
        favoriteUrls.addAll(prefs.getStringSet("FAVORITES", emptySet()) ?: emptySet())
    }

    private fun saveFavorites() {
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("FAVORITES", HashSet(favoriteUrls)).apply()
    }

    private fun toggleFavorite(channel: Channel) {
        val currentFocusPos = displayedChannels.indexOf(channel)
        if (favoriteUrls.contains(channel.streamUrl)) {
            favoriteUrls.remove(channel.streamUrl)
        } else {
            favoriteUrls.add(channel.streamUrl)
        }
        saveFavorites()
        if (selectedCategory == "Избранное") {
            filterChannels("Избранное")
        } else if (currentFocusPos != -1) {
            recyclerViewChannels.adapter?.notifyItemChanged(currentFocusPos)
        }
    }

    private fun initNumpadButtons() {
        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
            R.id.btn8, R.id.btn9, R.id.btnClear, R.id.btnGo
        )

        for (id in buttonIds) {
            val btn = findViewById<Button>(id) ?: continue
            btn.backgroundTintList = null
            btn.background = defaultBtnBg
            btn.setTextColor(Color.parseColor("#ECEFF1"))
            btn.setOnFocusChangeListener { _, hasFocus ->
                btn.background = if (hasFocus) focusedBtnBg else defaultBtnBg
                btn.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#ECEFF1"))
            }
        }

        val buttons = mapOf(
            R.id.btn0 to 0, R.id.btn1 to 1, R.id.btn2 to 2, R.id.btn3 to 3,
            R.id.btn4 to 4, R.id.btn5 to 5, R.id.btn6 to 6, R.id.btn7 to 7,
            R.id.btn8 to 8, R.id.btn9 to 9
        )
        for ((id, digit) in buttons) {
            findViewById<Button>(id)?.setOnClickListener { appendNumpadDigit(digit) }
        }
        findViewById<Button>(R.id.btnClear)?.setOnClickListener {
            digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
            digitInputBuffer.clear()
            tvNumpadDisplay.text = "---"
        }
        findViewById<Button>(R.id.btnGo)?.setOnClickListener { executeNumpadSearch() }
    }

    private fun showSettingsDialog() {
        closeAllPanels()
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        etM3uUrl.setText(prefs.getString("M3U_URL", ""))
        etEpgUrl.setText(prefs.getString("EPG_URL", ""))
        linkDialogPanel.visibility = View.VISIBLE
        btnSaveM3u.post { btnSaveM3u.requestFocus() }
    }

    private fun fetchM3U(url: String, autoPlay: Boolean) {
        synchronized(loadingLock) {
            if (isM3uLoading) return
            isM3uLoading = true
        }

        runOnUiThread { 
            tvSyncChannels.text = "Каналы: Загрузка..."
            tvSyncLogos.text = "Логотипы: 0"
        }

        val request = try {
            Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
                .build()
        } catch (_: Exception) {
            synchronized(loadingLock) { isM3uLoading = false }
            runOnUiThread {
                tvSyncChannels.text = "Каналы: Ошибка ссылки"
                if (allChannels.isEmpty()) showSettingsDialog()
            }
            return
        }

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                synchronized(loadingLock) { isM3uLoading = false }
                runOnUiThread {
                    tvSyncChannels.text = "Каналы: Ошибка загрузки"
                    if (allChannels.isEmpty() && !isFinishing) showSettingsDialog()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful || response.body == null) {
                    response.close()
                    synchronized(loadingLock) { isM3uLoading = false }
                    runOnUiThread {
                        tvSyncChannels.text = "Каналы: Пустой ответ"
                        if (allChannels.isEmpty() && !isFinishing) showSettingsDialog()
                    }
                    return
                }

                Thread {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                        val lastPlayedUrl = if (currentPlayingIndex in allChannels.indices) {
                            allChannels[currentPlayingIndex].streamUrl
                        } else {
                            prefs.getString("LAST_CHANNEL_URL", "") ?: ""
                        }

                        response.body!!.byteStream().use { stream ->
                            parseM3UStream(stream)
                        }
                        applyEpgLogosToChannels()

                        val channelCount = allChannels.size
                        val logosCount = allChannels.count { it.logoUrl.isNotEmpty() }

                        runOnUiThread {
                            synchronized(loadingLock) { isM3uLoading = false }
                            if (isFinishing || isDestroyed) return@runOnUiThread

                            tvSyncChannels.text = "Каналы: $channelCount"
                            tvSyncLogos.text = "Логотипы: $logosCount"

                            setupCategoryAdapter()
                            val catToSelect = if (categories.contains(selectedCategory)) selectedCategory else "Все"
                            filterChannels(catToSelect)

                            if (allChannels.isNotEmpty()) {
                                val resumeIndex = if (lastPlayedUrl.isNotEmpty()) {
                                    allChannels.indexOfFirst { it.streamUrl == lastPlayedUrl }.takeIf { it >= 0 } ?: 0
                                } else 0

                                if (autoPlay || player?.isPlaying != true) {
                                    playChannelByIndex(resumeIndex)
                                } else {
                                    currentPlayingIndex = resumeIndex
                                }

                                val savedEpg = prefs.getString("EPG_URL", "") ?: ""
                                if (savedEpg.startsWith("http")) {
                                    handler.postDelayed({ downloadAndParseEpg(savedEpg) }, 12000)
                                }
                            } else {
                                showSettingsDialog()
                            }
                        }
                    } catch (_: Exception) {
                        synchronized(loadingLock) { isM3uLoading = false }
                        runOnUiThread {
                            tvSyncChannels.text = "Каналы: Ошибка обработки"
                            if (allChannels.isEmpty() && !isFinishing) showSettingsDialog()
                        }
                    }
                }.start()
            }
        })
    }

    private fun parseM3UStream(inputStream: InputStream) {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 32768)
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentTvgId = ""
        var counter = 1

        val tempChannels = ArrayList<Channel>(4000)
        val tempCategories = LinkedHashSet<String>()
        tempCategories.add("Все")
        tempCategories.add("Избранное")

        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                    currentLogo = extractAttribute(trimmed, "tvg-logo").ifEmpty { extractAttribute(trimmed, "logo") }
                    currentTvgId = extractAttribute(trimmed, "tvg-id").ifEmpty { extractAttribute(trimmed, "tvg-name") }
                    val grp = extractAttribute(trimmed, "group-title")
                    if (grp.isNotEmpty()) {
                        currentGroup = grp
                        tempCategories.add(currentGroup)
                    }
                    val commaIndex = trimmed.lastIndexOf(',')
                    currentName = if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
                        trimmed.substring(commaIndex + 1).trim()
                    } else {
                        "Канал $counter"
                    }
                } else if (trimmed.startsWith("#EXTGRP:", ignoreCase = true)) {
                    val grp = trimmed.substringAfter(":").trim()
                    if (grp.isNotEmpty()) {
                        currentGroup = grp
                        tempCategories.add(currentGroup)
                    }
                } else if (!trimmed.startsWith("#") && (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("rtmp://", ignoreCase = true) || trimmed.startsWith("udp://", ignoreCase = true))) {
                    val name = if (currentName.isNotEmpty()) currentName else "Канал $counter"
                    val cat = if (currentGroup.isNotEmpty()) currentGroup else "Другие"
                    tempCategories.add(cat)
                    tempChannels.add(
                        Channel(
                            number = counter,
                            name = name,
                            logoUrl = currentLogo,
                            streamUrl = trimmed,
                            category = cat,
                            tvgId = currentTvgId
                        )
                    )
                    counter++
                    currentName = ""
                    currentLogo = ""
                    currentGroup = ""
                    currentTvgId = ""
                }
            }
            line = reader.readLine()
        }

        allChannels.clear()
        allChannels.addAll(tempChannels)
        categories.clear()
        categories.addAll(tempCategories)
    }

    private fun extractAttribute(line: String, attr: String): String {
        val searchKey = "$attr="
        val startIdx = line.indexOf(searchKey, ignoreCase = true)
        if (startIdx == -1) return ""
        var valStart = startIdx + searchKey.length
        if (valStart >= line.length) return ""
        val firstChar = line[valStart]
        if (firstChar == '"' || firstChar == '\'') {
            valStart++
            val valEnd = line.indexOf(firstChar, valStart)
            return if (valEnd != -1) line.substring(valStart, valEnd).trim() else ""
        } else {
            var valEnd = line.indexOf(' ', valStart)
            val commaEnd = line.indexOf(',', valStart)
            if (commaEnd != -1 && (valEnd == -1 || commaEnd < valEnd)) {
                valEnd = commaEnd
            }
            return if (valEnd != -1) line.substring(valStart, valEnd).trim() else line.substring(valStart).trim()
        }
    }

    private fun applyEpgLogosToChannels() {
        if (epgLogosMap.isEmpty() || allChannels.isEmpty()) return
        for (ch in allChannels) {
            if (ch.logoUrl.isEmpty()) {
                if (ch.tvgId.isNotEmpty()) {
                    val l = epgLogosMap[ch.tvgId.lowercase(Locale.ROOT)]
                    if (!l.isNullOrEmpty()) {
                        ch.logoUrl = l
                        continue
                    }
                }
                val norm = normalizeName(ch.name)
                val l2 = epgLogosMap[norm]
                if (!l2.isNullOrEmpty()) {
                    ch.logoUrl = l2
                    continue
                }
                val l3 = epgLogosMap[ch.name.lowercase(Locale.ROOT)]
                if (!l3.isNullOrEmpty()) {
                    ch.logoUrl = l3
                }
            }
        }
    }

    private fun deleteLocalEpgCache() {
        try {
            val cacheFile = File(cacheDir, "epg_cache.gz")
            if (cacheFile.exists()) cacheFile.delete()
        } catch (_: Exception) {}
    }

    private fun downloadAndParseEpg(epgUrl: String) {
        synchronized(loadingLock) {
            if (isEpgLoading) return
            isEpgLoading = true
        }

        val cacheFile = File(cacheDir, "epg_cache.gz")
        if (cacheFile.exists() && cacheFile.length() > 0 &&
            System.currentTimeMillis() - cacheFile.lastModified() < 8 * 3600 * 1000L
        ) {
            Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                parseEpgLiveStream(cacheFile)
            }.start()
            return
        }

        runOnUiThread { tvSyncPrograms.text = "Программы: Скачивание..." }

        val request = try {
            Request.Builder()
                .url(epgUrl)
                .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
                .header("Accept-Encoding", "gzip, deflate")
                .build()
        } catch (_: Exception) {
            synchronized(loadingLock) { isEpgLoading = false }
            runOnUiThread { tvSyncPrograms.text = "Программы: Ошибка ссылки" }
            return
        }

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                synchronized(loadingLock) { isEpgLoading = false }
                runOnUiThread { tvSyncPrograms.text = "Программы: Ошибка сети" }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    response.close()
                    synchronized(loadingLock) { isEpgLoading = false }
                    runOnUiThread { tvSyncPrograms.text = "Программы: Ошибка ответа" }
                    return
                }

                Thread {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                    try {
                        val tempFile = File(cacheDir, "epg_cache.tmp")
                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var readBytes: Int
                                while (input.read(buffer).also { readBytes = it } != -1) {
                                    output.write(buffer, 0, readBytes)
                                }
                            }
                        }
                        if (tempFile.length() > 0) {
                            if (cacheFile.exists()) cacheFile.delete()
                            tempFile.renameTo(cacheFile)
                            parseEpgLiveStream(cacheFile)
                        } else {
                            throw IOException("Пустой EPG")
                        }
                    } catch (_: Exception) {
                        synchronized(loadingLock) { isEpgLoading = false }
                        runOnUiThread { tvSyncPrograms.text = "Программы: Ошибка файла" }
                    }
                }.start()
            }
        })
    }

    private fun parseEpgLiveStream(file: File) {
        try {
            runOnUiThread { tvSyncPrograms.text = "Программы: 0" }

            val matchedChannelMap = HashMap<String, String>(allChannels.size * 3)
            for (ch in allChannels) {
                val norm = normalizeName(ch.name)
                if (norm.isNotEmpty()) matchedChannelMap[norm] = norm
                if (ch.tvgId.isNotEmpty()) {
                    matchedChannelMap[ch.tvgId.lowercase(Locale.ROOT)] = norm
                }
                matchedChannelMap[ch.name.lowercase(Locale.ROOT)] = norm
            }

            val fis = FileInputStream(file)
            val bis = BufferedInputStream(fis, 64 * 1024)
            bis.mark(4)
            val b1 = bis.read()
            val b2 = bis.read()
            bis.reset()

            val inStream: InputStream = if (b1 == 0x1f && b2 == 0x8b) {
                GZIPInputStream(bis, 64 * 1024)
            } else {
                bis
            }

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inStream, "UTF-8")

            val now = System.currentTimeMillis()
            val xmlIdToOurKey = HashMap<String, String>(3000)
            val livePrograms = HashMap<String, String>(allChannels.size)
            val tempEpgLogos = HashMap<String, String>(allChannels.size)

            var currentChannelId = ""
            var inChannel = false
            var inDisplayName = false
            var inTitle = false
            var currentTargetKey: String? = null
            var lastReportedCount = 0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        if (tag.equals("channel", ignoreCase = true)) {
                            inChannel = true
                            currentChannelId = parser.getAttributeValue(null, "id")?.trim()?.lowercase(Locale.ROOT) ?: ""
                            val key = matchedChannelMap[currentChannelId]
                            if (key != null) {
                                xmlIdToOurKey[currentChannelId] = key
                            }
                        } else if (inChannel && tag.equals("display-name", ignoreCase = true)) {
                            inDisplayName = true
                        } else if (inChannel && tag.equals("icon", ignoreCase = true)) {
                            val iconSrc = parser.getAttributeValue(null, "src")?.trim() ?: ""
                            if (iconSrc.isNotEmpty() && currentChannelId.isNotEmpty()) {
                                tempEpgLogos[currentChannelId] = iconSrc
                                xmlIdToOurKey[currentChannelId]?.let { ourKey ->
                                    tempEpgLogos[ourKey] = iconSrc
                                }
                            }
                        } else if (tag.equals("programme", ignoreCase = true)) {
                            val progChannel = parser.getAttributeValue(null, "channel")?.trim()?.lowercase(Locale.ROOT) ?: ""
                            val ourKey = xmlIdToOurKey[progChannel] ?: matchedChannelMap[progChannel]

                            if (ourKey != null && !livePrograms.containsKey(ourKey)) {
                                val startStr = parser.getAttributeValue(null, "start")
                                val stopStr = parser.getAttributeValue(null, "stop")
                                val startMs = parseXmlTvTimeFast(startStr)
                                val stopMs = parseXmlTvTimeFast(stopStr)

                                if (startMs > 0 && stopMs > 0 && now in (startMs - 15 * 60 * 1000L)..(stopMs + 15 * 60 * 1000L)) {
                                    currentTargetKey = ourKey
                                } else {
                                    currentTargetKey = null
                                }
                            } else {
                                currentTargetKey = null
                            }
                        } else if (currentTargetKey != null && tag.equals("title", ignoreCase = true)) {
                            inTitle = true
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (inDisplayName || inTitle) {
                            val text = parser.text?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                if (inDisplayName && currentChannelId.isNotEmpty()) {
                                    val norm = normalizeName(text)
                                    val matched = matchedChannelMap[norm]
                                    if (matched != null) {
                                        xmlIdToOurKey[currentChannelId] = matched
                                        tempEpgLogos[currentChannelId]?.let { logo ->
                                            tempEpgLogos[matched] = logo
                                        }
                                    }
                                } else if (inTitle && currentTargetKey != null) {
                                    livePrograms[currentTargetKey!!] = text
                                    currentTargetKey = null
                                    inTitle = false

                                    val currentCount = livePrograms.size
                                    if (currentCount == 1 || currentCount - lastReportedCount >= 25) {
                                        lastReportedCount = currentCount
                                        runOnUiThread {
                                            tvSyncPrograms.text = "Программы: $currentCount"
                                        }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name
                        if (endTag.equals("display-name", ignoreCase = true)) {
                            inDisplayName = false
                        } else if (endTag.equals("title", ignoreCase = true)) {
                            inTitle = false
                        } else if (endTag.equals("channel", ignoreCase = true)) {
                            inChannel = false
                            currentChannelId = ""
                        } else if (endTag.equals("programme", ignoreCase = true)) {
                            currentTargetKey = null
                            inTitle = false
                        }
                    }
                }
                eventType = parser.next()
            }

            inStream.close()

            if (tempEpgLogos.isNotEmpty()) {
                epgLogosMap.putAll(tempEpgLogos)
                applyEpgLogosToChannels()
            }

            currentProgramsMap.clear()
            currentProgramsMap.putAll(livePrograms)

            runOnUiThread {
                tvSyncPrograms.text = "Программы: ${currentProgramsMap.size}"
                tvSyncLogos.text = "Логотипы: ${allChannels.count { it.logoUrl.isNotEmpty() }}"
                if (channelPanel.visibility == View.VISIBLE) {
                    recyclerViewChannels.adapter?.notifyDataSetChanged()
                }
                if (infoBanner.visibility == View.VISIBLE && currentPlayingIndex in allChannels.indices) {
                    showInfoBanner(allChannels[currentPlayingIndex])
                }
            }
        } catch (_: Exception) {
            runOnUiThread { tvSyncPrograms.text = "Программы: Ошибка" }
        } finally {
            synchronized(loadingLock) { isEpgLoading = false }
        }
    }

    private fun parseXmlTvTimeFast(timeStr: String?): Long {
        if (timeStr == null || timeStr.length < 14) return 0L
        try {
            val year = timeStr.substring(0, 4).toInt()
            val month = timeStr.substring(4, 6).toInt() - 1
            val day = timeStr.substring(6, 8).toInt()
            val hour = timeStr.substring(8, 10).toInt()
            val min = timeStr.substring(10, 12).toInt()
            val sec = timeStr.substring(12, 14).toInt()

            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(year, month, day, hour, min, sec)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            var timeMs = cal.timeInMillis

            val plusIdx = timeStr.indexOf('+', 14)
            val minusIdx = timeStr.indexOf('-', 14)
            val tzIdx = if (plusIdx != -1) plusIdx else minusIdx

            if (tzIdx != -1 && tzIdx + 5 <= timeStr.length) {
                val sign = if (timeStr[tzIdx] == '-') -1 else 1
                val tzHours = timeStr.substring(tzIdx + 1, tzIdx + 3).toIntOrNull() ?: 0
                val tzMins = timeStr.substring(tzIdx + 3, tzIdx + 5).toIntOrNull() ?: 0
                val offsetMs = (tzHours * 3600000L + tzMins * 60000L) * sign
                timeMs -= offsetMs
            } else {
                timeMs -= detectedTimeZone.getOffset(timeMs)
            }
            return timeMs
        } catch (_: Exception) {
            return 0L
        }
    }

    private fun normalizeName(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("""\b(hd|sd|fhd|4k|ru|ua|az|tr|hevc|50fps|orig|original|tv|channel)\b"""), "")
            .replace(Regex("""[^a-z0-9а-яёәғçşöüı]"""), "")
            .trim()
    }

    private fun getProgramForChannel(ch: Channel): String {
        if (currentProgramsMap.isEmpty()) return ""

        val norm = normalizeName(ch.name)
        if (norm.isNotEmpty()) {
            currentProgramsMap[norm]?.let { return it }
        }

        if (ch.tvgId.isNotEmpty()) {
            currentProgramsMap[ch.tvgId.lowercase(Locale.ROOT)]?.let { return it }
        }

        currentProgramsMap[ch.name.lowercase(Locale.ROOT)]?.let { return it }

        return ""
    }

    private fun filterChannels(category: String) {
        selectedCategory = category
        displayedChannels.clear()

        when (category) {
            "Все" -> displayedChannels.addAll(allChannels)
            "Избранное" -> displayedChannels.addAll(allChannels.filter { favoriteUrls.contains(it.streamUrl) })
            else -> displayedChannels.addAll(allChannels.filter { it.category == category })
        }
        setupChannelAdapter()
    }

    private fun openChannelPanelCenteredOnCurrent() {
        closeAllPanels()
        channelPanel.visibility = View.VISIBLE

        if (allChannels.isEmpty() || currentPlayingIndex !in allChannels.indices) {
            filterChannels(selectedCategory)
            recyclerViewChannels.post { recyclerViewChannels.requestFocus() }
            return
        }
        val currentChannel = allChannels[currentPlayingIndex]
        val inCurrentCat = when (selectedCategory) {
            "Все" -> true
            "Избранное" -> favoriteUrls.contains(currentChannel.streamUrl)
            else -> currentChannel.category == selectedCategory
        }
        if (!inCurrentCat) selectedCategory = "Все"
        filterChannels(selectedCategory)
        focusCurrentChannelCentered()
    }

    private fun focusCurrentChannelCentered() {
        if (allChannels.isEmpty() || currentPlayingIndex !in allChannels.indices) {
            recyclerViewChannels.post { recyclerViewChannels.requestFocus() }
            return
        }
        val currentChannel = allChannels[currentPlayingIndex]
        val targetPos = displayedChannels.indexOfFirst { it.number == currentChannel.number }.coerceAtLeast(0)

        recyclerViewChannels.post {
            val layoutManager = recyclerViewChannels.layoutManager as? LinearLayoutManager ?: return@post
            val itemHeightPx = (68 * resources.displayMetrics.density).toInt()
            val centerOffset = (recyclerViewChannels.height / 2) - (itemHeightPx / 2)
            layoutManager.scrollToPositionWithOffset(targetPos, centerOffset)

            recyclerViewChannels.post {
                val holder = recyclerViewChannels.findViewHolderForAdapterPosition(targetPos)
                if (holder != null) holder.itemView.requestFocus() else recyclerViewChannels.requestFocus()
            }
        }
    }

    private fun setupCategoryAdapter() {
        recyclerViewCategories.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 6)
                    }
                    setPadding(18, 14, 18, 14)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    isFocusable = true
                    isClickable = true
                    background = rowDefaultBg
                }
                val iconView = TextView(this@MainActivity).apply {
                    id = View.generateViewId()
                    textSize = 18f
                }
                val titleView = TextView(this@MainActivity).apply {
                    id = View.generateViewId()
                    textSize = 15f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 14
                    }
                }
                val arrowView = TextView(this@MainActivity).apply {
                    id = View.generateViewId()
                    text = "›"
                    textSize = 18f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                row.addView(iconView)
                row.addView(titleView)
                row.addView(arrowView)
                return object : RecyclerView.ViewHolder(row) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val cat = categories[position]
                val row = holder.itemView as LinearLayout
                val iconView = row.getChildAt(0) as TextView
                val titleView = row.getChildAt(1) as TextView
                val arrowView = row.getChildAt(2) as TextView

                titleView.text = cat
                iconView.text = when (cat) {
                    "Все" -> "☰"
                    "Избранное" -> "★"
                    else -> "•"
                }

                row.background = if (row.hasFocus()) rowFocusedBg else rowDefaultBg
                titleView.setTextColor(Color.parseColor("#ECEFF1"))
                iconView.setTextColor(Color.parseColor("#90A4AE"))
                arrowView.setTextColor(Color.parseColor("#90A4AE"))

                row.setOnFocusChangeListener { _, hasFocus ->
                    row.background = if (hasFocus) rowFocusedBg else rowDefaultBg
                    val color = if (hasFocus) Color.WHITE else Color.parseColor("#ECEFF1")
                    val subColor = if (hasFocus) Color.WHITE else Color.parseColor("#90A4AE")
                    titleView.setTextColor(color)
                    iconView.setTextColor(subColor)
                    arrowView.setTextColor(subColor)
                }

                val selectAction = {
                    filterChannels(cat)
                    categoryPanel.visibility = View.GONE
                    channelPanel.visibility = View.VISIBLE
                    recyclerViewChannels.post {
                        recyclerViewChannels.scrollToPosition(0)
                        val vh = recyclerViewChannels.findViewHolderForAdapterPosition(0)
                        if (vh != null) vh.itemView.requestFocus() else recyclerViewChannels.requestFocus()
                    }
                }

                row.setOnClickListener { selectAction() }

                row.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            btnOpenSettings.requestFocus()
                            return@setOnKeyListener true
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                            selectAction()
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
            }

            override fun getItemCount() = categories.size
        }
    }

    private fun setupChannelAdapter() {
        recyclerViewChannels.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(layoutInflater.inflate(R.layout.item_channel, parent, false)) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ch = displayedChannels[position]
                val row = holder.itemView.findViewById<LinearLayout>(R.id.channelRowLayout)
                val tvNum = holder.itemView.findViewById<TextView>(R.id.tvNumber)
                val imgLogo = holder.itemView.findViewById<ImageView>(R.id.imgLogo)
                val tvName = holder.itemView.findViewById<TextView>(R.id.tvName)
                val tvProg = holder.itemView.findViewById<TextView>(R.id.tvProgramInfo)
                val tvFavStar = holder.itemView.findViewById<TextView>(R.id.tvFavStar)

                tvNum.text = ch.number.toString()
                tvName.text = ch.name

                val currentProg = getProgramForChannel(ch)
                if (currentProg.isNotEmpty()) {
                    tvProg?.text = currentProg
                    tvProg?.visibility = View.VISIBLE
                } else {
                    tvProg?.text = ""
                    tvProg?.visibility = View.GONE
                }

                tvFavStar?.visibility = if (favoriteUrls.contains(ch.streamUrl)) View.VISIBLE else View.GONE

                val finalLogoUrl = ch.logoUrl.ifEmpty {
                    if (ch.tvgId.isNotEmpty()) epgLogosMap[ch.tvgId.lowercase(Locale.ROOT)] ?: "" else ""
                }.ifEmpty {
                    epgLogosMap[normalizeName(ch.name)] ?: ""
                }

                if (finalLogoUrl.isNotEmpty()) {
                    imgLogo.visibility = View.VISIBLE
                    Glide.with(this@MainActivity)
                        .load(finalLogoUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(60, 60)
                        .dontAnimate()
                        .timeout(2500)
                        .into(imgLogo)
                } else {
                    val safeLogoView: View = imgLogo
                    Glide.with(this@MainActivity).clear(safeLogoView)
                    imgLogo.setImageDrawable(null)
                    imgLogo.visibility = View.GONE
                }

                row.background = if (row.hasFocus()) rowFocusedBg else rowDefaultBg

                row.setOnFocusChangeListener { _, hasFocus ->
                    row.background = if (hasFocus) rowFocusedBg else rowDefaultBg
                    tvNum.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#B0BEC5"))
                    tvName.setTextColor(Color.WHITE)
                }

                row.setOnClickListener {
                    val allIndex = allChannels.indexOf(ch)
                    if (allIndex != -1) {
                        closeAllPanels()
                        playChannelByIndex(allIndex)
                    }
                }

                row.setOnLongClickListener {
                    toggleFavorite(ch)
                    true
                }

                row.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0 && displayedChannels.size > 1) {
                            val lastIndex = displayedChannels.size - 1
                            recyclerViewChannels.scrollToPosition(lastIndex)
                            recyclerViewChannels.post {
                                recyclerViewChannels.findViewHolderForAdapterPosition(lastIndex)?.itemView?.requestFocus()
                            }
                            return@setOnKeyListener true
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && position == displayedChannels.size - 1 && displayedChannels.size > 1) {
                            recyclerViewChannels.scrollToPosition(0)
                            recyclerViewChannels.post {
                                recyclerViewChannels.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                            }
                            return@setOnKeyListener true
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            channelPanel.visibility = View.GONE
                            categoryPanel.visibility = View.VISIBLE
                            val catIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
                            recyclerViewCategories.post {
                                recyclerViewCategories.scrollToPosition(catIndex)
                                val vh = recyclerViewCategories.findViewHolderForAdapterPosition(catIndex)
                                if (vh != null) vh.itemView.requestFocus() else recyclerViewCategories.requestFocus()
                            }
                            return@setOnKeyListener true
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            closeAllPanels()
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
            }

            override fun getItemCount() = displayedChannels.size
        }
    }

    private fun stepChannelInCurrentCategory(step: Int) {
        val list = if (displayedChannels.isNotEmpty()) displayedChannels else allChannels
        if (list.isEmpty()) return

        val currentPlayingChannel = if (currentPlayingIndex in allChannels.indices) allChannels[currentPlayingIndex] else null
        val currentPosInDisplayed = list.indexOfFirst { it.streamUrl == currentPlayingChannel?.streamUrl }
        val nextPos = if (currentPosInDisplayed != -1) {
            (currentPosInDisplayed + step + list.size) % list.size
        } else {
            0
        }

        val targetChannel = list[nextPos]
        val targetIndexInAll = allChannels.indexOf(targetChannel)
        if (targetIndexInAll != -1) {
            playChannelByIndex(targetIndexInAll)
        }
    }

    private fun showInfoBanner(channel: Channel) {
        if (categoryPanel.visibility == View.VISIBLE ||
            channelPanel.visibility == View.VISIBLE ||
            numPadPanel.visibility == View.VISIBLE ||
            linkDialogPanel.visibility == View.VISIBLE
        ) {
            infoBanner.visibility = View.GONE
            return
        }

        tvBannerNumber.text = channel.number.toString()
        tvBannerName.text = channel.name
        val isFav = if (favoriteUrls.contains(channel.streamUrl)) " ★" else ""

        val prog = getProgramForChannel(channel)
        val progText = if (prog.isNotEmpty()) "  |  🔹 $prog" else ""

        tvBannerCategory.text = "● ${channel.category.uppercase()}$isFav$progText"

        val finalLogoUrl = channel.logoUrl.ifEmpty {
            if (channel.tvgId.isNotEmpty()) epgLogosMap[channel.tvgId.lowercase(Locale.ROOT)] ?: "" else ""
        }.ifEmpty {
            epgLogosMap[normalizeName(channel.name)] ?: ""
        }

        val bannerView = imgBannerLogo
        if (bannerView != null) {
            if (finalLogoUrl.isNotEmpty()) {
                bannerView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(finalLogoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(100, 100)
                    .dontAnimate()
                    .timeout(2500)
                    .into(bannerView)
            } else {
                val safeView: View = bannerView
                Glide.with(this).clear(safeView)
                bannerView.setImageDrawable(null)
                bannerView.visibility = View.GONE
            }
        }

        infoBanner.visibility = View.VISIBLE
        bannerRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { infoBanner.visibility = View.GONE }
        bannerRunnable = r
        handler.postDelayed(r, 4500)
    }

    private fun exitApplicationCompletely() {
        handler.removeCallbacks(bufferingWatchdog)
        player?.stop()
        player?.release()
        player = null
        finishAffinity()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (linkDialogPanel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                linkDialogPanel.visibility = View.GONE
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (numPadPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    closeAllPanels()
                    return true
                }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    appendNumpadDigit(keyCode - KeyEvent.KEYCODE_0)
                    return true
                }
            }
            return super.onKeyDown(keyCode, event)
        }

        if (categoryPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    closeAllPanels()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    btnOpenSettings.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    categoryPanel.visibility = View.GONE
                    channelPanel.visibility = View.VISIBLE
                    focusCurrentChannelCentered()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    return super.onKeyDown(keyCode, event)
                }
            }
        }

        if (channelPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    closeAllPanels()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    channelPanel.visibility = View.GONE
                    categoryPanel.visibility = View.VISIBLE
                    val catIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
                    recyclerViewCategories.post {
                        recyclerViewCategories.scrollToPosition(catIndex)
                        val vh = recyclerViewCategories.findViewHolderForAdapterPosition(catIndex)
                        if (vh != null) vh.itemView.requestFocus() else recyclerViewCategories.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    return super.onKeyDown(keyCode, event)
                }
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (infoBanner.visibility == View.VISIBLE) {
                    infoBanner.visibility = View.GONE
                    return true
                }

                backPressCount++
                resetBackPressRunnable?.let { handler.removeCallbacks(it) }

                if (backPressCount >= 5) {
                    exitApplicationCompletely()
                    return true
                }

                val r = Runnable { backPressCount = 0 }
                resetBackPressRunnable = r
                handler.postDelayed(r, 2000)

                if (previousPlayingIndex in allChannels.indices && previousPlayingIndex != currentPlayingIndex) {
                    playChannelByIndex(previousPlayingIndex)
                }

                if (backPressCount >= 2) {
                    Toast.makeText(this, "Нажмите ещё ${5 - backPressCount} раз(а) для выхода", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                okPressCount++
                resetOkPressRunnable?.let { handler.removeCallbacks(it) }

                if (okPressCount >= 3) {
                    okPressCount = 0
                    syncStatusCard.visibility = if (syncStatusCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    return true
                }

                val r = Runnable {
                    if (okPressCount == 1 && allChannels.isNotEmpty() && currentPlayingIndex in allChannels.indices) {
                        showInfoBanner(allChannels[currentPlayingIndex])
                    }
                    okPressCount = 0
                }
                resetOkPressRunnable = r
                handler.postDelayed(r, 500)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                stepChannelInCurrentCategory(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                stepChannelInCurrentCategory(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                openChannelPanelCenteredOnCurrent()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                closeAllPanels()
                numPadPanel.visibility = View.VISIBLE
                digitInputBuffer.clear()
                tvNumpadDisplay.text = "---"
                findViewById<Button>(R.id.btn1)?.post { findViewById<Button>(R.id.btn1)?.requestFocus() }
                return true
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                closeAllPanels()
                appendNumpadDigit(keyCode - KeyEvent.KEYCODE_0)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun appendNumpadDigit(digit: Int) {
        if (digitInputBuffer.length >= 4) return
        digitInputBuffer.append(digit)
        tvNumpadDisplay.text = digitInputBuffer.toString()
        if (numPadPanel.visibility != View.VISIBLE) {
            closeAllPanels()
            numPadPanel.visibility = View.VISIBLE
            findViewById<Button>(R.id.btn1)?.post { findViewById<Button>(R.id.btn1)?.requestFocus() }
        }
        digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { executeNumpadSearch() }
        digitAutoExecuteRunnable = r
        handler.postDelayed(r, 3500)
    }

    private fun executeNumpadSearch() {
        digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
        val targetNumber = digitInputBuffer.toString().toIntOrNull()
        if (targetNumber != null) {
            val foundIndex = allChannels.indexOfFirst { it.number == targetNumber }
            if (foundIndex != -1) {
                closeAllPanels()
                playChannelByIndex(foundIndex)
            } else {
                Toast.makeText(applicationContext, "Канал не найден", Toast.LENGTH_SHORT).show()
                tvNumpadDisplay.text = "---"
            }
        }
        digitInputBuffer.clear()
    }
}
