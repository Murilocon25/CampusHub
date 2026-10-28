package com.example.loginfirebase.ui.events

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.loginfirebase.R
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.EventMediaStore
import com.example.loginfirebase.data.repository.EventMediaType
import com.example.loginfirebase.ui.auth.LoginActivity
import com.google.android.material.appbar.MaterialToolbar

class EventVideoPlayerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EVENT_ID = "event_video_event_id"
        const val EXTRA_VIDEO_ID = "event_video_id"
        private const val STATE_POSITION_MS = "event_video_position_ms"
        private const val STATE_RESUME_PLAYBACK = "event_video_resume_playback"
    }

    private val authRepository = AuthRepository()
    private var videoView: VideoView? = null
    private var loadingIndicator: ProgressBar? = null
    private var isPrepared = false
    private var playbackPositionMs = 0
    private var resumePlayback = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (authRepository.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        if (eventId.isBlank() || videoId.isBlank()) {
            finishWithError(R.string.event_video_not_found)
            return
        }
        playbackPositionMs = savedInstanceState?.getInt(STATE_POSITION_MS, 0) ?: 0
        resumePlayback = savedInstanceState?.getBoolean(STATE_RESUME_PLAYBACK, true) ?: true
        setContentView(R.layout.activity_event_video_player)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0

        findViewById<MaterialToolbar>(R.id.event_video_player_toolbar).apply {
            setTitle(R.string.event_video_player_title)
            setNavigationOnClickListener { finish() }
        }
        loadingIndicator = findViewById(R.id.event_video_player_loading)
        videoView = findViewById<VideoView>(R.id.event_video_player_view).apply {
            val controller = android.widget.MediaController(this@EventVideoPlayerActivity)
            controller.setAnchorView(this)
            setMediaController(controller)
            setOnPreparedListener {
                isPrepared = true
                loadingIndicator?.isVisible = false
                if (playbackPositionMs > 0) seekTo(playbackPositionMs)
                if (resumePlayback) start()
            }
            setOnErrorListener { _, _, _ ->
                loadingIndicator?.isVisible = false
                finishWithError(R.string.event_video_play_error)
                true
            }
        }
        loadVideo(eventId, videoId)
    }

    override fun onPause() {
        videoView?.let { player ->
            if (isPrepared) {
                playbackPositionMs = player.currentPosition.coerceAtLeast(0)
                resumePlayback = player.isPlaying
                player.pause()
            }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (isPrepared && resumePlayback) videoView?.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (isPrepared) {
            videoView?.let { player ->
                playbackPositionMs = player.currentPosition.coerceAtLeast(0)
                outState.putInt(STATE_POSITION_MS, playbackPositionMs)
                outState.putBoolean(STATE_RESUME_PLAYBACK, resumePlayback)
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        videoView?.stopPlayback()
        videoView = null
        super.onDestroy()
    }

    private fun loadVideo(eventId: String, videoId: String) {
        EventMediaStore.get(eventId, videoId) { result ->
            if (isFinishing || isDestroyed) return@get
            val media = result.getOrNull()
            if (media == null || media.type != EventMediaType.VIDEO) {
                finishWithError(R.string.event_video_not_found)
                return@get
            }
            EventMediaStore.downloadUrl(media) { urlResult ->
                if (isFinishing || isDestroyed) return@downloadUrl
                val uri: Uri? = urlResult.getOrNull()
                if (uri == null) finishWithError(R.string.event_video_play_error)
                else videoView?.setVideoURI(uri)
            }
        }
    }

    private fun finishWithError(messageRes: Int) {
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
        finish()
    }
}
