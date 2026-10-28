package com.example.loginfirebase.ui.events

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.view.View
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.MainActivity
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.model.toEventComment
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.EventRepository
import com.example.loginfirebase.data.repository.EventPhotoStore
import com.example.loginfirebase.data.repository.EventAudio
import com.example.loginfirebase.data.repository.EventAudioStore
import com.example.loginfirebase.ui.auth.LoginActivity
import com.example.loginfirebase.ui.common.CampusMapView
import com.example.loginfirebase.ui.common.addCampusButton
import com.example.loginfirebase.ui.common.addCampusText
import com.example.loginfirebase.ui.common.createCampusInput
import com.example.loginfirebase.ui.common.showCampusState
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class EventDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        private const val MAX_AUDIO_DURATION_MS = 60_000
    }

    private val authRepository = AuthRepository()
    private val eventRepository = EventRepository()
    private lateinit var body: LinearLayout
    private var eventId: String = ""
    private var mapView: CampusMapView? = null
    private var savedMapState: Bundle? = null
    private var eventPhotosAdapter: EventPhotoAdapter? = null
    private var eventPhotosRecycler: RecyclerView? = null
    private var eventPhotosEmptyMessage: TextView? = null
    private var eventAudiosContainer: LinearLayout? = null
    private var eventAudiosEmptyMessage: TextView? = null
    private var audioRecordButton: MaterialButton? = null
    private var audioRecordingHint: TextView? = null
    private var pendingCameraPhoto: File? = null
    private var pendingAudioRecording: File? = null
    private var audioRecorder: MediaRecorder? = null
    private var audioPlayer: MediaPlayer? = null
    private var currentlyPlayingAudioId: String? = null
    private var audioOwnerUid = ""
    private var audioEventId = ""
    private val photoIoExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) saveEventPhoto { EventPhotoStore.saveFromUri(this, eventId, uri) }
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val photo = pendingCameraPhoto
        pendingCameraPhoto = null
        if (result.resultCode == RESULT_OK && photo != null && photo.length() > 0L) {
            saveEventPhoto {
                try {
                    EventPhotoStore.copyCapturedPhoto(this, eventId, photo)
                } finally {
                    photo.delete()
                }
            }
        } else {
            photo?.delete()
        }
    }
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginAudioRecording()
        else showMessage(R.string.event_audio_permission_required)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedMapState = savedInstanceState
        val user = authRepository.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        if (eventId.isBlank()) {
            finish()
            return
        }

        setContentView(R.layout.activity_event_detail)
        body = findViewById(R.id.event_detail_content)
        val viewModel = ViewModelProvider(this)[EventDetailViewModel::class.java]
        viewModel.state.observe(this) { state ->
            when (state) {
                EventDetailState.Loading -> body.showCampusState(getString(R.string.event_details_loading))
                EventDetailState.Empty -> {
                    Toast.makeText(this, getString(R.string.event_not_found), Toast.LENGTH_SHORT).show()
                    finish()
                }
                is EventDetailState.Error -> body.showCampusState(getString(R.string.event_load_error))
                is EventDetailState.Success -> renderEvent(state.event, user.uid, user.displayName, user.email)
            }
        }
        viewModel.load(eventId)
    }

    private fun renderEvent(event: CampusEvent, userId: String, displayName: String?, email: String?) {
        body.removeAllViews()
        val overview = createDetailCard()
        val badges = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        badges.addView(createBadge(
            event.category.ifBlank { getString(R.string.events_category_fallback) },
            R.color.color_blue_surface,
            R.color.color_blue_text
        ), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        val ended = event.hasEnded()
        badges.addView(createBadge(
            getString(if (ended) R.string.event_status_ended else R.string.event_status_upcoming),
            if (ended) R.color.color_background else R.color.color_blue_surface,
            if (ended) R.color.color_text_subtitle else R.color.color_blue_text
        ))
        overview.addView(badges)
        overview.addView(TextView(this).apply {
            text = event.title
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.color_text_title))
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
            bottomMargin = dp(16)
        })
        addDetailInfo(overview, getString(R.string.event_date_label), event.displayDate())
        addDetailDivider(overview)
        addDetailInfo(
            overview,
            getString(R.string.event_place_label),
            event.place.ifBlank { getString(R.string.not_informed) }
        )

        val description = createDetailCard(getString(R.string.event_description_title))
        description.addView(TextView(this).apply {
            text = event.description
            textSize = 16f
            setTextColor(getColor(R.color.color_text_body))
            setLineSpacing(dp(4).toFloat(), 1f)
        })
        event.location?.let { location ->
            val mapSection = createDetailCard(getString(R.string.event_location_map_title))
            mapView = CampusMapView(this, lifecycle, savedMapState)
            mapSection.addView(mapView, LinearLayout.LayoutParams(-1, dp(230)))
            mapView?.getMapAsync { map ->
                val eventPosition = LatLng(location.latitude, location.longitude)
                map.cameraPosition = CameraPosition.Builder()
                    .target(eventPosition)
                    .zoom(16.0)
                    .build()
                map.addMarker(MarkerOptions().position(eventPosition).title(event.title).snippet(event.place))
            }
        }
        addEventPhotosSection(event.id)
        addEventAudiosSection(event.id, userId)

        val ratingSummary = createDetailCard(getString(R.string.event_ratings_title))
        loadRatingSummary(event.id, ratingSummary)

        val commentsSection = createDetailCard(getString(R.string.event_comments_title))
        val (commentInputLayout, commentInput) = createCampusInput(
            getString(R.string.event_comment_hint), minLines = 2, maxLines = 5
        )
        commentsSection.addView(commentInputLayout)

        val commentsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val commentsRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@EventDetailActivity)
            isNestedScrollingEnabled = false
        }
        lateinit var commentsAdapter: CommentAdapter
        fun refreshComments() {
            eventRepository.getComments(event.id)
                .addOnSuccessListener { snapshot ->
                    val comments = snapshot.documents.map { it.toEventComment() }
                    if (comments.isEmpty()) {
                        commentsContainer.showCampusState(getString(R.string.event_comments_empty))
                    } else {
                        if (commentsRecycler.parent !== commentsContainer) {
                            commentsContainer.removeAllViews()
                            commentsContainer.addView(commentsRecycler, LinearLayout.LayoutParams(-1, -2))
                        }
                        commentsAdapter.submitList(comments)
                    }
                }
                .addOnFailureListener { commentsContainer.showCampusState(getString(R.string.event_comments_load_error)) }
        }
        commentsAdapter = CommentAdapter(
            currentUserId = userId,
            onEdit = { comment -> editComment(event.id, comment.id, comment.text, ::refreshComments) },
            onDelete = { comment -> confirmDeleteComment(event.id, comment.id, ::refreshComments) }
        )
        commentsRecycler.adapter = commentsAdapter
        commentsContainer.showCampusState(getString(R.string.event_comments_loading))
        commentsSection.addView(commentsContainer, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        refreshComments()

        commentsSection.addCampusButton(getString(R.string.event_comment_publish)) {
            val text = commentInput.text?.toString()?.trim().orEmpty()
            if (text.isBlank() || text.length > 1000) {
                commentInput.error = getString(R.string.event_comment_invalid)
                return@addCampusButton
            }
            eventRepository.getProfile(userId)
                .addOnSuccessListener { profile ->
                    val authorName = profile.getString("name")?.takeIf(String::isNotBlank)
                        ?: displayName?.takeIf(String::isNotBlank)
                        ?: email?.takeIf(String::isNotBlank)
                        ?: getString(R.string.profile_student_fallback)
                    eventRepository.publishComment(event.id, userId, authorName, text)
                        .addOnSuccessListener {
                            commentInput.text?.clear()
                            refreshComments()
                        }
                        .addOnFailureListener { showMessage(R.string.event_comment_publish_error) }
                }
                .addOnFailureListener { showMessage(R.string.event_profile_name_error) }
        }

        val participationSection = createDetailCard(getString(R.string.event_participation_title))
        val favoriteButton = MaterialButton(this).apply {
            isAllCaps = false
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            setTextColor(getColor(R.color.color_blue_text))
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_surface)))
            strokeWidth = dp(1)
            strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.color_primary))
        }
        participationSection.addView(favoriteButton, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })
        eventRepository.getFavorite(userId, event.id)
            .addOnSuccessListener { snapshot ->
                var isFavorite = snapshot.exists()
                favoriteButton.setText(if (isFavorite) R.string.event_favorite_remove else R.string.event_favorite_add)
                favoriteButton.setOnClickListener {
                    val update = if (isFavorite) eventRepository.removeFavorite(userId, event.id)
                    else eventRepository.saveFavorite(userId, event.id)
                    update.addOnSuccessListener {
                        isFavorite = !isFavorite
                        favoriteButton.setText(if (isFavorite) R.string.event_favorite_remove else R.string.event_favorite_add)
                        showMessage(if (isFavorite) R.string.event_favorite_added else R.string.event_favorite_removed)
                    }.addOnFailureListener {
                        showMessage(if (isFavorite) R.string.event_favorite_remove_error else R.string.event_favorite_add_error)
                    }
                }
            }
            .addOnFailureListener { showMessage(R.string.event_favorite_check_error) }

        val ratingSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        participationSection.addView(ratingSection)
        val ratingBar = RatingBar(this).apply {
            numStars = 5
            stepSize = 1f
            progressTintList = getColorStateList(R.color.color_primary)
            secondaryProgressTintList = getColorStateList(R.color.color_blue_surface)
            contentDescription = getString(R.string.event_rating_hint)
        }
        ratingSection.addCampusText(getString(R.string.event_rating_your), 20f, bold = true)
        ratingSection.addView(ratingBar)
        ratingSection.addCampusButton(getString(R.string.event_rating_save)) {
            val score = ratingBar.rating.toInt()
            if (score !in 1..5) {
                showMessage(R.string.event_rating_invalid)
                return@addCampusButton
            }
            eventRepository.saveRating(userId, event.id, score)
                .addOnSuccessListener {
                    showMessage(R.string.event_rating_saved)
                    loadRatingSummary(event.id, ratingSummary)
                }
                .addOnFailureListener { showMessage(R.string.event_rating_save_error) }
        }

        val registrationButton = MaterialButton(this).apply {
            isAllCaps = false
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
        }
        participationSection.addView(registrationButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
        eventRepository.getRegistration(userId, event.id)
            .addOnSuccessListener { snapshot ->
                var isRegistered = snapshot.exists()
                fun updateRegistrationUi() {
                    registrationButton.setText(if (isRegistered) R.string.registration_cancel else R.string.registration_join)
                    ratingSection.visibility = if (isRegistered && event.hasEnded()) View.VISIBLE else View.GONE
                }
                updateRegistrationUi()
                if (isRegistered && event.hasEnded()) {
                    eventRepository.getRating(userId, event.id).addOnSuccessListener { saved ->
                        saved.getLong("score")?.let { ratingBar.rating = it.toFloat() }
                    }
                }
                registrationButton.setOnClickListener {
                    val update = if (isRegistered) eventRepository.cancelRegistration(userId, event.id)
                    else eventRepository.register(userId, event)
                    update.addOnSuccessListener {
                        isRegistered = !isRegistered
                        updateRegistrationUi()
                        if (isRegistered && event.hasEnded()) {
                            eventRepository.getRating(userId, event.id).addOnSuccessListener { saved ->
                                saved.getLong("score")?.let { ratingBar.rating = it.toFloat() }
                            }
                        }
                        showMessage(if (isRegistered) R.string.registration_confirmed else R.string.registration_cancelled)
                    }.addOnFailureListener {
                        showMessage(if (isRegistered) R.string.registration_cancel_error else R.string.registration_error)
                    }
                }
            }
            .addOnFailureListener { showMessage(R.string.registration_check_error) }

        body.addCampusButton(getString(R.string.back_to_events), ::returnToEvents)
    }

    private fun addEventPhotosSection(eventId: String) {
        val section = createDetailCard(getString(R.string.event_photos_title))
        section.addView(createSectionNote(getString(R.string.event_photos_local_notice)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val cameraButton = MaterialButton(this).apply {
            setText(R.string.event_photo_take)
            isAllCaps = false
            maxLines = 2
            gravity = android.view.Gravity.CENTER
            minHeight = dp(60)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { launchCameraForEventPhoto() }
        }
        val galleryButton = MaterialButton(this).apply {
            setText(R.string.event_photo_choose)
            isAllCaps = false
            maxLines = 2
            gravity = android.view.Gravity.CENTER
            minHeight = dp(60)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { galleryPicker.launch("image/*") }
        }
        actions.addView(cameraButton, LinearLayout.LayoutParams(0, dp(60), 1f).apply {
            marginEnd = dp(6)
        })
        actions.addView(galleryButton, LinearLayout.LayoutParams(0, dp(60), 1f).apply {
            marginStart = dp(6)
        })
        section.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
            bottomMargin = dp(8)
        })

        eventPhotosEmptyMessage = TextView(this).apply {
            setText(R.string.event_photos_empty)
            textSize = 14f
            setTextColor(getColor(R.color.color_text_body))
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        section.addView(eventPhotosEmptyMessage)

        eventPhotosAdapter = EventPhotoAdapter()
        eventPhotosRecycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@EventDetailActivity, 3)
            adapter = eventPhotosAdapter
            isNestedScrollingEnabled = false
        }
        section.addView(eventPhotosRecycler, LinearLayout.LayoutParams(-1, -2))
        refreshEventPhotos(eventId)
    }

    private fun addEventAudiosSection(eventId: String, userId: String) {
        audioEventId = eventId
        audioOwnerUid = userId
        val section = createDetailCard(getString(R.string.event_audios_title))
        section.addView(createSectionNote(getString(R.string.event_audios_local_notice)))

        audioRecordingHint = TextView(this).apply {
            setText(R.string.event_audio_recording_hint)
            textSize = 14f
            setTextColor(getColor(R.color.color_text_body))
            visibility = View.GONE
        }
        section.addView(audioRecordingHint)

        audioRecordButton = MaterialButton(this).apply {
            setText(R.string.event_audio_record)
            isAllCaps = false
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            setOnClickListener {
                if (audioRecorder != null) {
                    stopAndPublishAudio()
                } else if (ContextCompat.checkSelfPermission(this@EventDetailActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    beginAudioRecording()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        section.addView(audioRecordButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        })

        eventAudiosEmptyMessage = TextView(this).apply {
            setText(R.string.event_audios_empty)
            textSize = 14f
            setTextColor(getColor(R.color.color_text_body))
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        section.addView(eventAudiosEmptyMessage)
        eventAudiosContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section.addView(eventAudiosContainer, LinearLayout.LayoutParams(-1, -2))
        refreshEventAudios(eventId, userId)
    }

    private fun beginAudioRecording() {
        if (audioRecorder != null) return
        val destination = try {
            EventAudioStore.createRecordingFile(this, audioEventId)
        } catch (_: Exception) {
            showMessage(R.string.event_audio_record_error)
            return
        }

        val recorder = MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44_100)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setMaxDuration(MAX_AUDIO_DURATION_MS)
            recorder.setOutputFile(destination.absolutePath)
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopAndPublishAudio()
            }
            recorder.prepare()
            recorder.start()
            pendingAudioRecording = destination
            audioRecorder = recorder
            audioRecordButton?.setText(R.string.event_audio_finish)
            audioRecordingHint?.visibility = View.VISIBLE
        } catch (_: Exception) {
            try { recorder.release() } catch (_: Exception) { }
            destination.delete()
            showMessage(R.string.event_audio_record_error)
        }
    }

    private fun stopAndPublishAudio() {
        val recorder = audioRecorder ?: return
        audioRecorder = null
        audioRecordButton?.setText(R.string.event_audio_record)
        audioRecordingHint?.visibility = View.GONE
        val recording = pendingAudioRecording
        pendingAudioRecording = null
        try {
            recorder.stop()
        } catch (_: Exception) {
            recording?.delete()
            showMessage(R.string.event_audio_too_short)
            try { recorder.release() } catch (_: Exception) { }
            return
        }
        try { recorder.release() } catch (_: Exception) { }

        if (recording == null) {
            showMessage(R.string.event_audio_record_error)
            return
        }
        try {
            EventAudioStore.publishRecording(this, audioEventId, audioOwnerUid, recording)
            refreshEventAudios(audioEventId, audioOwnerUid)
            showMessage(R.string.event_audio_published)
        } catch (_: Exception) {
            recording.delete()
            showMessage(R.string.event_audio_save_error)
        }
    }

    private fun refreshEventAudios(eventId: String, userId: String) {
        photoIoExecutor.execute {
            val audios = EventAudioStore.list(this, eventId)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                val container = eventAudiosContainer ?: return@post
                container.removeAllViews()
                eventAudiosEmptyMessage?.isVisible = audios.isEmpty()
                audios.forEachIndexed { index, audio ->
                    container.addView(createAudioRow(eventId, userId, audio))
                    if (index < audios.lastIndex) {
                        container.addView(View(this).apply {
                            setBackgroundColor(getColor(R.color.color_blue_surface))
                        }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                            topMargin = dp(8)
                            bottomMargin = dp(8)
                        })
                    }
                }
            }
        }
    }

    private fun createAudioRow(eventId: String, userId: String, audio: EventAudio): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val authorLabel = if (audio.ownerUid == userId) R.string.event_audio_yours else R.string.event_audio_other_user
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Date(audio.file.lastModified()))
        row.addCampusText(getString(R.string.event_audio_item, getString(authorLabel), date), 14f)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val playButton = MaterialButton(this).apply {
            isAllCaps = false
            minHeight = dp(44)
            insetTop = 0
            insetBottom = 0
            setText(if (currentlyPlayingAudioId == audio.file.name) R.string.event_audio_stop else R.string.event_audio_play)
            setOnClickListener { playOrStopAudio(eventId, userId, audio) }
        }
        actions.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })

        if (audio.ownerUid == userId) {
            val deleteButton = MaterialButton(this).apply {
                isAllCaps = false
                minHeight = dp(44)
                insetTop = 0
                insetBottom = 0
                setText(R.string.event_audio_delete)
                setOnClickListener { confirmDeleteAudio(eventId, userId, audio) }
            }
            actions.addView(deleteButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        }
        row.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        return row
    }

    private fun playOrStopAudio(eventId: String, userId: String, audio: EventAudio) {
        if (currentlyPlayingAudioId == audio.file.name) {
            stopAudioPlayback()
            return
        }
        stopAudioPlayback(refresh = false)
        try {
            val player = MediaPlayer.create(this, Uri.fromFile(audio.file))
            if (player == null) {
                showMessage(R.string.event_audio_play_error)
                return
            }
            audioPlayer = player
            currentlyPlayingAudioId = audio.file.name
            player.setOnCompletionListener {
                stopAudioPlayback(refresh = false)
                refreshEventAudios(eventId, userId)
            }
            player.start()
            refreshEventAudios(eventId, userId)
        } catch (_: Exception) {
            stopAudioPlayback(refresh = false)
            showMessage(R.string.event_audio_play_error)
        }
    }

    private fun stopAudioPlayback(refresh: Boolean = true) {
        val player = audioPlayer
        audioPlayer = null
        currentlyPlayingAudioId = null
        try {
            if (player?.isPlaying == true) player.stop()
            player?.release()
        } catch (_: Exception) { }
        if (refresh && audioEventId.isNotBlank()) refreshEventAudios(audioEventId, audioOwnerUid)
    }

    private fun confirmDeleteAudio(eventId: String, userId: String, audio: EventAudio) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.event_audio_delete_prompt))
            .setNegativeButton(getString(R.string.event_cancel), null)
            .setPositiveButton(getString(R.string.event_delete)) { _, _ ->
                if (currentlyPlayingAudioId == audio.file.name) stopAudioPlayback(refresh = false)
                if (EventAudioStore.delete(this, eventId, audio, userId)) {
                    refreshEventAudios(eventId, userId)
                    showMessage(R.string.event_audio_deleted)
                } else {
                    showMessage(R.string.event_audio_delete_error)
                }
            }
            .show()
    }

    private fun launchCameraForEventPhoto() {
        val captureDirectory = File(cacheDir, "event_photo_capture")
        if (!captureDirectory.exists() && !captureDirectory.mkdirs()) {
            showMessage(R.string.event_photo_camera_unavailable)
            return
        }

        val photo = try {
            File.createTempFile("event_photo_", ".jpg", captureDirectory)
        } catch (_: Exception) {
            showMessage(R.string.event_photo_camera_unavailable)
            return
        }
        pendingCameraPhoto = photo
        try {
            val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                clipData = ClipData.newUri(contentResolver, getString(R.string.event_photos_title), photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(cameraIntent)
        } catch (_: Exception) {
            pendingCameraPhoto = null
            photo.delete()
            showMessage(R.string.event_photo_camera_unavailable)
        }
    }

    private fun saveEventPhoto(save: () -> File) {
        photoIoExecutor.execute {
            try {
                save()
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        refreshEventPhotos(eventId)
                        showMessage(R.string.event_photo_saved)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) showMessage(R.string.event_photo_save_error)
                }
            }
        }
    }

    private fun refreshEventPhotos(eventId: String) {
        photoIoExecutor.execute {
            val photos = EventPhotoStore.list(this, eventId)
            mainHandler.post {
                if (!isFinishing && !isDestroyed) {
                    eventPhotosAdapter?.submitPhotos(photos)
                    eventPhotosEmptyMessage?.isVisible = photos.isEmpty()
                    eventPhotosRecycler?.isVisible = photos.isNotEmpty()
                }
            }
        }
    }

    private fun cancelAudioRecording() {
        val recorder = audioRecorder ?: return
        audioRecorder = null
        try { recorder.stop() } catch (_: Exception) { }
        try { recorder.release() } catch (_: Exception) { }
        pendingAudioRecording?.delete()
        pendingAudioRecording = null
    }

    override fun onDestroy() {
        cancelAudioRecording()
        stopAudioPlayback(refresh = false)
        photoIoExecutor.shutdown()
        super.onDestroy()
    }

    private fun returnToEvents() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, R.id.navigation_events)
        })
        finish()
    }

    private fun loadRatingSummary(eventId: String, container: LinearLayout) {
        container.showCampusState(getString(R.string.event_ratings_loading))
        eventRepository.getRatings(eventId)
            .addOnSuccessListener { snapshot ->
                val scores = snapshot.documents.mapNotNull { it.getLong("score") }
                if (scores.isEmpty()) {
                    container.showCampusState(getString(R.string.event_ratings_empty))
                } else {
                    container.removeAllViews()
                    val average = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", scores.average())
                    container.addCampusText(getString(R.string.event_rating_average, average, scores.size))
                }
            }
            .addOnFailureListener { container.showCampusState(getString(R.string.event_ratings_load_error)) }
    }

    private fun editComment(eventId: String, commentId: String, originalText: String, onSaved: () -> Unit) {
        val (inputLayout, input) = createCampusInput(getString(R.string.event_comment_hint), minLines = 2, maxLines = 5)
        input.setText(originalText)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.event_comment_edit))
            .setView(inputLayout)
            .setNegativeButton(getString(R.string.event_cancel), null)
            .setPositiveButton(getString(R.string.event_save)) { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isBlank() || text.length > 1000) {
                    showMessage(R.string.event_comment_invalid)
                } else {
                    eventRepository.updateComment(eventId, commentId, text)
                        .addOnSuccessListener { onSaved() }
                        .addOnFailureListener { showMessage(R.string.event_comment_edit_error) }
                }
            }
            .show()
    }

    private fun confirmDeleteComment(eventId: String, commentId: String, onDeleted: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.event_comment_delete_prompt))
            .setNegativeButton(getString(R.string.event_cancel), null)
            .setPositiveButton(getString(R.string.event_delete)) { _, _ ->
                eventRepository.deleteComment(eventId, commentId)
                    .addOnSuccessListener { onDeleted() }
                    .addOnFailureListener { showMessage(R.string.event_comment_delete_error) }
            }
            .show()
    }

    private fun showMessage(stringId: Int) {
        Toast.makeText(this, getString(stringId), Toast.LENGTH_SHORT).show()
    }

    private fun createDetailCard(title: String? = null): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.color_border)
            setCardBackgroundColor(getColor(R.color.color_surface))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        title?.let { label ->
            content.addView(TextView(this).apply {
                text = label
                textSize = 19f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.color_text_title))
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
        return content
    }

    private fun createBadge(text: String, backgroundColor: Int, textColor: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(getColor(textColor))
        background = GradientDrawable().apply {
            setColor(getColor(backgroundColor))
            cornerRadius = dp(24).toFloat()
        }
        setPadding(dp(10), dp(6), dp(10), dp(6))
    }

    private fun addDetailInfo(container: LinearLayout, label: String, value: String) {
        container.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.color_text_subtitle))
        })
        container.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(getColor(R.color.color_text_body))
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(3)
        })
    }

    private fun addDetailDivider(container: LinearLayout) {
        container.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.color_border))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })
    }

    private fun createSectionNote(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(getColor(R.color.color_text_subtitle))
        setPadding(0, 0, 0, dp(8))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.saveMapState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
