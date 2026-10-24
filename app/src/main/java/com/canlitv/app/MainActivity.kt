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
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class Channel(
    val number: Int,
    val name: String,
    val logoUrl: String,
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
    private lateinit var btnSaveUrl: Button
    private lateinit var btnCloseSettings: Button
    private lateinit var btnOpenSettings: View
    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var tvNumpadDisplay: TextView
    private lateinit var tvBannerNumber: TextView
    private lateinit var tvBannerName: TextView
    private lateinit var tvBannerCategory: TextView

    private val allChannels = mutableListOf<Channel>()
    private val displayedChannels = mutableListOf<Channel>()
    private val categories = mutableListOf<String>()
    private var selectedCategory = "Все"
    private val favoriteUrls = mutableSetOf<String>()

    private val epgMap = ConcurrentHashMap<String, String>()
    private var detectedPlaylistEpgUrl: String = ""

    private var currentPlayingIndexInAll = -1
    private var previousPlayingIndexInAll = -1
    private var doubleBackToExitPressedOnce = false

    private val handler = Handler(Looper.getMainLooper())
    private val digitInputBuffer = StringBuilder()
    private var bannerRunnable: Runnable? = null
    private var digitAutoExecuteRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        channelPanel = findViewById(R.id.channelPanel)
        categoryPanel = findViewById(R.id.categoryPanel)
        numPadPanel = findViewById(R.id.numPadPanel)
        infoBanner = findViewById(R.id.infoBanner)
        linkDialogPanel = findViewById(R.id.linkDialogPanel)
        etM3uUrl = findViewById(R.id.etM3uUrl)
        etEpgUrl = findViewById(R.id.etEpgUrl)
        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        recyclerViewChannels = findViewById(R.id.recyclerViewChannels)
        recyclerViewCategories = findViewById(R.id.recyclerViewCategories)
        tvNumpadDisplay = findViewById(R.id.tvNumpadDisplay)
        tvBannerNumber = findViewById(R.id.tvBannerNumber)
        tvBannerName = findViewById(R.id.tvBannerName)
        tvBannerCategory = findViewById(R.id.tvBannerCategory)

        categoryPanel.setBackgroundColor(Color.parseColor("#800B0E14"))
        channelPanel.setBackgroundColor(Color.parseColor("#800B0E14"))
        infoBanner.setBackgroundColor(Color.parseColor("#880B0E14"))
        numPadPanel.background = GradientDrawable().apply {
            setColor(Color.parseColor("#990B0E14"))
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }

        findViewById<View?>(R.id.btnOpenSearch)?.visibility = View.GONE

        (categoryPanel as? LinearLayout)?.let { panel ->
            panel.removeView(btnOpenSettings)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (52 * resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
            }
            btnOpenSettings.layoutParams = params
            panel.addView(btnOpenSettings)
        }

        loadFavorites()
        initNumpadButtons()

        val settingsDefaultBg = GradientDrawable().apply {
            setColor(Color.parseColor("#551C222E"))
            cornerRadius = 14f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }
        val settingsFocusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#2979FF"))
            cornerRadius = 14f
            setStroke(3, Color.parseColor("#FFFFFF"))
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
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    categoryPanel.visibility = View.GONE
                    channelPanel.visibility = View.VISIBLE
                    focusCurrentChannelCentered()
                    return@setOnKeyListener true
                }
            }
            false
        }

        initPlayer()

        recyclerViewChannels.layoutManager = LinearLayoutManager(this)
        recyclerViewCategories.layoutManager = LinearLayoutManager(this)

        btnSaveUrl.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            val epgUrl = etEpgUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("M3U_URL", url)
                    .putString("EPG_URL", epgUrl)
                    .apply()
                linkDialogPanel.visibility = View.GONE
                currentPlayingIndexInAll = -1
                fetchM3U(url, autoPlay = true)
            } else {
                Toast.makeText(applicationContext, "Введите корректную ссылку M3U", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseSettings.setOnClickListener {
            linkDialogPanel.visibility = View.GONE
        }
    }

    private fun initPlayer() {
        if (player == null) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
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

    override fun onStart() {
        super.onStart()
        initPlayer()
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("M3U_URL", "") ?: ""

        if (savedUrl.isNotEmpty()) {
            fetchM3U(savedUrl, autoPlay = true)
        } else {
            showSettingsDialog()
        }
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
            Toast.makeText(applicationContext, "${channel.name} удален из Избранного", Toast.LENGTH_SHORT).show()
        } else {
            favoriteUrls.add(channel.streamUrl)
            Toast.makeText(applicationContext, "${channel.name} добавлен в Избранное", Toast.LENGTH_SHORT).show()
        }
        saveFavorites()
        if (currentFocusPos != -1) recyclerViewChannels.adapter?.notifyItemChanged(currentFocusPos)
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
            digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
            digitInputBuffer.clear()
            tvNumpadDisplay.text = "---"
        }
        findViewById<Button>(R.id.btnGo).setOnClickListener { executeNumpadSearch() }
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        etM3uUrl.setText(prefs.getString("M3U_URL", ""))
        etEpgUrl.setText(prefs.getString("EPG_URL", ""))
        linkDialogPanel.visibility = View.VISIBLE
        etM3uUrl.requestFocus()
    }

    private fun fetchM3U(url: String, autoPlay: Boolean) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cache(null)
            .build()

        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    if (allChannels.isEmpty()) {
                        Toast.makeText(applicationContext, "Не удалось загрузить плейлист", Toast.LENGTH_LONG).show()
                        showSettingsDialog()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        if (allChannels.isEmpty()) {
                            Toast.makeText(applicationContext, "Ошибка сервера: ${response.code}", Toast.LENGTH_LONG).show()
                            showSettingsDialog()
                        }
                    }
                    return
                }

                val body = response.body?.string() ?: return

                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                val lastPlayedUrl = if (currentPlayingIndexInAll in allChannels.indices) {
                    allChannels[currentPlayingIndexInAll].streamUrl
                } else {
                    prefs.getString("LAST_CHANNEL_URL", "") ?: ""
                }

                parseM3U(body)

                val savedEpg = prefs.getString("EPG_URL", "")?.trim() ?: ""
                val finalEpgUrl = if (savedEpg.isNotEmpty() && savedEpg.startsWith("http")) {
                    savedEpg
                } else {
                    detectedPlaylistEpgUrl
                }

                if (finalEpgUrl.isNotEmpty() && finalEpgUrl.startsWith("http")) {
                    fetchEPG(finalEpgUrl)
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
                            playChannelByAllIndex(resumeIndex)
                        } else {
                            currentPlayingIndexInAll = resumeIndex
                        }
                    } else {
                        Toast.makeText(applicationContext, "Каналы не найдены в плейлисте", Toast.LENGTH_LONG).show()
                        showSettingsDialog()
                    }
                }
            }
        })
    }

    private fun parseM3U(content: String) {
        val lines = content.lines()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentTvgId = ""
        var counter = 1
        detectedPlaylistEpgUrl = ""

        val tempChannels = mutableListOf<Channel>()
        val tempCategories = LinkedHashSet<String>()
        tempCategories.add("Все")
        tempCategories.add("Избранное")

        val groupRegex = Regex("group-title=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val logoRegex = Regex("tvg-logo=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val idRegex = Regex("tvg-id=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val epgHeaderRegex = Regex("""(?:url-tvg|x-tvg-url)\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
                val epgMatch = epgHeaderRegex.find(trimmed)
                val extractedEpg = (epgMatch?.groups?.get(1)?.value ?: epgMatch?.groups?.get(2)?.value ?: "").trim()
                if (extractedEpg.isNotEmpty()) {
                    detectedPlaylistEpgUrl = extractedEpg.split(",").first().trim()
                }
            } else if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
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
                currentName = ""; currentLogo = ""; currentGroup = ""; currentTvgId = ""
            }
        }

        allChannels.clear()
        allChannels.addAll(tempChannels)
        categories.clear()
        categories.addAll(tempCategories)
    }

    private fun fetchEPG(epgUrl: String) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .cache(null)
            .build()

        val request = Request.Builder()
            .url(epgUrl)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) return
                val bodyStream = response.body?.byteStream() ?: return
                try {
                    val isGzip = epgUrl.endsWith(".gz", ignoreCase = true) ||
                            response.header("Content-Encoding")?.contains("gzip", ignoreCase = true) == true
                    val inputStream: InputStream = if (isGzip) GZIPInputStream(bodyStream) else bodyStream
                    parseXmlTvStream(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        bodyStream.close()
                    } catch (_: Exception) {}
                }
            }
        })
    }

    private fun parseXmlTvStream(inputStream: InputStream) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        val now = System.currentTimeMillis()
        val tempEpg = HashMap<String, String>()

        var eventType = parser.eventType
        var currentChannelId = ""
        var inChannel = false
        var inProgramme = false
        var progChannel = ""
        var isCurrentProg = false
        var progTitle = ""

        fun cleanKey(text: String): String {
            return text.lowercase(Locale.ROOT)
                .replace(Regex("""\b(hd|sd|ru|ua|int|+1|+2|4k|ussr|iptv)\b"""), "")
                .replace(Regex("""[^a-z0-9а-яё]"""), "")
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            inChannel = true
                            currentChannelId = parser.getAttributeValue(null, "id")?.trim() ?: ""
                        }
                        "display-name" -> {
                            if (inChannel && currentChannelId.isNotEmpty()) {
                                val dName = parser.nextText().trim()
                                if (dName.isNotEmpty()) {
                                    tempEpg[cleanKey(dName)] = ""
                                    tempEpg[currentChannelId.lowercase(Locale.ROOT)] = dName
                                }
                            }
                        }
                        "programme" -> {
                            val startStr = parser.getAttributeValue(null, "start") ?: ""
                            val stopStr = parser.getAttributeValue(null, "stop") ?: ""
                            progChannel = parser.getAttributeValue(null, "channel")?.trim() ?: ""
                            val startMs = parseXmlTvTime(startStr)
                            val stopMs = parseXmlTvTime(stopStr)
                            if (now in startMs..stopMs && progChannel.isNotEmpty()) {
                                inProgramme = true
                                isCurrentProg = true
                                progTitle = ""
                            }
                        }
                        "title" -> {
                            if (inProgramme && isCurrentProg) {
                                progTitle = parser.nextText().trim()
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            inChannel = false
                            currentChannelId = ""
                        }
                        "programme" -> {
                            if (inProgramme && isCurrentProg && progTitle.isNotEmpty() && progChannel.isNotEmpty()) {
                                tempEpg[progChannel.lowercase(Locale.ROOT) + "_prog"] = progTitle
                            }
                            inProgramme = false
                            isCurrentProg = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val finalEpg = HashMap<String, String>()
        for ((key, value) in tempEpg) {
            if (key.endsWith("_prog")) {
                val chId = key.removeSuffix("_prog")
                val realName = tempEpg[chId]
                if (realName != null) {
                    finalEpg[cleanKey(realName)] = value
                }
                finalEpg[chId] = value
            }
        }

        epgMap.clear()
        epgMap.putAll(finalEpg)

        runOnUiThread {
            recyclerViewChannels.adapter?.notifyDataSetChanged()
            if (infoBanner.visibility == View.VISIBLE && currentPlayingIndexInAll in allChannels.indices) {
                showInfoBanner(allChannels[currentPlayingIndexInAll])
            }
        }
    }

    private fun parseXmlTvTime(timeStr: String): Long {
        if (timeStr.length < 14) return 0L
        return try {
            val clean = timeStr.trim()
            val format = if (clean.length >= 19 && (clean.contains("+") || clean.contains("-"))) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            }
            format.parse(clean)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun getProgramForChannel(ch: Channel): String {
        if (epgMap.isEmpty()) return ""
        
        fun cleanKey(text: String): String {
            return text.lowercase(Locale.ROOT)
                .replace(Regex("""\b(hd|sd|ru|ua|int|+1|+2|4k|ussr|iptv)\b"""), "")
                .replace(Regex("""[^a-z0-9а-яё]"""), "")
        }

        if (ch.tvgId.isNotEmpty()) {
            epgMap[ch.tvgId.lowercase(Locale.ROOT)]?.let { return it }
        }
        epgMap[cleanKey(ch.name)]?.let { return it }
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
        if (allChannels.isEmpty() || currentPlayingIndexInAll !in allChannels.indices) {
            filterChannels(selectedCategory)
            recyclerViewChannels.post { recyclerViewChannels.requestFocus() }
            return
        }
        val currentChannel = allChannels[currentPlayingIndexInAll]
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
        if (allChannels.isEmpty() || currentPlayingIndexInAll !in allChannels.indices) {
            recyclerViewChannels.post { recyclerViewChannels.requestFocus() }
            return
        }
        val currentChannel = allChannels[currentPlayingIndexInAll]
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

                val catFocusedBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2979FF"))
                    cornerRadius = 14f
                    setStroke(3, Color.parseColor("#FFFFFF"))
                }
                val catDefaultBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#4D151A22"))
                    cornerRadius = 14f
                }

                row.background = catDefaultBg

                row.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        row.background = catFocusedBg
                        titleView.setTextColor(Color.WHITE)
                        iconView.setTextColor(Color.WHITE)
                        arrowView.setTextColor(Color.WHITE)
                    } else {
                        row.background = catDefaultBg
                        titleView.setTextColor(Color.parseColor("#ECEFF1"))
                        iconView.setTextColor(Color.parseColor("#90A4AE"))
                        arrowView.setTextColor(Color.parseColor("#90A4AE"))
                    }
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
                        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
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

                tvProg?.text = ""
                tvProg?.visibility = View.GONE

                if (favoriteUrls.contains(ch.streamUrl)) {
                    tvFavStar?.visibility = View.VISIBLE
                } else {
                    tvFavStar?.visibility = View.GONE
                }

                if (ch.logoUrl.isNotEmpty()) {
                    imgLogo.visibility = View.VISIBLE
                    Glide.with(this@MainActivity).load(ch.logoUrl).into(imgLogo)
                } else {
                    imgLogo.visibility = View.GONE
                }

                val cardFocusedBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2979FF"))
                    cornerRadius = 14f
                    setStroke(3, Color.parseColor("#FFFFFF"))
                }
                val cardDefaultBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#4D151A22"))
                    cornerRadius = 14f
                }

                row.background = cardDefaultBg

                row.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        row.background = cardFocusedBg
                        tvNum.setTextColor(Color.WHITE)
                        tvName.setTextColor(Color.WHITE)
                    } else {
                        row.background = cardDefaultBg
                        tvNum.setTextColor(Color.parseColor("#B0BEC5"))
                        tvName.setTextColor(Color.parseColor("#FFFFFF"))
                    }
                }

                row.setOnClickListener {
                    val allIndex = allChannels.indexOf(ch)
                    if (allIndex != -1) playChannelByAllIndex(allIndex)
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

    private fun playChannelByAllIndex(index: Int) {
        if (index !in allChannels.indices) return
        
        if (currentPlayingIndexInAll != -1 && currentPlayingIndexInAll != index) {
            previousPlayingIndexInAll = currentPlayingIndexInAll
        }

        currentPlayingIndexInAll = index
        val channel = allChannels[index]

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

    private fun showInfoBanner(channel: Channel) {
        tvBannerNumber.text = channel.number.toString()
        tvBannerName.text = channel.name
        val isFav = if (favoriteUrls.contains(channel.streamUrl)) " ★" else ""
        
        val currentProg = getProgramForChannel(channel)
        val progText = if (currentProg.isNotEmpty()) "  |  🔹 $currentProg" else ""

        tvBannerCategory.text = "● ${channel.category.uppercase()}$isFav$progText"

        infoBanner.visibility = View.VISIBLE
        bannerRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { infoBanner.visibility = View.GONE }
        bannerRunnable = r
        handler.postDelayed(r, 4000)
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
                if (previousPlayingIndexInAll in allChannels.indices && previousPlayingIndexInAll != currentPlayingIndexInAll) {
                    playChannelByAllIndex(previousPlayingIndexInAll)
                    return true
                }

                if (doubleBackToExitPressedOnce) {
                    exitApplicationCompletely()
                    return true
                }

                this.doubleBackToExitPressedOnce = true
                Toast.makeText(this, "Нажмите еще раз для выхода", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ doubleBackToExitPressedOnce = false }, 2500)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (allChannels.isNotEmpty() && currentPlayingIndexInAll in allChannels.indices) {
                    showInfoBanner(allChannels[currentPlayingIndexInAll])
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (allChannels.isNotEmpty()) { playChannelByAllIndex((currentPlayingIndexInAll + 1) % allChannels.size); return true }
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (allChannels.isNotEmpty()) {
                    playChannelByAllIndex(if (currentPlayingIndexInAll - 1 < 0) allChannels.size - 1 else currentPlayingIndexInAll - 1)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { openChannelPanelCenteredOnCurrent(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                numPadPanel.visibility = View.VISIBLE; digitInputBuffer.clear(); tvNumpadDisplay.text = "---"
                findViewById<Button>(R.id.btn1).post { findViewById<Button>(R.id.btn1).requestFocus() }
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
            findViewById<Button>(R.id.btn1).post { findViewById<Button>(R.id.btn1).requestFocus() }
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
            if (foundIndex != -1) { playChannelByAllIndex(foundIndex); numPadPanel.visibility = View.GONE }
            else { Toast.makeText(applicationContext, "Не найден", Toast.LENGTH_SHORT).show(); tvNumpadDisplay.text = "---" }
        }
        digitInputBuffer.clear()
    }
}
