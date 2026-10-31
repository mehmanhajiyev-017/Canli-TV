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
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.PlayerView
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
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
    private lateinit var btnToggleMode: Button
    private lateinit var btnOpenSettings: View
    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var tvNumpadDisplay: TextView
    private lateinit var tvBannerNumber: TextView
    private var imgBannerLogo: ImageView? = null
    private lateinit var tvBannerName: TextView
    private lateinit var tvBannerCategory: TextView

    private lateinit var syncStatusCard: View
    private lateinit var tvSyncChannels: TextView
    private lateinit var tvSyncLogos: TextView
    private lateinit var tvSyncPrograms: TextView

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

    private var isTvMode = true

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

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var detectedTimeZone: TimeZone = TimeZone.getDefault()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        rowDefaultBg = GradientDrawable().apply {
            setColor(Color.parseColor("#33151A22"))
            cornerRadius = 12f
        }
        rowFocusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#402979FF"))
            cornerRadius = 12f
            setStroke(2, Color.parseColor("#80FFFFFF"))
        }

        defaultBtnBg = GradientDrawable().apply {
            setColor(Color.parseColor("#4D151A22"))
            cornerRadius = 14f
            setStroke(2, Color.parseColor("#55FFFFFF"))
        }
        focusedBtnBg = GradientDrawable().apply {
            setColor(Color.parseColor("#402979FF"))
            cornerRadius = 14f
            setStroke(2, Color.parseColor("#80FFFFFF"))
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
        btnToggleMode = findViewById(R.id.btnToggleMode)
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

        syncStatusCard.background = GradientDrawable().apply {
            setColor(Color.parseColor("#D9101522"))
            cornerRadius = 16f
            setStroke(2, Color.parseColor("#55FFFFFF"))
        }
        syncStatusCard.visibility = View.GONE

        numPadPanel.background = GradientDrawable().apply {
            setColor(Color.parseColor("#990B0E14"))
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }

        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        isTvMode = prefs.getBoolean("IS_TV_MODE", true)
        applyAppMode(isTvMode)

        detectTimezoneByIp()
        loadFavorites()
        initNumpadButtons()
        initSettingsPanelFocus()

        playerView.setOnClickListener {
            if (!isTvMode) {
                if (channelPanel.visibility == View.VISIBLE || categoryPanel.visibility == View.VISIBLE) {
                    channelPanel.visibility = View.GONE
                    categoryPanel.visibility = View.GONE
                } else {
                    openChannelPanelCenteredOnCurrent()
                }
            }
        }

        val settingsDefaultBg = GradientDrawable().apply {
            setColor(Color.parseColor("#551C222E"))
            cornerRadius = 14f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }
        val settingsFocusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#402979FF"))
            cornerRadius = 14f
            setStroke(2, Color.parseColor("#80FFFFFF"))
        }

        btnOpenSettings.background = settingsDefaultBg
        btnOpenSettings.setOnClickListener {
            categoryPanel.visibility = View.GONE
            channelPanel.visibility = View.GONE
            showSettingsDialog()
        }

        btnOpenSettings.setOnFocusChangeListener { _, hasFocus ->
            val layout = btnOpenSettings as? LinearLayout
            val icon = layout?.getChildAt(0) as? TextView
            val text = layout?.getChildAt(1) as? TextView
            if (hasFocus) {
                btnOpenSettings.background = settingsFocusedBg
                icon?.setTextColor(Color.WHITE)
                text?.setTextColor(Color.WHITE)
            } else {
                btnOpenSettings.background = settingsDefaultBg
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
        recyclerViewChannels.setItemViewCacheSize(15)

        recyclerViewCategories.layoutManager = LinearLayoutManager(this)
        recyclerViewCategories.setHasFixedSize(true)

        btnToggleMode.setOnClickListener {
            isTvMode = !isTvMode
            prefs.edit().putBoolean("IS_TV_MODE", isTvMode).apply()
            applyAppMode(isTvMode)
            Toast.makeText(
                applicationContext,
                if (isTvMode) "TV Rejimi Aktivdir" else "Mobil Rejim Aktivdir",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnSaveM3u.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                prefs.edit().putString("M3U_URL", url).apply()
                linkDialogPanel.visibility = View.GONE
                currentPlayingIndex = -1
                fetchM3U(url, autoPlay = true)
            } else {
                Toast.makeText(applicationContext, "M3U linkini daxil edin", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshM3u.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                linkDialogPanel.visibility = View.GONE
                fetchM3U(url, autoPlay = false)
            } else {
                Toast.makeText(applicationContext, "M3U linki daxil edilməyib", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                prefs.edit().putString("EPG_URL", epgUrl).apply()
                linkDialogPanel.visibility = View.GONE
                deleteLocalEpgCache()
                downloadAndParseEpg(epgUrl)
            } else {
                Toast.makeText(applicationContext, "EPG linkini daxil edin", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                linkDialogPanel.visibility = View.GONE
                deleteLocalEpgCache()
                downloadAndParseEpg(epgUrl)
            } else {
                Toast.makeText(applicationContext, "EPG linki daxil edilməyib", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseSettings.setOnClickListener {
            linkDialogPanel.visibility = View.GONE
        }
    }

    private fun applyAppMode(tvMode: Boolean) {
        val density = resources.displayMetrics.density
        if (tvMode) {
            btnToggleMode.text = "📺"
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            categoryPanel.layoutParams.width = (320 * density).toInt()
            channelPanel.layoutParams.width = (420 * density).toInt()
        } else {
            btnToggleMode.text = "📱"
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            categoryPanel.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            channelPanel.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        categoryPanel.requestLayout()
        channelPanel.requestLayout()
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
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2500, 12000, 1000, 2000)
                .build()

            player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
                .setLoadControl(loadControl)
                .build()

            playerView.player = player
            playerView.useController = false

            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        player?.seekToDefaultPosition()
                        player?.prepare()
                        player?.play()
                    } else {
                        handler.postDelayed({
                            if (!isFinishing && player != null) {
                                player?.prepare()
                                player?.play()
                            }
                        }, 1200)
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
                setColor(Color.parseColor("#1A212D"))
                cornerRadius = 12f
                setStroke(2, Color.parseColor("#55FFFFFF"))
            }
            val focusedEditBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1A212D"))
                cornerRadius = 12f
                setStroke(3, Color.parseColor("#2979FF"))
            }
            input.background = defaultEditBg
            input.setTextColor(Color.WHITE)
            input.setHintTextColor(Color.parseColor("#90A4AE"))
            input.setOnFocusChangeListener { _, hasFocus ->
                input.background = if (hasFocus) focusedEditBg else defaultEditBg
            }
        }

        val dialogButtons = listOf(btnSaveM3u, btnRefreshM3u, btnSaveEpg, btnRefreshEpg, btnToggleMode, btnCloseSettings)
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
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        etM3uUrl.setText(prefs.getString("M3U_URL", ""))
        etEpgUrl.setText(prefs.getString("EPG_URL", ""))
        applyAppMode(isTvMode)
        linkDialogPanel.visibility = View.VISIBLE
        btnSaveM3u.requestFocus()
    }

    private fun fetchM3U(url: String, autoPlay: Boolean) {
        synchronized(loadingLock) {
            if (isM3uLoading) return
            isM3uLoading = true
        }

        runOnUiThread { 
            tvSyncChannels.text = "Kanallar: Yüklənir..."
            tvSyncLogos.text = "Loqolar: 0"
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
                tvSyncChannels.text = "Kanallar: Link xətası"
                if (allChannels.isEmpty()) showSettingsDialog()
            }
            return
        }

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                synchronized(loadingLock) { isM3uLoading = false }
                runOnUiThread {
                    tvSyncChannels.text = "Kanallar: Yüklənmə xətası"
                    if (allChannels.isEmpty() && !isFinishing) showSettingsDialog()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    synchronized(loadingLock) { isM3uLoading = false }
                    runOnUiThread {
                        tvSyncChannels.text = "Kanallar: Boş cavab"
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

                        parseM3ULive(body)
                        applyEpgLogosToChannels()

                        val channelCount = allChannels.size
                        val logosCount = allChannels.count { it.logoUrl.isNotEmpty() }

                        runOnUiThread {
                            synchronized(loadingLock) { isM3uLoading = false }
                            if (isFinishing || isDestroyed) return@runOnUiThread

                            tvSyncChannels.text = "Kanallar: $channelCount"
                            tvSyncLogos.text = "Loqolar: $logosCount"

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
                                    handler.postDelayed({ downloadAndParseEpg(savedEpg) }, 10000)
                                }
                            } else {
                                showSettingsDialog()
                            }
                        }
                    } catch (_: Exception) {
                        synchronized(loadingLock) { isM3uLoading = false }
                        runOnUiThread {
                            tvSyncChannels.text = "Kanallar: Emal xətası"
                            if (allChannels.isEmpty() && !isFinishing) showSettingsDialog()
                        }
                    }
                }.start()
            }
        })
    }

    private fun parseM3ULive(content: String) {
        val lines = content.lines()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentTvgId = ""
        var counter = 1

        val tempChannels = ArrayList<Channel>(lines.size / 2)
        val tempCategories = LinkedHashSet<String>()
        tempCategories.add("Все")
        tempCategories.add("Избранное")

        val groupRegex = Regex("""group-title\s*=\s*["']?([^"',\r\n]+)["']?""", RegexOption.IGNORE_CASE)
        val logoRegex = Regex("""(?:tvg-logo|logo)\s*=\s*["']?([^"',\s\r\n]+)["']?""", RegexOption.IGNORE_CASE)
        val idRegex = Regex("""(?:tvg-id|tvg-name)\s*=\s*["']?([^"',\r\n]+)["']?""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                currentLogo = logoRegex.find(trimmed)?.groups?.get(1)?.value?.trim() ?: ""
                currentTvgId = idRegex.find(trimmed)?.groups?.get(1)?.value?.trim() ?: ""
                val groupFound = groupRegex.find(trimmed)?.groups?.get(1)?.value?.trim() ?: ""
                if (groupFound.isNotEmpty()) {
                    currentGroup = groupFound
                    tempCategories.add(currentGroup)
                }
                currentName = trimmed.substringAfterLast(",").trim()
            } else if (trimmed.startsWith("#EXTGRP:", ignoreCase = true)) {
                val grp = trimmed.substringAfter(":").trim()
                if (grp.isNotEmpty()) {
                    currentGroup = grp
                    tempCategories.add(currentGroup)
                }
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val name = if (currentName.isNotEmpty()) currentName else "Channel $counter"
                val cat = if (currentGroup.isNotEmpty()) currentGroup else "Other"
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
                currentName = ""; currentLogo = ""; currentGroup = ""; currentTvgId = ""
            }
        }

        allChannels.clear()
        allChannels.addAll(tempChannels)
        categories.clear()
        categories.addAll(tempCategories)
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
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                parseEpgFromFile(cacheFile)
            }.start()
            return
        }

        runOnUiThread { tvSyncPrograms.text = "Proqramlar: Yüklənir..." }

        val request = try {
            Request.Builder()
                .url(epgUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
        } catch (_: Exception) {
            synchronized(loadingLock) { isEpgLoading = false }
            runOnUiThread { tvSyncPrograms.text = "Proqramlar: Link xətası" }
            return
        }
