package com.canlitv.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
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
    private lateinit var recyclerView: RecyclerView
    private val channelList = mutableListOf<Channel>()

    // Bura öz M3U linkini qoya bilərsən:
    private val M3U_URL = "https://example.com/channels.m3u"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        channelPanel = findViewById(R.id.channelPanel)
        recyclerView = findViewById(R.id.recyclerViewChannels)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false

        recyclerView.layoutManager = LinearLayoutManager(this)
        fetchM3U()
    }

    private fun fetchM3U() {
        val client = OkHttpClient()
        val request = Request.Builder().url(M3U_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                parseM3U(body)
                runOnUiThread {
                    setupAdapter()
                    if (channelList.isNotEmpty()) {
                        playChannel(channelList[0])
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
            channelPanel.visibility = if (channelPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (channelPanel.visibility == View.VISIBLE) {
                recyclerView.requestFocus()
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_BACK && channelPanel.visibility == View.VISIBLE) {
            channelPanel.visibility = View.GONE
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
