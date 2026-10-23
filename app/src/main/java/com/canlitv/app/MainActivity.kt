package com.livetv.hajiyev

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Channel(
    val number: Int,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String,
    val tvgId: String = ""
)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
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
    private lateinit var btnOpenSearch: View
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

    private var detectedTimeZone: TimeZone = TimeZone.getTimeZone("GMT+04:00")
    private var currentPlayingIndexInAll = 0
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
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
        btnOpenSearch = findViewById(R.id.btnOpenSearch)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        recyclerViewChannels = findViewById(R.id.recyclerViewChannels)
        recyclerViewCategories = findViewById(R.id.recyclerViewCategories)
        tvNumpadDisplay = findViewById(R.id.tvNumpadDisplay)
        tvBannerNumber = findViewById(R.id.tvBannerNumber)
        tvBannerName = findViewById(R.id.tvBannerName)
        tvBannerCategory = findViewById(R.id.tvBannerCategory)

        loadFavorites()
        initNumpadButtons()
        detectTimeZoneByIpHttps()

        btnOpenSearch.setOnClickListener {
            categoryPanel.visibility = View.GONE
            channelPanel.visibility = View.GONE
            numPadPanel.visibility = View.VISIBLE
            digitInputBuffer.clear()
            tvNumpadDisplay.text = "---"
            findViewById<Button>(R.id.btn1).post { findViewById<Button>(R.id.btn1).requestFocus() }
        }

        btnOpenSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                btnOpenSearch.performClick()
                return@setOnKeyListener true
            }
            false
        }

        btnOpenSettings.setOnClickListener {
            categoryPanel.visibility = View.GONE
            channelPanel.visibility = View.GONE
            showSettingsDialog()
        }

        btnOpenSettings.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                btnOpenSettings.setBackgroundColor(Color.WHITE)
                (btnOpenSettings as LinearLayout).getChildAt(0)?.let { (it as? TextView)?.setTextColor(Color.parseColor("#1E232B")) }
                (btnOpenSettings as LinearLayout).getChildAt(1)?.let { (it as? TextView)?.setTextColor(Color.parseColor("#1E232B")) }
                (btnOpenSettings as LinearLayout).getChildAt(2)?.let { (it as? TextView)?.setTextColor(Color.parseColor("#1E232B")) }
            } else {
                btnOpenSettings.setBackgroundColor(Color.parseColor("#252A34"))
                (btnOpenSettings as LinearLayout).getChildAt(0)?.let { (it as? TextView)?.setTextColor(Color.parseColor("#D8DEE9")) }
                (btnOpenSettings as LinearLayout).getChildAt(1)?.let { (it as? TextView)?.setTextColor(Color.WHITE) }
                (btnOpenSettings as LinearLayout).getChildAt(2)?.let { (it as? TextView)?.setTextColor(Color.parseColor("#78909C")) }
            }
        }

        btnOpenSettings.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                btnOpenSettings.performClick()
                return@setOnKeyListener true
            }
            false
        }

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
                runOnUiThread {
                    Toast.makeText(applicationContext, "Ошибка воспроизведения потока", Toast.LENGTH_SHORT).show()
                }
            }
        })

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
                fetchM3U(url, autoPlayFirst = true)
            } else {
                Toast.makeText(applicationContext, "Введите корректную ссылку M3U", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseSettings.setOnClickListener {
            linkDialogPanel.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("M3U_URL", "") ?: ""

        if (savedUrl.isNotEmpty()) {
            val isFirstLaunch = allChannels.isEmpty()
            fetchM3U(savedUrl, autoPlayFirst = isFirstLaunch)
        } else {
            showSettingsDialog()
        }
    }

    private fun detectTimeZoneByIpHttps() {
        val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("https://ipwho.is/").header("User-Agent", "Mozilla/5.0").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: return
                    val json = JSONObject(body)
                    if (json.optBoolean("success", false)) {
                        val tzId = json.optJSONObject("timezone")?.optString("id", "") ?: ""
                        if (tzId.isNotEmpty()) {
                            detectedTimeZone = TimeZone.getTimeZone(tzId)
                            TimeZone.setDefault(detectedTimeZone)
                        }
                    }
                } catch (_: Exception) {}
            }
        })
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

    private fun fetchM3U(url: String, autoPlayFirst: Boolean) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", "VLC/3.0.18")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    if (allChannels.isEmpty()) {
                        Toast.makeText(applicationContext, "Ошибка загрузки плейлиста", Toast.LENGTH_SHORT).show()
                        showSettingsDialog()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStream = response.body?.byteStream() ?: return

                backgroundExecutor.execute {
                    val tempChannels = mutableListOf<Channel>()
                    val tempCategories = mutableSetOf<String>()
                    tempCategories.add("Все")
                    tempCategories.add("Избранное")

                    try {
                        val reader = BufferedReader(bodyStream.reader(Charsets.UTF_8))
                        var line: String?
                        var currentName = ""
                        var currentLogo = ""
                        var currentGroup = "Общие"
                        var currentTvgId = ""
                        var counter = 1

                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line!!.trim()
                            if (trimmed.startsWith("#EXTINF")) {
                                currentLogo = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)?.groups?.get(1)?.value ?: ""
                                currentTvgId = Regex("tvg-id=\"([^\"]+)\"").find(trimmed)?.groups?.get(1)?.value ?: ""
                                currentGroup = Regex("group-title=\"([^\"]+)\"").find(trimmed)?.groups?.get(1)?.value ?: "Общие"
                                if (currentGroup.isNotEmpty()) tempCategories.add(currentGroup)
                                currentName = trimmed.substringAfterLast(",").trim()
                            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                val name = if (currentName.isNotEmpty()) currentName else "Канал $counter"
                                tempChannels.add(
                                    Channel(
                                        number = counter,
                                        name = name,
                                        logoUrl = currentLogo,
                                        streamUrl = trimmed,
                                        category = currentGroup,
                                        tvgId = currentTvgId.lowercase(Locale.ROOT)
                                    )
                                )
                                counter++
                                currentName = ""; currentLogo = ""; currentGroup = "Общие"; currentTvgId = ""
                            }
                        }
                        reader.close()
                    } catch (_: Exception) {}

                    runOnUiThread {
                        allChannels.clear()
                        allChannels.addAll(tempChannels)
                        categories.clear()
                        categories.addAll(tempCategories)

                        setupCategoryAdapter()
                        filterChannels("Все")

                        if (allChannels.isNotEmpty() && autoPlayFirst) {
                            playChannelByAllIndex(0)
                        } else if (allChannels.isEmpty()) {
                            Toast.makeText(applicationContext, "Каналы не найдены в плейлисте", Toast.LENGTH_SHORT).show()
                            showSettingsDialog()
                        }
                    }
                }
            }
        })
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
        if (allChannels.isEmpty() || currentPlayingIndexInAll !in allChannels.indices) return
        val currentChannel = allChannels[currentPlayingIndexInAll]
        val targetPos = displayedChannels.indexOfFirst { it.number == currentChannel.number }.coerceAtLeast(0)

        recyclerViewChannels.post {
            val layoutManager = recyclerViewChannels.layoutManager as? LinearLayoutManager ?: return@post
            val itemHeightPx = (72 * resources.displayMetrics.density).toInt()
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
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 4, 0, 6) }
                    setPadding(16, 12, 16, 12)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    isFocusable = true; isClickable = true
                }
                val iconView = TextView(this@MainActivity).apply { id = View.generateViewId(); textSize = 17f }
                val titleView = TextView(this@MainActivity).apply { id = View.generateViewId(); textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 14 } }
                val arrowView = TextView(this@MainActivity).apply { id = View.generateViewId(); text = "›"; textSize = 20f }
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
                iconView.text = when (cat) { "Все" -> "☰"; "Избранное" -> "★"; else -> "📁" }

                val whiteBg = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 24f }
                val transparentBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }

                row.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        row.background = whiteBg
                        titleView.setTextColor(Color.parseColor("#1E232B"))
                        iconView.setTextColor(Color.parseColor("#1E232B"))
                        arrowView.text = "••• ›"
                        arrowView.setTextColor(Color.parseColor("#4CAF50"))
                    } else {
                        row.background = transparentBg
                        titleView.setTextColor(Color.parseColor("#D8DEE9"))
                        iconView.setTextColor(Color.parseColor("#78909C"))
                        arrowView.text = "›"
                        arrowView.setTextColor(Color.parseColor("#78909C"))
                    }
                }

                row.setOnClickListener {
                    filterChannels(cat)
                    categoryPanel.visibility = View.GONE
                    channelPanel.visibility = View.VISIBLE
                    focusCurrentChannelCentered()
                }

                row.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        categoryPanel.visibility = View.GONE
                        channelPanel.visibility = View.VISIBLE
                        focusCurrentChannelCentered()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
            override fun getItemCount() = categories.size
        }
    }

    private fun setupChannelAdapter() {
        recyclerViewChannels.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(layoutInflater.inflate(R.layout.item_channel, parent, false)) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ch = displayedChannels[position]
                val row = holder.itemView.findViewById<LinearLayout>(R.id.channelRowLayout)
                val tvNum = holder.itemView.findViewById<TextView>(R.id.tvNumber)
                val imgLogo = holder.itemView.findViewById<ImageView>(R.id.imgLogo)
                val tvName = holder.itemView.findViewById<TextView>(R.id.tvName)
                val tvProg = holder.itemView.findViewById<TextView>(R.id.tvProgramInfo)

                tvNum.text = ch.number.toString()
                tvName.text = ch.name
                tvProg.text = "Канал №${ch.number}"

                if (ch.logoUrl.isNotEmpty()) {
                    imgLogo.visibility = View.VISIBLE
                    Glide.with(this@MainActivity).load(ch.logoUrl).into(imgLogo)
                } else {
                    imgLogo.visibility = View.GONE
                }

                val cardFocusedBg = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 14f }
                val cardDefaultBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }

                row.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        row.background = cardFocusedBg
                        tvNum.setTextColor(Color.parseColor("#1E232B"))
                        tvName.setTextColor(Color.parseColor("#1E232B"))
                        tvProg.setTextColor(Color.parseColor("#5A6578"))
                    } else {
                        row.background = cardDefaultBg
                        tvNum.setTextColor(Color.parseColor("#E0E0E0"))
                        tvName.setTextColor(Color.WHITE)
                        tvProg.setTextColor(Color.parseColor("#78909C"))
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
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            channelPanel.visibility = View.GONE
                            categoryPanel.visibility = View.VISIBLE
                            val catIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
                            recyclerViewCategories.post {
                                recyclerViewCategories.scrollToPosition(catIndex)
                                recyclerViewCategories.findViewHolderForAdapterPosition(catIndex)?.itemView?.requestFocus() ?: recyclerViewCategories.requestFocus()
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
        currentPlayingIndexInAll = index
        val channel = allChannels[index]

        val streamUri = Uri.parse(channel.streamUrl)
        val mediaItem = if (channel.streamUrl.contains(".m3u8", ignoreCase = true)) {
            MediaItem.Builder().setUri(streamUri).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        } else {
            MediaItem.fromUri(streamUri)
        }

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        showInfoBanner(channel)
    }

    private fun showInfoBanner(channel: Channel) {
        tvBannerNumber.text = channel.number.toString()
        tvBannerName.text = channel.name
        val isFav = if (favoriteUrls.contains(channel.streamUrl)) " ★ ИЗБРАННОЕ" else ""
        tvBannerCategory.text = "● ${channel.category.uppercase()}$isFav"

        infoBanner.visibility = View.VISIBLE
        bannerRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { infoBanner.visibility = View.GONE }
        bannerRunnable = r
        handler.postDelayed(r, 4000)
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
            if (keyCode == KeyEvent.KEYCODE_BACK) { categoryPanel.visibility = View.GONE; return true }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                categoryPanel.visibility = View.GONE; channelPanel.visibility = View.VISIBLE; focusCurrentChannelCentered(); return true
            }
        }
        if (channelPanel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                channelPanel.visibility = View.GONE; categoryPanel.visibility = View.VISIBLE
                recyclerViewCategories.post { recyclerViewCategories.requestFocus() }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BACK) {
                channelPanel.visibility = View.GONE; return true
            }
        }

        when (keyCode) {
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
            else { Toast.makeText(applicationContext, "Канал $targetNumber не найден", Toast.LENGTH_SHORT).show(); tvNumpadDisplay.text = "Не найден" }
        }
        digitInputBuffer.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        player.release()
    }
}
