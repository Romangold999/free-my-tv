package com.romgold.geelyyoutube

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class VideoPlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var streamUrl: String? = null
    private var dashUrl: String? = null
    private var audioUrl: String? = null
    private var playbackPosition: Long = 0L
    private var shouldPlayWhenReady: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        dashUrl = intent.getStringExtra(EXTRA_DASH_URL)
        audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        if (streamUrl.isNullOrBlank() && dashUrl.isNullOrBlank()) {
            Toast.makeText(this, "Ссылка на видео не получена", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong(STATE_POSITION, 0L)
            shouldPlayWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY, true)
        }

        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 4500
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(playerView)
    }

    override fun onStart() {
        super.onStart()
        if (player == null && !streamUrl.isNullOrBlank()) initializePlayer()
    }

    private fun initializePlayer() {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(client)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = when {
            !dashUrl.isNullOrBlank() ->
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(dashUrl!!))
            !audioUrl.isNullOrBlank() -> {
                val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
                val videoSource = progressiveFactory.createMediaSource(MediaItem.fromUri(streamUrl!!))
                val audioSource = progressiveFactory.createMediaSource(MediaItem.fromUri(audioUrl!!))
                MergingMediaSource(videoSource, audioSource)
            }
            else -> mediaSourceFactory.createMediaSource(MediaItem.fromUri(streamUrl!!))
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@VideoPlayerActivity,
                            "Ошибка воспроизведения: ${error.errorCodeName}",
                            Toast.LENGTH_LONG).show()
                    }
                })
                exo.setMediaSource(mediaSource)
                exo.prepare()
                exo.seekTo(playbackPosition)
                exo.playWhenReady = shouldPlayWhenReady
            }
        playerView.player = player
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val active = player
        if (active != null) {
            playbackPosition = active.currentPosition
            shouldPlayWhenReady = active.playWhenReady
        }
        outState.putLong(STATE_POSITION, playbackPosition)
        outState.putBoolean(STATE_PLAY_WHEN_READY, shouldPlayWhenReady)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        player?.let { active ->
            playbackPosition = active.currentPosition
            shouldPlayWhenReady = active.playWhenReady
            active.release()
        }
        player = null
        if (::playerView.isInitialized) playerView.player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_DASH_URL = "dash_manifest_url"
        const val EXTRA_AUDIO_URL = "audio_stream_url"
        private const val STATE_POSITION = "playback_position"
        private const val STATE_PLAY_WHEN_READY = "play_when_ready"
    }
}
