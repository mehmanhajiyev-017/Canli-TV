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
    private lateinit var linkDialogPanel: View
    private lateinit var etM3uUrl: EditText
    private lateinit var btnSaveUrl: Button
    private lateinit var recyclerView: RecyclerView
    private val channelList = mutableListOf<Channel>()

    private var currentChannelIndex = 0
    private val digitInputBuffer = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var digitRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        channelPanel = findViewById(R.id.channelPanel)
        linkDialogPanel = findViewById(R.id.linkDialogPanel)
        etM3uUrl = findViewById(R.id.etM3uUrl)
        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        recyclerView = findViewById(R.id.recyclerViewChannels)

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
                Toast.makeText(applicationContext, "Kanal açılmadı", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(applicationContext, "Pleylist yüklənmədi", Toast.LENGTH_LONG).show()
                    showLinkDialog()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                channelList.clear()
                parseM3U(body)
                runOnUiThread {
                    setupAdapter()
                    if (channelList.isNotEmpty()) {
                        playChannel(0)
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
                val formattedNumber = String.format("%03d", ch.number)
                holder.itemView.findViewById<TextView>(R.id.tvNumber).text = formattedNumber
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

        val formattedNumber = String.format("%03d", channel.number)
        Toast.makeText(applicationContext, "$formattedNumber - ${channel.name}", Toast.LENGTH_SHORT).show()
    }

    // Pultun Yumru D-Pad və Rəqəm düymələri ilə idarəetməsi
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (linkDialogPanel.visibility == View.VISIBLE) {
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            // Yuxarı ox: Sonrakı kanal (CH+)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (channelPanel.visibility != View.VISIBLE && channelList.isNotEmpty()) {
                    val nextIndex = (currentChannelIndex + 1) % channelList.size
                    playChannel(nextIndex)
                    return true
                }
            }

            // Aşağı ox: Əvvəlki kanal (CH-)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (channelPanel.visibility != View.VISIBLE && channelList.isNotEmpty()) {
                    val prevIndex = if (currentChannelIndex - 1 < 0) channelList.size - 1 else currentChannelIndex - 1
                    playChannel(prevIndex)
                    return true
                }
            }

            // Sol ox: Kanal siyahısını açır/bağlayır
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                channelPanel.visibility = if (channelPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (channelPanel.visibility == View.VISIBLE) {
                    recyclerView.requestFocus()
                    recyclerView.scrollToPosition(currentChannelIndex)
                }
                return true
            }

            // Sağ ox və ya OK: Kanal siyahısı bağlıdırsa menyunu açır
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (channelPanel.visibility == View.GONE) {
                    channelPanel.visibility = View.VISIBLE
                    recyclerView.requestFocus()
                    recyclerView.scrollToPosition(currentChannelIndex)
                    return true
                }
            }

            // Geri düyməsi
            KeyEvent.KEYCODE_BACK -> {
                if (channelPanel.visibility == View.VISIBLE) {
                    channelPanel.visibility = View.GONE
                    return true
                }
            }

            // Pultdan rəqəmlər yığıldıqda birbaşa həmin kanala keçid (0–9)
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val digit = keyCode - KeyEvent.KEYCODE_0
                handleDigitInput(digit)
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun handleDigitInput(digit: Int) {
        digitInputBuffer.append(digit)
        val channelQuery = digitInputBuffer.toString()
        Toast.makeText(applicationContext, "Kanal: $channelQuery", Toast.LENGTH_SHORT).show()

        digitRunnable?.let { handler.removeCallbacks(it) }
        digitRunnable = Runnable {
            val targetNumber = digitInputBuffer.toString().toIntOrNull()
            if (targetNumber != null) {
                val foundIndex = channelList.indexOfFirst { it.number == targetNumber }
                if (foundIndex != -1) {
                    playChannel(foundIndex)
                } else {
                    Toast.makeText(applicationContext, "Kanal $targetNumber tapılmadı", Toast.LENGTH_SHORT).show()
                }
            }
            digitInputBuffer.clear()
        }
        handler.postDelayed(digitRunnable!!, 2000) // 2 saniyə ərzində rəqəmi təsdiqləyir
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
