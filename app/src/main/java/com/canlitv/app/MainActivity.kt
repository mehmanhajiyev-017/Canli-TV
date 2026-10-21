package com.canlitv.app

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.*
import java.io.IOException

data class Channel(val name: String, val logoUrl: String, val streamUrl: String)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelPanel: View
    private lateinit var linkDialogPanel: View
    private lateinit var etM3uUrl: EditText
    private lateinit var btnSaveUrl: Button
    private lateinit var recyclerView: RecyclerView
    private val channelList = mutableListOf<Channel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        channelPanel = findViewById(R.id.channelPanel)
        linkDialogPanel = findViewById(R.id.linkDialogPanel)
        etM3uUrl = findViewById(R.id.etM3uUrl)
        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        recyclerView = findViewById(R.id.recyclerViewChannels)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false

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
                Toast.makeText(this, "Düzgün m3u linki daxil edin!", Toast.LENGTH_SHORT).show()
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
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Yüklənmə xətası! Linki yoxlayın.", Toast.LENGTH_LONG).show()
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
                        playChannel(channelList[0])
                    } else {
                        Toast.makeText(this@MainActivity, "Kanal tapılmadı!", Toast.LENGTH_SHORT).show()
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

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")
                currentLogo = logoRegex.find(trimmed)?.groups?.get(1)?.value ?: ""
                currentName = trimmed.substringAfterLast(",").trim()
            } else if (trimmed.startsWith("http")) {
                channelList.add(Channel(currentName, currentLogo, trimmed))
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
                holder.itemView.findViewById<TextView>(R.id.tvName).text = ch.name
                val logoImg = holder.itemView.findViewById<ImageView>(R.id.imgLogo)

                if (ch.logoUrl.isNotEmpty()) {
                    Glide.with(this@MainActivity).load(ch.logoUrl).into(logoImg)
                }

                holder.itemView.setOnClickListener {
                    playChannel(ch)
                    channelPanel.visibility = View.GONE
                }
            }

            override fun getItemCount() = channelList.size
        }
    }

    private fun playChannel(channel: Channel) {
        val mediaItem = MediaItem.fromUri(channel.streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (linkDialogPanel.visibility != View.VISIBLE) {
                channelPanel.visibility = if (channelPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (channelPanel.visibility == View.VISIBLE) {
                    recyclerView.requestFocus()
                }
                return true
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (channelPanel.visibility == View.VISIBLE) {
                channelPanel.visibility = View.GONE
                return true
            } else if (linkDialogPanel.visibility == View.VISIBLE) {
                linkDialogPanel.visibility = View.GONE
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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
