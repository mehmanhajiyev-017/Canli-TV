package com.canlitv.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
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
    private lateinit var btnCheckAppUpdate: Button
    private lateinit var tvUpdateStatus: TextView
    private lateinit var btnCloseSettings: Button
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

    private lateinit var rowDefaultBg: GradientDrawable
    private lateinit var rowFocusedBg: GradientDrawable

    private val handler = Handler(Looper.getMainLooper())
    private val digitInputBuffer = StringBuilder()
    private var bannerRunnable: Runnable? = null
    private var digitAutoExecuteRunnable: Runnable? = null
    private var hideSyncCardRunnable: Runnable? = null
    private var maxSyncTimeoutRunnable: Runnable? = null

    private var periodicSyncRunnable: Runnable? = null
    private val SYNC_INTERVAL_MS = 60 * 60 * 1000L

    private var isM3uLoading = false
    private var isEpgLoading = false

    private var downloadId: Long = -1L

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id != -1L && id == downloadId) {
                val dManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val uri = dManager.getUriForDownloadedFile(downloadId)
                if (uri != null) {
                    tvUpdateStatus.setTextColor(Color.parseColor("#4CAF50"))
                    tvUpdateStatus.text = "Загружено! Установка..."

                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    startActivity(install)
                } else {
                    tvUpdateStatus.setTextColor(Color.parseColor("#FF5252"))
                    tvUpdateStatus.text = "Файл не найден"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        rowDefaultBg = GradientDrawable().apply {
            setColor(Color.parseColor("#33151A22"))
            cornerRadius = 12f
        }
        rowFocusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#402979FF"))
            cornerRadius = 12f
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
        btnCheckAppUpdate = findViewById(R.id.btnCheckAppUpdate)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
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

        syncStatusCard.background = GradientDrawable().apply {
            setColor(Color.parseColor("#D9101522"))
            cornerRadius = 16f
            setStroke(2, Color.parseColor("#55FFFFFF"))
        }

        numPadPanel.background = GradientDrawable().apply {
            setColor(Color.parseColor("#990B0E14"))
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }

        loadFavorites()
        initNumpadButtons()
        initSettingsPanelFocus()

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
        recyclerViewChannels.setItemViewCacheSize(25)

        recyclerViewCategories.layoutManager = LinearLayoutManager(this)
        recyclerViewCategories.setHasFixedSize(true)

        btnSaveM3u.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                prefs.edit().putString("M3U_URL", url).apply()
                linkDialogPanel.visibility = View.GONE
                currentPlayingIndex = -1
                fetchM3U(url, autoPlay = true)
            } else {
                Toast.makeText(applicationContext, "Введите правильный URL M3U", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshM3u.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                linkDialogPanel.visibility = View.GONE
                fetchM3U(url, autoPlay = false)
            } else {
                Toast.makeText(applicationContext, "M3U ссылка не введена", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                prefs.edit().putString("EPG_URL", epgUrl).apply()
                linkDialogPanel.visibility = View.GONE
                fetchEPGDirectly(epgUrl)
            } else {
                Toast.makeText(applicationContext, "Введите правильный URL EPG", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                linkDialogPanel.visibility = View.GONE
                fetchEPGDirectly(epgUrl)
            } else {
                Toast.makeText(applicationContext, "EPG ссылка не введена", Toast.LENGTH_SHORT).show()
            }
        }

        btnCheckAppUpdate.setOnClickListener {
            checkAndDownloadAppUpdate()
        }

        btnCloseSettings.setOnClickListener {
            linkDialogPanel.visibility = View.GONE
        }
    }

    private fun checkAndDownloadAppUpdate() {
        tvUpdateStatus.visibility = View.VISIBLE
        tvUpdateStatus.setTextColor(Color.parseColor("#2979FF"))
        tvUpdateStatus.text = "Проверка обновления..."

        val updateUrl = "https://hajiyev.eu/canlitv/app-release.apk"

        try {
            val dManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(updateUrl)).apply {
                setTitle("Canli TV Обновление")
                setDescription("Загрузка...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "app-release.apk")
                setMimeType("application/vnd.android.package-archive")
            }

            val oldFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "app-release.apk")
            if (oldFile.exists()) oldFile.delete()

            downloadId = dManager.enqueue(request)
            tvUpdateStatus.text = "Загрузка..."
        } catch (e: Exception) {
            tvUpdateStatus.setTextColor(Color.parseColor("#FF5252"))
            tvUpdateStatus.text = "Ошибка: ${e.message}"
        }
    }

    private fun initPlayer() {
        if (player == null) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)

            val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

            player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            playerView.player = player
            playerView.useController = false

            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
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
        val streamUri = Uri.parse(channel.streamUrl)
        val mediaItem = MediaItem.fromUri(streamUri)

        player?.setMediaItem(mediaItem)
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

        val dialogButtons = listOf(btnSaveM3u, btnRefreshM3u, btnSaveEpg, btnRefreshEpg, btnCheckAppUpdate, btnCloseSettings)
        for (btn in dialogButtons) {
            btn.backgroundTintList = null
            val defaultBtnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#4D151A22"))
                cornerRadius = 14f
                setStroke(2, Color.parseColor("#55FFFFFF"))
            }
            val focusedBtnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#402979FF"))
                cornerRadius = 14f
                setStroke(2, Color.parseColor("#80FFFFFF"))
            }
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
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        val savedM3u = prefs.getString("M3U_URL", "") ?: ""
        val savedEpg = prefs.getString("EPG_URL", "") ?: ""

        if (savedM3u.isNotEmpty()) {
            fetchM3U(savedM3u, autoPlay = (player?.isPlaying != true))
        } else {
            showSettingsDialog()
        }

        if (savedEpg.isNotEmpty() && savedEpg.startsWith("http")) {
            fetchEPGDirectly(savedEpg)
        }

        startPeriodicSync()
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        player?.stop()
        player?.release()
        player = null
        stopPeriodicSync()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
        player?.release()
        player = null
        stopPeriodicSync()
        handler.removeCallbacksAndMessages(null)
    }

    private fun startPeriodicSync() {
        stopPeriodicSync()
        val r = object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                val m3u = prefs.getString("M3U_URL", "") ?: ""
                val epg = prefs.getString("EPG_URL", "") ?: ""

                if (m3u.isNotEmpty()) {
                    fetchM3U(m3u, autoPlay = false)
                }
                if (epg.isNotEmpty() && epg.startsWith("http")) {
                    fetchEPGDirectly(epg)
                }
                handler.postDelayed(this, SYNC_INTERVAL_MS)
            }
        }
        periodicSyncRunnable = r
        handler.postDelayed(r, SYNC_INTERVAL_MS)
    }

    private fun stopPeriodicSync() {
        periodicSyncRunnable?.let { handler.removeCallbacks(it) }
        periodicSyncRunnable = null
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
            val defaultBg = GradientDrawable().apply {
                setColor(Color.parseColor("#4D151A22"))
                cornerRadius = 14f
                setStroke(2, Color.parseColor("#33FFFFFF"))
            }
            val focusedBg = GradientDrawable().apply {
                setColor(Color.parseColor("#402979FF"))
                cornerRadius = 14f
                setStroke(2, Color.parseColor("#80FFFFFF"))
            }
            btn.background = defaultBg
            btn.setTextColor(Color.parseColor("#ECEFF1"))
            btn.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    btn.background = focusedBg
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.background = defaultBg
                    btn.setTextColor(Color.parseColor("#ECEFF1"))
                }
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
        tvUpdateStatus.visibility = View.GONE
        linkDialogPanel.visibility = View.VISIBLE
        btnSaveM3u.requestFocus()
    }

    private fun showSyncCard() {
        syncStatusCard.visibility = View.VISIBLE
        hideSyncCardRunnable?.let { handler.removeCallbacks(it) }

        maxSyncTimeoutRunnable?.let { handler.removeCallbacks(it) }
        val rTimeout = Runnable { syncStatusCard.visibility = View.GONE }
        maxSyncTimeoutRunnable = rTimeout
        handler.postDelayed(rTimeout, 600000)
    }

    private fun checkAndScheduleHideSyncCard() {
        if (!isM3uLoading && !isEpgLoading) {
            hideSyncCardRunnable?.let { handler.removeCallbacks(it) }
            val r = Runnable { syncStatusCard.visibility = View.GONE }
            hideSyncCardRunnable = r
            handler.postDelayed(r, 8000)
        }
    }

    private fun fetchM3U(url: String, autoPlay: Boolean) {
        isM3uLoading = true
        runOnUiThread { 
            showSyncCard()
            tvSyncChannels.text = "Каналы: 0"
            tvSyncLogos.text = "Логотипы: 0"
        }

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                isM3uLoading = false
                runOnUiThread {
                    tvSyncChannels.text = "Каналы: Ошибка"
                    if (allChannels.isEmpty()) {
                        showSettingsDialog()
                    }
                    checkAndScheduleHideSyncCard()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    isM3uLoading = false
                    runOnUiThread {
                        tvSyncChannels.text = "Каналы: Ошибка ${response.code}"
                        if (allChannels.isEmpty()) {
                            showSettingsDialog()
                        }
                        checkAndScheduleHideSyncCard()
                    }
                    return
                }

                val body = response.body?.string() ?: run {
                    isM3uLoading = false
                    runOnUiThread { checkAndScheduleHideSyncCard() }
                    return
                }

                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                val lastPlayedUrl = if (currentPlayingIndex in allChannels.indices) {
                    allChannels[currentPlayingIndex].streamUrl
                } else {
                    prefs.getString("LAST_CHANNEL_URL", "") ?: ""
                }

                parseM3ULive(body)
                applyEpgLogosToChannels()
                isM3uLoading = false

                val logosCount = allChannels.count { it.logoUrl.isNotEmpty() }

                runOnUiThread {
                    tvSyncChannels.text = "Каналы: ${allChannels.size}"
                    tvSyncLogos.text = "Логотипы: $logosCount"
                    checkAndScheduleHideSyncCard()
                }

                runOnUiThread {
                    setupCategoryAdapter()
                    val catToSelect = if (categories.contains(selectedCategory)) selectedCategory else "Все"
                    filterChannels(catToSelect)

                    if (allChannels.isNotEmpty()) {
                        val resumeIndex = if (lastPlayedUrl.isNotEmpty()) {
                            allChannels.indexOfFirst { it.streamUrl == lastPlayedUrl }.takeIf { it != -1 } ?: 0
                        } else {
                            0
                        }

                        if (autoPlay || player?.isPlaying != true) {
                            playChannelByIndex(resumeIndex)
                        } else {
                            currentPlayingIndex = resumeIndex
                        }
                    } else {
                        showSettingsDialog()
                    }
                }
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

        val tempChannels = mutableListOf<Channel>()
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

    private fun fetchEPGDirectly(epgUrl: String) {
        isEpgLoading = true
        runOnUiThread {
            showSyncCard()
            tvSyncPrograms.text = "Программы: 0"
        }

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(epgUrl)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                isEpgLoading = false
                runOnUiThread {
                    tvSyncPrograms.text = "Программы: Ошибка"
                    checkAndScheduleHideSyncCard()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    isEpgLoading = false
                    runOnUiThread {
                        tvSyncPrograms.text = "Программы: Ошибка ${response.code}"
                        checkAndScheduleHideSyncCard()
                    }
                    return
                }

                val bodyStream = response.body?.byteStream()
                if (bodyStream == null) {
                    isEpgLoading = false
                    runOnUiThread { checkAndScheduleHideSyncCard() }
                    return
                }

                Thread({
                    try {
                        val buffered = BufferedInputStream(bodyStream)
                        buffered.mark(2)
                        val b1 = buffered.read()
                        val b2 = buffered.read()
                        buffered.reset()

                        val isGzip = (b1 == 0x1f && b2 == 0x8b) || epgUrl.endsWith(".gz", ignoreCase = true)
                        val inStream: InputStream = if (isGzip) GZIPInputStream(buffered) else buffered

                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(inStream, null)

                        val now = System.currentTimeMillis()
                        val channelIdToNames = HashMap<String, MutableList<String>>()
                        val livePrograms = HashMap<String, String>()
                        val tempEpgLogos = HashMap<String, String>()

                        var eventType = parser.eventType
                        var currentChannelId = ""
                        var inChannel = false
                        var inProgramme = false
                        var progChannel = ""
                        var isCurrentProg = false
                        var logosDispatched = false

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            when (eventType) {
                                XmlPullParser.START_TAG -> {
                                    val tagName = parser.name
                                    when (tagName) {
                                        "channel" -> {
                                            inChannel = true
                                            currentChannelId = parser.getAttributeValue(null, "id")?.trim() ?: ""
                                            if (currentChannelId.isNotEmpty()) {
                                                channelIdToNames.getOrPut(currentChannelId.lowercase(Locale.ROOT)) { mutableListOf() }
                                            }
                                        }
                                        "display-name" -> {
                                            if (inChannel && currentChannelId.isNotEmpty()) {
                                                val text = parser.nextTextSafe().trim()
                                                if (text.isNotEmpty()) {
                                                    channelIdToNames.getOrPut(currentChannelId.lowercase(Locale.ROOT)) { mutableListOf() }.add(text)
                                                }
                                            }
                                        }
                                        "icon" -> {
                                            if (inChannel && currentChannelId.isNotEmpty()) {
                                                val iconSrc = parser.getAttributeValue(null, "src")?.trim() ?: ""
                                                if (iconSrc.isNotEmpty() && iconSrc.startsWith("http")) {
                                                    tempEpgLogos[currentChannelId.lowercase(Locale.ROOT)] = iconSrc
                                                }
                                            }
                                        }
                                        "programme" -> {
                                            if (!logosDispatched && tempEpgLogos.isNotEmpty()) {
                                                logosDispatched = true
                                                val finalLogos = HashMap<String, String>()
                                                for ((chId, logoUrl) in tempEpgLogos) {
                                                    finalLogos[chId] = logoUrl
                                                    channelIdToNames[chId]?.forEach { name ->
                                                        finalLogos[normalizeName(name)] = logoUrl
                                                        finalLogos[name.lowercase(Locale.ROOT)] = logoUrl
                                                    }
                                                }
                                                epgLogosMap.putAll(finalLogos)
                                                applyEpgLogosToChannels()

                                                val quickLogoCount = allChannels.count { it.logoUrl.isNotEmpty() }
                                                runOnUiThread {
                                                    tvSyncLogos.text = "Логотипы: $quickLogoCount"
                                                    recyclerViewChannels.adapter?.notifyDataSetChanged()
                                                }
                                            }

                                            val startStr = parser.getAttributeValue(null, "start") ?: ""
                                            val stopStr = parser.getAttributeValue(null, "stop") ?: ""
                                            progChannel = parser.getAttributeValue(null, "channel")?.trim() ?: ""
                                            
                                            val startMs = parseXmlTvTime(startStr)
                                            val stopMs = parseXmlTvTime(stopStr)

                                            // Geniş zaman intervalı yoxlaması: Hazırda yayımlanan proqram
                                            if (startMs > 0 && stopMs > 0 && now in startMs..stopMs && progChannel.isNotEmpty()) {
                                                inProgramme = true
                                                isCurrentProg = true
                                            } else {
                                                inProgramme = false
                                                isCurrentProg = false
                                            }
                                        }
                                        "title" -> {
                                            if (inProgramme && isCurrentProg) {
                                                val titleText = parser.nextTextSafe().trim()
                                                if (titleText.isNotEmpty() && progChannel.isNotEmpty()) {
                                                    livePrograms[progChannel.lowercase(Locale.ROOT)] = titleText
                                                }
                                                isCurrentProg = false
                                            }
                                        }
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    val tagName = parser.name
                                    if (tagName == "channel") {
                                        inChannel = false
                                        currentChannelId = ""
                                    } else if (tagName == "programme") {
                                        inProgramme = false
                                        isCurrentProg = false
                                    }
                                }
                            }
                            eventType = parser.next()
                        }

                        if (!logosDispatched && tempEpgLogos.isNotEmpty()) {
                            val finalLogos = HashMap<String, String>()
                            for ((chId, logoUrl) in tempEpgLogos) {
                                finalLogos[chId] = logoUrl
                                channelIdToNames[chId]?.forEach { name ->
                                    finalLogos[normalizeName(name)] = logoUrl
                                    finalLogos[name.lowercase(Locale.ROOT)] = logoUrl
                                }
                            }
                            epgLogosMap.putAll(finalLogos)
                            applyEpgLogosToChannels()
                        }

                        // Verilişləri həm ID, həm display-name, həm də təmizlənmiş adla xəritələyirik
                        val mappedPrograms = HashMap<String, String>()
                        for ((chId, title) in livePrograms) {
                            mappedPrograms[chId] = title
                            channelIdToNames[chId]?.forEach { name ->
                                mappedPrograms[normalizeName(name)] = title
                                mappedPrograms[name.lowercase(Locale.ROOT)] = title
                            }
                        }

                        currentProgramsMap.clear()
                        currentProgramsMap.putAll(mappedPrograms)

                        val finalLogosCount = allChannels.count { it.logoUrl.isNotEmpty() }

                        runOnUiThread {
                            tvSyncPrograms.text = "Программы: ${currentProgramsMap.size}"
                            tvSyncLogos.text = "Логотипы: $finalLogosCount"
                            recyclerViewChannels.adapter?.notifyDataSetChanged()
                            if (infoBanner.visibility == View.VISIBLE && currentPlayingIndex in allChannels.indices) {
                                showInfoBanner(allChannels[currentPlayingIndex])
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { tvSyncPrograms.text = "Программы: Ошибка" }
                    } finally {
                        isEpgLoading = false
                        try {
                            bodyStream.close()
                        } catch (_: Exception) {}
                        runOnUiThread { checkAndScheduleHideSyncCard() }
                    }
                }, "FastEpgWorker").apply {
                    priority = Thread.MIN_PRIORITY
                }.start()
            }
        })
    }

    // nextText() xətalarının qarşısını alan etibarlı mətn oxuyucu
    private fun XmlPullParser.nextTextSafe(): String {
        var result = ""
        if (eventType != XmlPullParser.START_TAG) return result
        var event = next()
        while (event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.TEXT) {
                result += text
            }
            event = next()
        }
        return result
    }

    private fun normalizeName(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("""\b(hd|sd|fhd|4k|ru|ua|az|tr|hevc|50fps|orig|original|tv)\b"""), "")
            .replace(Regex("""[^a-z0-9а-яёәғçşöüı]"""), "")
            .trim()
    }

    // EPG vaxtlarını dəqiq millisaniyəyə çevirən funksiya (+0300, +0000, UTC dəstəkli)
    private fun parseXmlTvTime(timeStr: String): Long {
        if (timeStr.length < 14) return 0L
        val clean = timeStr.trim()
        return try {
            if (clean.length >= 19 && (clean.contains("+") || clean.contains("-"))) {
                val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                format.parse(clean)?.time ?: 0L
            } else {
                val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(clean.substring(0, 14))?.time ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun getProgramForChannel(ch: Channel): String {
        if (currentProgramsMap.isEmpty()) return ""
        
        // 1. tvg-id ilə yoxla
        if (ch.tvgId.isNotEmpty()) {
            currentProgramsMap[ch.tvgId.lowercase(Locale.ROOT)]?.let { return it }
        }
        // 2. Təmizlənmiş adla yoxla
        val normalized = normalizeName(ch.name)
        if (normalized.isNotEmpty()) {
            currentProgramsMap[normalized]?.let { return it }
        }
        // 3. Orijinal kiçik hərflərlə adla yoxla
        currentProgramsMap[ch.name.lowercase(Locale.ROOT)]?.let { return it }

        // 4. Əgər adda nöqtə, defis varsa onları atıb yoxla
        val simple = ch.name.lowercase(Locale.ROOT).replace("[^a-z0-9а-я]".toRegex(), "")
        if (simple.isNotEmpty()) {
            currentProgramsMap[simple]?.let { return it }
        }

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
        channelPanel.visibility = View.VISIBLE
        categoryPanel.visibility = View.GONE

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
                    layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
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
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 14 }
                }
                val arrowView = TextView(this@MainActivity).apply { 
                    id = View.generateViewId()
                    text = "›"
                    textSize = 18f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD 
                }
                row.addView(iconView); row.addView(titleView); row.addView(arrowView)
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
                        .override(90, 90)
                        .into(imgLogo)
                } else {
                    imgLogo.visibility = View.GONE
                }

                row.background = rowDefaultBg

                row.setOnFocusChangeListener { _, hasFocus ->
                    row.background = if (hasFocus) rowFocusedBg else rowDefaultBg
                    tvNum.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#B0BEC5"))
                    tvName.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#FFFFFF"))
                }

                row.setOnClickListener {
                    val allIndex = allChannels.indexOf(ch)
                    if (allIndex != -1) {
                        playChannelByIndex(allIndex)
                    }
                    channelPanel.visibility = View.GONE
                }

                row.setOnLongClickListener { toggleFavorite(ch); true }

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
                            channelPanel.visibility = View.GONE
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

        if (imgBannerLogo != null) {
            if (finalLogoUrl.isNotEmpty()) {
                imgBannerLogo?.visibility = View.VISIBLE
                Glide.with(this)
                    .load(finalLogoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imgBannerLogo!!)
            } else {
                imgBannerLogo?.visibility = View.GONE
            }
        }

        infoBanner.visibility = View.VISIBLE
        bannerRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { infoBanner.visibility = View.GONE }
        bannerRunnable = r
        handler.postDelayed(r, 4500)
    }

    private fun exitApplicationCompletely() {
        player?.stop()
        player?.release()
        player = null
        finishAffinity()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (linkDialogPanel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { linkDialogPanel.visibility = View.GONE; return true }
            return super.onKeyDown(keyCode, event)
        }

        if (numPadPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
                    digitInputBuffer.clear(); numPadPanel.visibility = View.GONE; return true
                }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { appendNumpadDigit(keyCode - KeyEvent.KEYCODE_0); return true }
            }
            return super.onKeyDown(keyCode, event)
        }

        if (categoryPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    categoryPanel.visibility = View.GONE
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
                    channelPanel.visibility = View.GONE
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
                if (allChannels.isNotEmpty() && currentPlayingIndex in allChannels.indices) {
                    showInfoBanner(allChannels[currentPlayingIndex])
                    return true
                }
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
                numPadPanel.visibility = View.VISIBLE
                digitInputBuffer.clear()
                tvNumpadDisplay.text = "---"
                findViewById<Button>(R.id.btn1)?.post { findViewById<Button>(R.id.btn1)?.requestFocus() }
                return true
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { appendNumpadDigit(keyCode - KeyEvent.KEYCODE_0); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun appendNumpadDigit(digit: Int) {
        if (digitInputBuffer.length >= 4) return
        digitInputBuffer.append(digit)
        tvNumpadDisplay.text = digitInputBuffer.toString()
        if (numPadPanel.visibility != View.VISIBLE) {
            numPadPanel.visibility = View.VISIBLE
            findViewById<Button>(R.id.btn1)?.post { findViewById<Button>(R.id.btn1)?.requestFocus() }
        }
        digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { executeNumpadSearch() }
        digitAutoExecuteRunnable = r
        handler.postDelayed(r, 5000)
    }

    private fun executeNumpadSearch() {
        digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
        val targetNumber = digitInputBuffer.toString().toIntOrNull()
        if (targetNumber != null) {
            val foundIndex = allChannels.indexOfFirst { it.number == targetNumber }
            if (foundIndex != -1) {
                playChannelByIndex(foundIndex)
                numPadPanel.visibility = View.GONE
            } else {
                Toast.makeText(applicationContext, "Канал не найден", Toast.LENGTH_SHORT).show()
                tvNumpadDisplay.text = "---"
            }
        }
        digitInputBuffer.clear()
    }
}
