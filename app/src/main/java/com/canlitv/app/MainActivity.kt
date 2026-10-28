package com.canlitv.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
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
    private lateinit var btnOpenSettings: View
    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var tvNumpadDisplay: TextView
    private lateinit var tvBannerNumber: TextView
    private lateinit var imgBannerLogo: ImageView
    private lateinit var tvBannerName: TextView
    private lateinit var tvBannerCategory: TextView

    private lateinit var tvCategoriesTitle: TextView
    private lateinit var tvSettingsBtnText: TextView
    private lateinit var tvSettingsDialogTitle: TextView
    private lateinit var tvM3uLabel: TextView
    private lateinit var tvEpgLabel: TextView

    private lateinit var syncStatusCard: View
    private lateinit var tvSyncChannels: TextView
    private lateinit var tvSyncLogos: TextView
    private lateinit var tvSyncPrograms: TextView

    private lateinit var btnLangAz: LinearLayout
    private lateinit var btnLangRu: LinearLayout
    private lateinit var btnLangEn: LinearLayout
    private lateinit var btnLangDe: LinearLayout

    private val allChannels = mutableListOf<Channel>()
    private val displayedChannels = mutableListOf<Channel>()
    private val categories = mutableListOf<String>()
    private var selectedCategory = "Все"
    private val favoriteUrls = mutableSetOf<String>()

    private val currentProgramsMap = ConcurrentHashMap<String, String>()
    private val epgLogosMap = ConcurrentHashMap<String, String>()

    private var currentPlayingIndex = -1
    private var previousPlayingIndex = -1
    private var doubleBackToExitPressedOnce = false

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

    private var currentLang = "az"

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

        tvCategoriesTitle = findViewById(R.id.tvCategoriesTitle)
        tvSettingsBtnText = findViewById(R.id.tvSettingsBtnText)
        tvSettingsDialogTitle = findViewById(R.id.tvSettingsDialogTitle)
        tvM3uLabel = findViewById(R.id.tvM3uLabel)
        tvEpgLabel = findViewById(R.id.tvEpgLabel)

        syncStatusCard = findViewById(R.id.syncStatusCard)
        tvSyncChannels = findViewById(R.id.tvSyncChannels)
        tvSyncLogos = findViewById(R.id.tvSyncLogos)
        tvSyncPrograms = findViewById(R.id.tvSyncPrograms)

        btnLangAz = findViewById(R.id.btnLangAz)
        btnLangRu = findViewById(R.id.btnLangRu)
        btnLangEn = findViewById(R.id.btnLangEn)
        btnLangDe = findViewById(R.id.btnLangDe)

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

        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        currentLang = prefs.getString("APP_LANG", "az") ?: "az"
        selectedCategory = getAllCategoryName()

        loadFavorites()
        initNumpadButtons()
        initSettingsPanelFocus()
        initFlagButtons()
        updateUILanguage()

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
            val layout = btnOpenSettings as LinearLayout
            val icon = layout.getChildAt(0) as? TextView
            val text = layout.getChildAt(1) as? TextView
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
                prefs.edit().putString("M3U_URL", url).apply()
                linkDialogPanel.visibility = View.GONE
                currentPlayingIndex = -1
                fetchM3U(url, autoPlay = true)
            } else {
                Toast.makeText(applicationContext, getLocalizedText("toast_invalid_m3u"), Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshM3u.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                linkDialogPanel.visibility = View.GONE
                fetchM3U(url, autoPlay = false)
            } else {
                Toast.makeText(applicationContext, getLocalizedText("toast_no_m3u"), Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                prefs.edit().putString("EPG_URL", epgUrl).apply()
                linkDialogPanel.visibility = View.GONE
                fetchEPGDirectly(epgUrl)
            } else {
                Toast.makeText(applicationContext, getLocalizedText("toast_invalid_epg"), Toast.LENGTH_SHORT).show()
            }
        }

        btnRefreshEpg.setOnClickListener {
            val epgUrl = etEpgUrl.text.toString().trim()
            if (epgUrl.isNotEmpty() && epgUrl.startsWith("http")) {
                linkDialogPanel.visibility = View.GONE
                fetchEPGDirectly(epgUrl)
            } else {
                Toast.makeText(applicationContext, getLocalizedText("toast_no_epg"), Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseSettings.setOnClickListener {
            linkDialogPanel.visibility = View.GONE
        }
    }

    // Tənzimləmələr pəncərəsinin sol aşağısındakı bayraq düymələri
    private fun initFlagButtons() {
        val flagList = listOf(btnLangAz to "az", btnLangRu to "ru", btnLangEn to "en", btnLangDe to "de")

        val flagDefaultBg = GradientDrawable().apply {
            setColor(Color.parseColor("#261C222E"))
            cornerRadius = 10f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }
        val flagFocusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#402979FF"))
            cornerRadius = 10f
            setStroke(2, Color.parseColor("#80FFFFFF"))
        }

        for ((btn, langCode) in flagList) {
            btn.background = flagDefaultBg
            btn.setOnFocusChangeListener { _, hasFocus ->
                btn.background = if (hasFocus) flagFocusedBg else flagDefaultBg
            }
            btn.setOnClickListener {
                setLanguage(langCode)
            }
        }
    }

    private fun setLanguage(langCode: String) {
        currentLang = langCode
        getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
            .edit()
            .putString("APP_LANG", langCode)
            .apply()

        updateUILanguage()
        selectedCategory = getAllCategoryName()

        setupCategoryAdapter()
        filterChannels(selectedCategory)

        Toast.makeText(applicationContext, getLocalizedText("lang_changed"), Toast.LENGTH_SHORT).show()
    }

    private fun updateUILanguage() {
        tvCategoriesTitle.text = when (currentLang) {
            "az" -> "Kateqoriyalar"
            "en" -> "Categories"
            "de" -> "Kategorien"
            else -> "Категории"
        }

        tvSettingsBtnText.text = when (currentLang) {
            "az" -> "Tənzimləmələr"
            "en" -> "Settings"
            "de" -> "Einstellungen"
            else -> "Настройки"
        }

        tvSettingsDialogTitle.text = when (currentLang) {
            "az" -> "Mənbələrin idarə edilməsi"
            "en" -> "Source Management"
            "de" -> "Quellenverwaltung"
            else -> "Управление источниками"
        }

        tvM3uLabel.text = when (currentLang) {
            "az" -> "1. Pleylist (M3U URL):"
            "en" -> "1. Playlist (M3U URL):"
            "de" -> "1. Wiedergabeliste (M3U URL):"
            else -> "1. Плейлист (M3U URL):"
        }

        tvEpgLabel.text = when (currentLang) {
            "az" -> "2. Teleproqram (EPG XMLTV / .gz URL):"
            "en" -> "2. TV Guide (EPG XMLTV / .gz URL):"
            "de" -> "2. Programmzeitschrift (EPG XMLTV / .gz URL):"
            else -> "2. Телепрограмма (EPG XMLTV / .gz URL):"
        }

        btnCloseSettings.text = when (currentLang) {
            "az" -> "BAĞLA"
            "en" -> "CLOSE"
            "de" -> "SCHLIESSEN"
            else -> "ЗАКРЫТЬ"
        }

        etM3uUrl.hint = when (currentLang) {
            "az" -> "M3U linkini daxil edin"
            "en" -> "Paste M3U link"
            "de" -> "M3U-Link einfügen"
            else -> "Вставьте M3U ссылку"
        }

        etEpgUrl.hint = when (currentLang) {
            "az" -> "EPG linkini daxil edin (.xml / .gz)"
            "en" -> "Paste EPG link (.xml / .gz)"
            "de" -> "EPG-Link einfügen (.xml / .gz)"
            else -> "Вставьте EPG ссылку (.xml / .gz)"
        }
    }

    private fun getAllCategoryName(): String {
        return when (currentLang) {
            "az" -> "Hamısı"
            "en" -> "All"
            "de" -> "Alle"
            else -> "Все"
        }
    }

    private fun getFavoritesCategoryName(): String {
        return when (currentLang) {
            "az" -> "Seçilmişlər"
            "en" -> "Favorites"
            "de" -> "Favoriten"
            else -> "Избранное"
        }
    }

    private fun getLocalizedText(key: String): String {
        return when (key) {
            "lang_changed" -> when (currentLang) {
                "az" -> "Dil dəyişdirildi: Azərbaycan"
                "en" -> "Language changed: English"
                "de" -> "Sprache geändert: Deutsch"
                else -> "Язык изменён: Русский"
            }
            "exit_prompt" -> when (currentLang) {
                "az" -> "Çıxış üçün bir daha basın"
                "en" -> "Press back again to exit"
                "de" -> "Erneut drücken zum Beenden"
                else -> "Нажмите ещё раз для выхода"
            }
            "channel_not_found" -> when (currentLang) {
                "az" -> "Kanal tapılmadı"
                "en" -> "Channel not found"
                "de" -> "Kanal nicht gefunden"
                else -> "Канал не найден"
            }
            "toast_invalid_m3u" -> when (currentLang) {
                "az" -> "Düzgün M3U URL daxil edin"
                "en" -> "Enter valid M3U URL"
                "de" -> "Gültige M3U-URL eingeben"
                else -> "Введите правильный URL M3U"
            }
            "toast_no_m3u" -> when (currentLang) {
                "az" -> "M3U linki daxil edilməyib"
                "en" -> "No M3U URL provided"
                "de" -> "Keine M3U-URL angegeben"
                else -> "M3U ссылка не введена"
            }
            "toast_invalid_epg" -> when (currentLang) {
                "az" -> "Düzgün EPG URL daxil edin"
                "en" -> "Enter valid EPG URL"
                "de" -> "Gültige EPG-URL eingeben"
                else -> "Введите правильный URL EPG"
            }
            "toast_no_epg" -> when (currentLang) {
                "az" -> "EPG linki daxil edilməyib"
                "en" -> "No EPG URL provided"
                "de" -> "Keine EPG-URL angegeben"
                else -> "EPG ссылка не введена"
            }
            "error_stream" -> when (currentLang) {
                "az" -> "Yayım xətası"
                "en" -> "Playback error"
                "de" -> "Wiedergabefehler"
                else -> "Ошибка воспроизведения"
            }
            else -> ""
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
                        Toast.makeText(applicationContext, getLocalizedText("error_stream"), Toast.LENGTH_SHORT).show()
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

        val dialogButtons = listOf(btnSaveM3u, btnRefreshM3u, btnSaveEpg, btnRefreshEpg, btnCloseSettings)
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
        if (selectedCategory == getFavoritesCategoryName()) {
            filterChannels(getFavoritesCategoryName())
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
            tvSyncChannels.text = "Channels: 0"
            tvSyncLogos.text = "Logotypes: 0"
        }

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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
                    tvSyncChannels.text = "Channels: Xəta"
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
                        tvSyncChannels.text = "Channels: Xəta ${response.code}"
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
                    tvSyncChannels.text = "Channels: ${allChannels.size}"
                    tvSyncLogos.text = "Logotypes: $logosCount"
                    checkAndScheduleHideSyncCard()
                }

                runOnUiThread {
                    setupCategoryAdapter()
                    val allCatName = getAllCategoryName()
                    val catToSelect = if (categories.contains(selectedCategory)) selectedCategory else allCatName
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
        var logosFoundCount = 0

        val tempChannels = mutableListOf<Channel>()
        val tempCategories = LinkedHashSet
