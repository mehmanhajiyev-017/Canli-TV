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
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

data class Channel(val number: Int, val name: String, val logoUrl: String, val streamUrl: String, val category: String)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelPanel: View
    private lateinit var categoryPanel: View
    private lateinit var numPadPanel: View
    private lateinit var infoBanner: View
    private lateinit var linkDialogPanel: View
    private lateinit var etM3uUrl: EditText
    private lateinit var btnSaveUrl: Button
    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var tvTotalChannels: TextView
    private lateinit var tvTotalCategories: TextView
    private lateinit var tvCurrentCategoryTitle: TextView
    private lateinit var tvNumpadDisplay: TextView
    private lateinit var tvBannerNumber: TextView
    private lateinit var tvBannerName: TextView
    private lateinit var tvBannerCategory: TextView
    private lateinit var imgBannerLogo: ImageView

    private val allChannels = mutableListOf<Channel>()
    private val displayedChannels = mutableListOf<Channel>()
    private val categories = mutableListOf<String>()
    private var selectedCategory = "Hamısı"
    private val favoriteUrls = mutableSetOf<String>()

    private var currentPlayingIndexInAll = 0
    private val digitInputBuffer = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
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
        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        recyclerViewChannels = findViewById(R.id.recyclerViewChannels)
        recyclerViewCategories = findViewById(R.id.recyclerViewCategories)
        tvTotalChannels = findViewById(R.id.tvTotalChannels)
        tvTotalCategories = findViewById(R.id.tvTotalCategories)
        tvCurrentCategoryTitle = findViewById(R.id.tvCurrentCategoryTitle)
        tvNumpadDisplay = findViewById(R.id.tvNumpadDisplay)
        tvBannerNumber = findViewById(R.id.tvBannerNumber)
        tvBannerName = findViewById(R.id.tvBannerName)
        tvBannerCategory = findViewById(R.id.tvBannerCategory)
        imgBannerLogo = findViewById(R.id.imgBannerLogo)

        loadFavorites()
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

        recyclerViewChannels.layoutManager = LinearLayoutManager(this)
        recyclerViewCategories.layoutManager = LinearLayoutManager(this)

        btnSaveUrl.setOnClickListener {
            val url = etM3uUrl.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("http")) {
                val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
                prefs.edit().putString("M3U_URL", url).apply()
                linkDialogPanel.visibility = View.GONE
                fetchM3U(url, autoPlayFirst = true)
            } else {
                Toast.makeText(applicationContext, "Düzgün link daxil edin", Toast.LENGTH_SHORT).show()
            }
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
            showLinkDialog()
        }
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
        if (favoriteUrls.contains(channel.streamUrl)) {
            favoriteUrls.remove(channel.streamUrl)
            Toast.makeText(applicationContext, "${channel.name} Seçilmişlərdən silindi", Toast.LENGTH_SHORT).show()
        } else {
            favoriteUrls.add(channel.streamUrl)
            Toast.makeText(applicationContext, "${channel.name} ★ Seçilmişlərə əlavə olundu", Toast.LENGTH_SHORT).show()
        }
        saveFavorites()
        filterChannels(selectedCategory)
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
        findViewById<Button>(R.id.btnGo).setOnClickListener {
            executeNumpadSearch()
        }
    }

    private fun showLinkDialog() {
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        etM3uUrl.setText(prefs.getString("M3U_URL", ""))
        linkDialogPanel.visibility = View.VISIBLE
        etM3uUrl.requestFocus()
    }

    private fun fetchM3U(url: String, autoPlayFirst: Boolean) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    if (allChannels.isEmpty()) {
                        Toast.makeText(applicationContext, "Pleylist yenilənmədi", Toast.LENGTH_SHORT).show()
                        showLinkDialog()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                allChannels.clear()
                categories.clear()
                categories.add("Hamısı")
                categories.add("★ Seçilmişlər")

                parseM3U(body)

                runOnUiThread {
                    tvTotalCategories.text = "${categories.size} qrup mövcuddur"
                    setupCategoryAdapter()
                    filterChannels("Hamısı")

                    if (allChannels.isNotEmpty()) {
                        if (autoPlayFirst) {
                            playChannelByAllIndex(0)
                        }
                    } else {
                        Toast.makeText(applicationContext, "Kanal tapılmadı", Toast.LENGTH_SHORT).show()
                        showLinkDialog()
                    }
                }
            }
        })
    }

    private fun parseM3U(content: String) {
        val lines = content.lines()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "Ümumi"
        var counter = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")
                currentLogo = logoRegex.find(trimmed)?.groups?.get(1)?.value ?: ""

                val groupRegex = Regex("group-title=\"([^\"]+)\"")
                currentGroup = groupRegex.find(trimmed)?.groups?.get(1)?.value ?: "Ümumi"
                if (currentGroup.isNotEmpty() && !categories.contains(currentGroup)) {
                    categories.add(currentGroup)
                }

                currentName = trimmed.substringAfterLast(",").trim()
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val name = if (currentName.isNotEmpty()) currentName else "Kanal $counter"
                allChannels.add(Channel(counter, name, currentLogo, trimmed, currentGroup))
                counter++
                currentName = ""
                currentLogo = ""
                currentGroup = "Ümumi"
            }
        }
    }

    private fun filterChannels(category: String) {
        selectedCategory = category
        tvCurrentCategoryTitle.text = category
        displayedChannels.clear()

        when (category) {
            "Hamısı" -> displayedChannels.addAll(allChannels)
            "★ Seçilmişlər" -> displayedChannels.addAll(allChannels.filter { favoriteUrls.contains(it.streamUrl) })
            else -> displayedChannels.addAll(allChannels.filter { it.category == category })
        }

        tvTotalChannels.text = "${displayedChannels.size} kanal mövcuddur"
        setupChannelAdapter()
    }

    private fun setupCategoryAdapter() {
        recyclerViewCategories.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(this@MainActivity).apply {
                    setPadding(20, 18, 20, 18)
                    textSize = 17f
                    isFocusable = true
                    isClickable = true
                    setBackgroundResource(android.R.drawable.btn_default)
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val cat = categories[position]
                val tv = holder.itemView as TextView
                tv.text = cat
                if (cat == selectedCategory) {
                    tv.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
                } else {
                    tv.setTextColor(android.graphics.Color.WHITE)
                }

                // Qrupu seçəndə birbaşa həmin qrupun kanallarını açır və fokusu kanallara verir
                tv.setOnClickListener {
                    openChannelPanelWithCategory(cat)
                }
            }

            override fun getItemCount() = categories.size
        }
    }

    private fun openChannelPanelWithCategory(cat: String) {
        filterChannels(cat)
        categoryPanel.visibility = View.GONE
        channelPanel.visibility = View.VISIBLE
        recyclerViewChannels.requestFocus()
    }

    private fun setupChannelAdapter() {
        recyclerViewChannels.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = layoutInflater.inflate(R.layout.item_channel, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ch = displayedChannels[position]
                holder.itemView.findViewById<TextView>(R.id.tvNumber).text = String.format("%03d", ch.number)
                holder.itemView.findViewById<TextView>(R.id.tvName).text = ch.name
                val logoImg = holder.itemView.findViewById<ImageView>(R.id.imgLogo)
                val favIcon = holder.itemView.findViewById<TextView>(R.id.tvFavIcon)

                favIcon.visibility = if (favoriteUrls.contains(ch.streamUrl)) View.VISIBLE else View.GONE

                if (ch.logoUrl.isNotEmpty()) {
                    Glide.with(this@MainActivity).load(ch.logoUrl).into(logoImg)
                } else {
                    logoImg.setImageDrawable(null)
                }

                holder.itemView.setOnClickListener {
                    val allIndex = allChannels.indexOf(ch)
                    if (allIndex != -1) {
                        playChannelByAllIndex(allIndex)
                    }
                    channelPanel.visibility = View.GONE
                }

                holder.itemView.setOnLongClickListener {
                    toggleFavorite(ch)
                    true
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
            MediaItem.Builder()
                .setUri(streamUri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        } else {
            MediaItem.fromUri(streamUri)
        }

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        showInfoBanner(channel)
    }

    private fun showInfoBanner(channel: Channel) {
        tvBannerNumber.text = String.format("%03d", channel.number)
        tvBannerName.text = channel.name
        val isFav = if (favoriteUrls.contains(channel.streamUrl)) " ★ SEÇİLMİŞ" else ""
        tvBannerCategory.text = "● ${channel.category.uppercase()}$isFav"

        if (channel.logoUrl.isNotEmpty()) {
            Glide.with(this).load(channel.logoUrl).into(imgBannerLogo)
        } else {
            imgBannerLogo.setImageDrawable(null)
        }

        infoBanner.visibility = View.VISIBLE
        bannerRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { infoBanner.visibility = View.GONE }
        bannerRunnable = r
        handler.postDelayed(r, 4000)
    }

    // ƏSAS PULT İDARƏETMƏSİ: Sola basdıqca pilləli keçid
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (linkDialogPanel.visibility == View.VISIBLE) {
            return super.onKeyDown(keyCode, event)
        }

        // 1. Rəqəmli axtarış açıqdırsa
        if (numPadPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
                    digitInputBuffer.clear()
                    numPadPanel.visibility = View.GONE
                    return true
                }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    val digit = keyCode - KeyEvent.KEYCODE_0
                    appendNumpadDigit(digit)
                    return true
                }
                else -> return super.onKeyDown(keyCode, event)
            }
        }

        // 2. Əgər KATEQORİYALAR paneli açıqdırsa:
        if (categoryPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    categoryPanel.visibility = View.GONE
                    return true
                }
                // Sağa basanda yenidən kanallar panelinə qayıdır
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    categoryPanel.visibility = View.GONE
                    channelPanel.visibility = View.VISIBLE
                    recyclerViewChannels.requestFocus()
                    return true
                }
                else -> return super.onKeyDown(keyCode, event)
            }
        }

        // 3. Əgər KANALLAR paneli açıqdırsa:
        if (channelPanel.visibility == View.VISIBLE) {
            when (keyCode) {
                // Kanal siyahısındaykən SOLA basanda: KATEQORİYALAR açılır!
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    channelPanel.visibility = View.GONE
                    categoryPanel.visibility = View.VISIBLE
                    recyclerViewCategories.requestFocus()
                    val catIndex = categories.indexOf(selectedCategory)
                    if (catIndex != -1) recyclerViewCategories.scrollToPosition(catIndex)
                    return true
                }
                // Kanal siyahısındaykən SAĞA basanda və ya GERİ basanda: Menyu bağlanır
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK -> {
                    channelPanel.visibility = View.GONE
                    return true
                }
                else -> return super.onKeyDown(keyCode, event)
            }
        }

        // 4. Normal Yayım zamanı:
        when (keyCode) {
            // OK basanda cari kanal məlumatı
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (allChannels.isNotEmpty() && currentPlayingIndexInAll in allChannels.indices) {
                    showInfoBanner(allChannels[currentPlayingIndexInAll])
                    return true
                }
            }

            // Yuxarı: Sonrakı kanal
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (allChannels.isNotEmpty()) {
                    val nextIndex = (currentPlayingIndexInAll + 1) % allChannels.size
                    playChannelByAllIndex(nextIndex)
                    return true
                }
            }

            // Aşağı: Əvvəlki kanal
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (allChannels.isNotEmpty()) {
                    val prevIndex = if (currentPlayingIndexInAll - 1 < 0) allChannels.size - 1 else currentPlayingIndexInAll - 1
                    playChannelByAllIndex(prevIndex)
                    return true
                }
            }

            // 1-ci DƏFƏ SOLA: Kanallar siyahısını açır
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                channelPanel.visibility = View.VISIBLE
                filterChannels(selectedCategory)
                recyclerViewChannels.requestFocus()
                return true
            }

            // SAĞA: Rəqəmli axtarış paneli
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                numPadPanel.visibility = View.VISIBLE
                digitInputBuffer.clear()
                tvNumpadDisplay.text = "---"
                findViewById<Button>(R.id.btn1).requestFocus()
                return true
            }

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val digit = keyCode - KeyEvent.KEYCODE_0
                appendNumpadDigit(digit)
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
            numPadPanel.visibility = View.VISIBLE
            findViewById<Button>(R.id.btn1).requestFocus()
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
                playChannelByAllIndex(foundIndex)
                numPadPanel.visibility = View.GONE
            } else {
                Toast.makeText(applicationContext, "Kanal $targetNumber tapılmadı", Toast.LENGTH_SHORT).show()
                tvNumpadDisplay.text = "Tapılmadı"
            }
        }
        digitInputBuffer.clear()
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showLinkDialog()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
