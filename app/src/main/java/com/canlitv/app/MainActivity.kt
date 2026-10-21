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

    // Hər dəfə tətbiq açılanda və ya ön plana gələndə avtomatik pleylisti təzələyir
    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("TV_SETTINGS", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("M3U_URL", "") ?: ""

        if (savedUrl.isNotEmpty()) {
            val isFirstLaunch = channelList.isEmpty()
            fetchM3U(savedUrl, autoPlayFirst = isFirstLaunch)
        } else {
            showLinkDialog()
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

        // Keş saxlamadan birbaşa ən son yenilənmiş pleylisti çəkir
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    if (channelList.isEmpty()) {
                        Toast.makeText(applicationContext, "Pleylist yenilənmədi", Toast.LENGTH_SHORT).show()
                        showLinkDialog()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                channelList.clear()
                parseM3U(body)
                runOnUiThread {
                    tvTotalChannels.text = "${channelList.size} kanal mövcuddur"
                    setupAdapter()
                    if (channelList.isNotEmpty()) {
                        if (autoPlayFirst) {
                            playChannel(0)
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
        var counter = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")
                currentLogo = logoRegex.find(trimmed)?.groups?.get(1)?.value ?: ""
                currentName = trimmed.substringAfterLast(",").trim()
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val name = if (currentName.isNotEmpty()) currentName else "Kanal $counter"
                channelList.add(Channel(counter, name, currentLogo, trimmed))
                counter++
                currentName = ""
                currentLogo = ""
            }
        }
    }

    private fun setupAdapter() {
        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = layoutInflater.inflate(R.layout.item_channel, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ch = channelList[position]
                holder.itemView.findViewById<TextView>(R.id.tvNumber).text = String.format("%03d", ch.number)
                holder.itemView.findViewById<TextView>(R.id.tvName).text = ch.name
                val logoImg = holder.itemView.findViewById<ImageView>(R.id.imgLogo)

                if (ch.logoUrl.isNotEmpty()) {
                    Glide.with(this@MainActivity).load(ch.logoUrl).into(logoImg)
                }

                holder.itemView.setOnClickListener {
                    playChannel(position)
                    channelPanel.visibility = View.GONE
                }
            }

            override fun getItemCount() = channelList.size
        }
    }

    private fun playChannel(index: Int) {
        if (index !in channelList.indices) return
        currentChannelIndex = index
        val channel = channelList[index]

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
        if (channel.logoUrl.isNotEmpty()) {
            Glide.with(this).load(channel.logoUrl).into(imgBannerLogo)
        } else {
            imgBannerLogo.setImageDrawable(null)
        }

        infoBanner.visibility = View.VISIBLE
        bannerRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { infoBanner.visibility = View.GONE }
        bannerRunnable = r
        handler.postDelayed(r, 3500)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (linkDialogPanel.visibility == View.VISIBLE) {
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (channelPanel.visibility != View.VISIBLE && numPadPanel.visibility != View.VISIBLE && channelList.isNotEmpty()) {
                    val nextIndex = (currentChannelIndex + 1) % channelList.size
                    playChannel(nextIndex)
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (channelPanel.visibility != View.VISIBLE && numPadPanel.visibility != View.VISIBLE && channelList.isNotEmpty()) {
                    val prevIndex = if (currentChannelIndex - 1 < 0) channelList.size - 1 else currentChannelIndex - 1
                    playChannel(prevIndex)
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (numPadPanel.visibility == View.VISIBLE) numPadPanel.visibility = View.GONE

                channelPanel.visibility = if (channelPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (channelPanel.visibility == View.VISIBLE) {
                    recyclerView.requestFocus()
                    recyclerView.scrollToPosition(currentChannelIndex)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (channelPanel.visibility == View.VISIBLE) channelPanel.visibility = View.GONE

                numPadPanel.visibility = if (numPadPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (numPadPanel.visibility == View.VISIBLE) {
                    digitInputBuffer.clear()
                    tvNumpadDisplay.text = "---"
                    findViewById<Button>(R.id.btn1).requestFocus()
                }
                return true
            }

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val digit = keyCode - KeyEvent.KEYCODE_0
                appendNumpadDigit(digit)
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (channelPanel.visibility == View.VISIBLE) {
                    channelPanel.visibility = View.GONE
                    return true
                }
                if (numPadPanel.visibility == View.VISIBLE) {
                    numPadPanel.visibility = View.GONE
                    return true
                }
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
        }

        digitAutoExecuteRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { executeNumpadSearch() }
        digitAutoExecuteRunnable = r
        handler.postDelayed(r, 2200)
    }

    private fun executeNumpadSearch() {
        val targetNumber = digitInputBuffer.toString().toIntOrNull()
        if (targetNumber != null) {
            val foundIndex = channelList.indexOfFirst { it.number == targetNumber }
            if (foundIndex != -1) {
                playChannel(foundIndex)
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
