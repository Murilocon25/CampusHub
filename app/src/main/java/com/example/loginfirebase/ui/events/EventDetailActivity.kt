package com.example.loginfirebase.ui.events

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
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
import com.example.loginfirebase.data.repository.EventMedia
import com.example.loginfirebase.data.repository.EventMediaPublishException
import com.example.loginfirebase.data.repository.EventMediaStore
import com.example.loginfirebase.data.repository.EventMediaType
import com.example.loginfirebase.data.repository.EventVideoInput
import com.example.loginfirebase.data.repository.EventVideoInputException
import com.example.loginfirebase.data.repository.PendingEventVideo
import com.example.loginfirebase.ui.auth.LoginActivity
import com.example.loginfirebase.ui.common.CampusMapView
import com.example.loginfirebase.ui.common.addCampusButton
import com.example.loginfirebase.ui.common.addCampusText
import com.example.loginfirebase.ui.common.createCampusInput
import com.example.loginfirebase.ui.common.showCampusState
import com.example.loginfirebase.ui.common.showCampusSnackbar
import com.example.loginfirebase.ui.common.setCampusFont
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class EventDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        private const val MAX_AUDIO_DURATION_MS = 60_000
        private const val MAX_VIDEO_DURATION_SECONDS = 60
        private const val STATE_PENDING_VIDEO_CAPTURE = "pending_video_capture"
    }

    private val authRepository = AuthRepository()
    private val eventRepository = EventRepository()
    private lateinit var body: LinearLayout
    private lateinit var registrationBar: FrameLayout
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
    private var mediaAuthorName = ""
    private var mediaOwnerUid = ""
    private val deletingMediaIds = mutableSetOf<String>()
    private var pendingVideoCapture: File? = null
    private var videoRecordButton: MaterialButton? = null
    private var videoChooseButton: MaterialButton? = null
    private var eventVideosStatus: TextView? = null
    private var eventVideosRecycler: RecyclerView? = null
    private var eventVideosAdapter: EventVideoAdapter? = null
    private var isVideoInputBusy = false
    private var videoOwnerUid = ""
    private val videoIoExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) publishEventPhoto(uri)
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val photo = pendingCameraPhoto
        pendingCameraPhoto = null
        if (result.resultCode == RESULT_OK && photo != null && photo.length() > 0L) {
            publishCapturedEventPhoto(photo)
        } else {
            photo?.delete()
        }
    }
    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            finishVideoInput()
        } else {
            prepareEventVideo { EventVideoInput.copyFromUri(this, eventId, uri) }
        }
    }
    private val videoCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val capture = pendingVideoCapture
        pendingVideoCapture = null
        if (result.resultCode != RESULT_OK) {
            capture?.delete()
            finishVideoInput()
            return@registerForActivityResult
        }

        if (capture != null && capture.isFile && capture.length() > 0L) {
            prepareEventVideo { EventVideoInput.validateCaptureFile(this, eventId, capture) }
        } else {
            capture?.delete()
            val returnedUri = result.data?.data
                ?: result.data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            if (returnedUri != null) {
                prepareEventVideo { EventVideoInput.copyFromUri(this, eventId, returnedUri) }
            } else {
                finishVideoInput()
                showMessage(R.string.event_video_capture_error)
            }
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
        pendingVideoCapture = savedInstanceState
            ?.getString(STATE_PENDING_VIDEO_CAPTURE)
            ?.let(::File)
            ?.takeIf(File::isFile)
        isVideoInputBusy = pendingVideoCapture != null

        setContentView(R.layout.activity_event_detail)
        body = findViewById(R.id.event_detail_content)
        registrationBar = findViewById(R.id.event_detail_registration_bar)
        registrationBar.removeAllViews()
        findViewById<MaterialToolbar>(R.id.event_detail_toolbar).setNavigationOnClickListener { returnToEvents() }
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
        registrationBar.removeAllViews()
        registrationBar.isVisible = true
        findViewById<TextView>(R.id.event_detail_header).text = event.title
        mediaOwnerUid = userId
        mediaAuthorName = displayName?.trim()?.takeIf(String::isNotBlank)
            ?: email?.substringBefore('@')?.takeIf(String::isNotBlank)
            ?: getString(R.string.profile_student_fallback)
        eventRepository.getProfile(userId).addOnSuccessListener { profile ->
            profile.getString("name")?.trim()?.takeIf(String::isNotBlank)?.let { mediaAuthorName = it }
        }
        val overview = createDetailCard()
        val badges = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        badges.addView(createBadge(
            event.category.ifBlank { getString(R.string.events_category_fallback) },
            R.color.color_blue_surface,
            R.color.color_blue_text
        ), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.space_2) })
        val ended = event.hasEnded()
        badges.addView(createBadge(
            getString(if (ended) R.string.event_status_ended else R.string.event_status_upcoming),
            if (ended) R.color.color_background else R.color.color_blue_surface,
            if (ended) R.color.color_text_subtitle else R.color.color_blue_text
        ))
        overview.addView(badges)
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
            setTextAppearance(R.style.TextAppearance_CampusHub_Body)
            setCampusFont()
            setLineSpacing(resources.getDimensionPixelSize(R.dimen.space_1).toFloat(), 1f)
        })
        event.location?.let { location ->
            val mapSection = createDetailCard(getString(R.string.event_location_map_title))
            mapView = CampusMapView(this, lifecycle, savedMapState)
            val mapCard = MaterialCardView(this).apply {
                radius = resources.getDimension(R.dimen.shape_medium)
                cardElevation = 0f
                strokeWidth = 0
                clipToOutline = true
            }
            mapCard.addView(mapView, android.widget.FrameLayout.LayoutParams(
                -1, resources.getDimensionPixelSize(R.dimen.detail_map_height)
            ))
            mapSection.addView(mapCard, LinearLayout.LayoutParams(-1, -2))
            mapView?.getMapAsync { map ->
                val eventPosition = LatLng(location.latitude, location.longitude)
                map.cameraPosition = CameraPosition.Builder()
                    .target(eventPosition)
                    .zoom(16.0)
                    .build()
                map.addMarker(MarkerOptions().position(eventPosition).title(event.title).snippet(event.place))
            }
        }
        addEventPhotosSection(event.id, userId)
        addEventVideosSection(event.id, userId)
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
            topMargin = resources.getDimensionPixelSize(R.dimen.space_2)
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
            minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
            insetTop = 0
            insetBottom = 0
            setTextColor(getColor(R.color.color_on_secondary_container))
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_secondary_container)))
            strokeWidth = 0
        }
        participationSection.addView(favoriteButton, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_3)
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
            progressTintList = getColorStateList(R.color.color_secondary)
            secondaryProgressTintList = getColorStateList(R.color.color_secondary_container)
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

        val registrationButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            isAllCaps = false
            minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
            insetTop = 0
            insetBottom = 0
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_primary)))
            setTextColor(getColor(R.color.color_on_primary))
        }
        registrationBar.addView(registrationButton, FrameLayout.LayoutParams(-1, -2))
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

    }

    private fun addEventPhotosSection(eventId: String, userId: String) {
        val section = createDetailCard(getString(R.string.event_photos_title))
        section.addView(createSectionNote(getString(R.string.event_photos_shared_notice)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val cameraButton = MaterialButton(this).apply {
            setText(R.string.event_photo_take)
            isAllCaps = false
            maxLines = 2
            gravity = android.view.Gravity.CENTER
            minHeight = resources.getDimensionPixelSize(R.dimen.detail_action_height)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { launchCameraForEventPhoto() }
        }
        val galleryButton = MaterialButton(this).apply {
            setText(R.string.event_photo_choose)
            isAllCaps = false
            maxLines = 2
            gravity = android.view.Gravity.CENTER
            minHeight = resources.getDimensionPixelSize(R.dimen.detail_action_height)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { galleryPicker.launch("image/*") }
        }
        actions.addView(cameraButton, LinearLayout.LayoutParams(0, resources.getDimensionPixelSize(R.dimen.detail_action_height), 1f).apply {
            marginEnd = resources.getDimensionPixelSize(R.dimen.space_2)
        })
        actions.addView(galleryButton, LinearLayout.LayoutParams(0, resources.getDimensionPixelSize(R.dimen.detail_action_height), 1f).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.space_2)
        })
        section.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.space_1)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
        })

        eventPhotosEmptyMessage = TextView(this).apply {
            setText(R.string.event_photos_empty)
            setTextAppearance(R.style.TextAppearance_CampusHub_Body)
            setCampusFont()
            setPadding(resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_2), resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_2))
        }
        section.addView(eventPhotosEmptyMessage)

        eventPhotosAdapter = EventPhotoAdapter(userId, ::confirmDeleteMedia)
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
        section.addView(createSectionNote(getString(R.string.event_audios_shared_notice)))

        audioRecordingHint = TextView(this).apply {
            setText(R.string.event_audio_recording_hint)
            setTextAppearance(R.style.TextAppearance_CampusHub_Body)
            setCampusFont()
            visibility = View.GONE
        }
        section.addView(audioRecordingHint)

        audioRecordButton = MaterialButton(this).apply {
            setText(R.string.event_audio_record)
            isAllCaps = false
            minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
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
            topMargin = resources.getDimensionPixelSize(R.dimen.space_2)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
        })

        eventAudiosEmptyMessage = TextView(this).apply {
            setText(R.string.event_audios_empty)
            setTextAppearance(R.style.TextAppearance_CampusHub_Body)
            setCampusFont()
            setPadding(resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_2), resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_2))
        }
        section.addView(eventAudiosEmptyMessage)
        eventAudiosContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section.addView(eventAudiosContainer, LinearLayout.LayoutParams(-1, -2))
        refreshEventAudios(eventId, userId)
    }

    private fun addEventVideosSection(eventId: String, userId: String) {
        videoOwnerUid = userId
        val section = createDetailCard(getString(R.string.event_videos_title))
        section.addView(createSectionNote(getString(R.string.event_videos_shared_notice)))

        videoRecordButton = MaterialButton(this).apply {
            setText(R.string.event_video_record)
            isAllCaps = false
            isEnabled = !isVideoInputBusy
            minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { launchVideoCapture(eventId) }
        }
        section.addView(videoRecordButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.space_1)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
        })

        videoChooseButton = MaterialButton(this).apply {
            setText(R.string.event_video_choose)
            isAllCaps = false
            isEnabled = !isVideoInputBusy
            minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { launchVideoPicker() }
        }
        section.addView(videoChooseButton, LinearLayout.LayoutParams(-1, -2))

        eventVideosStatus = TextView(this).apply {
            setText(R.string.event_videos_loading)
            setTextAppearance(R.style.TextAppearance_CampusHub_Body)
            setCampusFont()
            setPadding(resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_3), resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_2))
        }
        section.addView(eventVideosStatus)

        eventVideosAdapter = EventVideoAdapter(
            currentUserUid = userId,
            onWatch = ::openEventVideo,
            onDelete = ::confirmDeleteMedia
        )
        eventVideosRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@EventDetailActivity)
            adapter = eventVideosAdapter
            isNestedScrollingEnabled = false
            isVisible = false
        }
        section.addView(eventVideosRecycler, LinearLayout.LayoutParams(-1, -2))
        refreshEventVideos(eventId)
    }

    private fun refreshEventVideos(eventId: String) {
        if (eventVideosAdapter?.itemCount == 0) {
            eventVideosStatus?.setText(R.string.event_videos_loading)
            eventVideosStatus?.isVisible = true
            eventVideosRecycler?.isVisible = false
        }
        EventMediaStore.list(eventId, EventMediaType.VIDEO) { result ->
            if (isFinishing || isDestroyed) return@list
            val status = eventVideosStatus ?: return@list
            val recycler = eventVideosRecycler ?: return@list
            result.onSuccess { videos ->
                eventVideosAdapter?.submitVideos(videos)
                status.isVisible = videos.isEmpty()
                status.setText(if (videos.isEmpty()) R.string.event_videos_empty else R.string.event_videos_loading)
                recycler.isVisible = videos.isNotEmpty()
            }.onFailure {
                eventVideosAdapter?.submitVideos(emptyList())
                status.setText(R.string.event_media_load_error)
                status.isVisible = true
                recycler.isVisible = false
            }
        }
    }

    private fun openEventVideo(video: EventMedia) {
        startActivity(Intent(this, EventVideoPlayerActivity::class.java).apply {
            putExtra(EventVideoPlayerActivity.EXTRA_EVENT_ID, video.eventId)
            putExtra(EventVideoPlayerActivity.EXTRA_VIDEO_ID, video.id)
        })
    }

    private fun launchVideoCapture(eventId: String) {
        if (isVideoInputBusy) return
        setVideoInputBusy(true)
        val capture = try {
            EventVideoInput.createCaptureFile(this, eventId)
        } catch (_: Exception) {
            finishVideoInput()
            showMessage(R.string.event_video_capture_error)
            return
        }
        pendingVideoCapture = capture

        try {
            val captureUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", capture)
            val captureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
                putExtra(MediaStore.EXTRA_DURATION_LIMIT, MAX_VIDEO_DURATION_SECONDS)
                putExtra(MediaStore.EXTRA_SIZE_LIMIT, EventVideoInput.MAX_SIZE_BYTES)
                clipData = ClipData.newUri(contentResolver, getString(R.string.event_videos_title), captureUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            videoCaptureLauncher.launch(captureIntent)
        } catch (_: Exception) {
            pendingVideoCapture = null
            capture.delete()
            finishVideoInput()
            showMessage(R.string.event_video_capture_error)
        }
    }

    private fun launchVideoPicker() {
        if (isVideoInputBusy) return
        setVideoInputBusy(true)
        try {
            videoPicker.launch("video/*")
        } catch (_: Exception) {
            finishVideoInput()
            showMessage(R.string.event_video_picker_error)
        }
    }

    private fun prepareEventVideo(prepare: () -> PendingEventVideo) {
        setVideoInputBusy(true)
        videoIoExecutor.execute {
            val result = runCatching(prepare)
            mainHandler.post {
                if (isFinishing || isDestroyed) {
                    result.getOrNull()?.let(EventVideoInput::delete)
                    return@post
                }
                result.onSuccess { pendingVideo ->
                    EventMediaStore.publishFile(
                        context = applicationContext,
                        eventId = pendingVideo.eventId,
                        type = EventMediaType.VIDEO,
                        file = pendingVideo.file,
                        authorUid = videoOwnerUid,
                        authorName = mediaAuthorName,
                        mimeType = pendingVideo.mimeType,
                        durationMs = pendingVideo.durationMs
                    ) { uploadResult ->
                        EventVideoInput.delete(pendingVideo)
                        if (isFinishing || isDestroyed) return@publishFile
                        setVideoInputBusy(false)
                        handleMediaPublished(EventMediaType.VIDEO, uploadResult)
                    }
                }.onFailure { error ->
                    setVideoInputBusy(false)
                    showMessage(videoInputErrorMessage(error))
                }
            }
        }
    }

    private fun videoInputErrorMessage(error: Throwable): Int =
        when ((error as? EventVideoInputException)?.reason) {
            EventVideoInputException.Reason.TOO_LARGE -> R.string.event_video_too_large
            EventVideoInputException.Reason.TOO_LONG -> R.string.event_video_too_long
            EventVideoInputException.Reason.INVALID_VIDEO -> R.string.event_video_invalid
            EventVideoInputException.Reason.UNREADABLE -> R.string.event_video_input_error
            else -> R.string.event_video_save_error
        }

    private fun setVideoInputBusy(busy: Boolean) {
        isVideoInputBusy = busy
        videoRecordButton?.isEnabled = !busy
        videoChooseButton?.isEnabled = !busy
    }

    private fun finishVideoInput() {
        setVideoInputBusy(false)
    }

    private fun beginAudioRecording() {
        if (audioRecorder != null) return
        val destination = try {
            createPendingAudioRecordingFile(audioEventId)
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

    private fun createPendingAudioRecordingFile(eventId: String): File {
        val safeEventId = eventId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "event" }
        val directory = File(File(cacheDir, "event_audio_pending"), safeEventId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create the temporary event audio directory")
        }
        return File.createTempFile("recording_", ".m4a", directory)
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
        audioRecordButton?.isEnabled = false
        EventMediaStore.publishFile(
            context = applicationContext,
            eventId = audioEventId,
            type = EventMediaType.AUDIO,
            file = recording,
            authorUid = audioOwnerUid,
            authorName = mediaAuthorName,
            mimeType = "audio/mp4"
        ) { result ->
            recording.delete()
            audioRecordButton?.isEnabled = true
            handleMediaPublished(EventMediaType.AUDIO, result)
        }
    }

    private fun refreshEventAudios(eventId: String, userId: String) {
        EventMediaStore.list(eventId, EventMediaType.AUDIO) { result ->
            if (isFinishing || isDestroyed) return@list
            val container = eventAudiosContainer ?: return@list
            val audios = result.getOrNull().orEmpty()
            container.removeAllViews()
            eventAudiosEmptyMessage?.setText(
                if (result.isSuccess) R.string.event_audios_empty else R.string.event_media_load_error
            )
            eventAudiosEmptyMessage?.isVisible = audios.isEmpty()
            audios.forEachIndexed { index, audio ->
                container.addView(createAudioRow(eventId, userId, audio))
                if (index < audios.lastIndex) {
                    container.addView(View(this).apply {
                    setBackgroundColor(getColor(R.color.color_secondary_container))
                    }, LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(R.dimen.stroke_width)).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.space_2)
                        bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
                    })
                }
            }
        }
    }

    private fun createAudioRow(eventId: String, userId: String, audio: EventMedia): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_2), resources.getDimensionPixelSize(R.dimen.space_1), resources.getDimensionPixelSize(R.dimen.space_2))
        }
        val date = formatMediaDate(audio.createdAtMillis)
        row.addCampusText(getString(R.string.event_audio_item, audio.authorName, date), 14f)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val playButton = MaterialButton(this).apply {
            isAllCaps = false
            minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
            insetTop = 0
            insetBottom = 0
            setText(if (currentlyPlayingAudioId == audio.id) R.string.event_audio_stop else R.string.event_audio_play)
            setOnClickListener { playOrStopAudio(eventId, userId, audio) }
        }
        actions.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.space_2) })

        if (EventMediaStore.isOwnedBy(audio, userId)) {
            val deleteButton = MaterialButton(this).apply {
                isAllCaps = false
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                insetTop = 0
                insetBottom = 0
                setText(R.string.event_audio_delete)
                setOnClickListener { confirmDeleteMedia(audio) }
            }
            actions.addView(deleteButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = resources.getDimensionPixelSize(R.dimen.space_2) })
        }
        row.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_1) })
        return row
    }

    private fun playOrStopAudio(eventId: String, userId: String, audio: EventMedia) {
        if (currentlyPlayingAudioId == audio.id) {
            stopAudioPlayback()
            return
        }
        stopAudioPlayback(refresh = false)
        val player = MediaPlayer()
        audioPlayer = player
        currentlyPlayingAudioId = audio.id
        player.setOnPreparedListener {
            if (audioPlayer === player && currentlyPlayingAudioId == audio.id) {
                player.start()
                refreshEventAudios(eventId, userId)
            }
        }
        player.setOnCompletionListener {
            stopAudioPlayback(refresh = false)
            refreshEventAudios(eventId, userId)
        }
        player.setOnErrorListener { _, _, _ ->
            stopAudioPlayback(refresh = false)
            showMessage(R.string.event_audio_play_error)
            refreshEventAudios(eventId, userId)
            true
        }
        EventMediaStore.downloadUrl(audio) { result ->
            if (audioPlayer !== player || currentlyPlayingAudioId != audio.id) return@downloadUrl
            val uri = result.getOrNull()
            if (uri == null) {
                stopAudioPlayback(refresh = false)
                showMessage(R.string.event_audio_play_error)
                refreshEventAudios(eventId, userId)
                return@downloadUrl
            }
            try {
                player.setDataSource(this, uri)
                player.prepareAsync()
            } catch (_: Exception) {
                stopAudioPlayback(refresh = false)
                showMessage(R.string.event_audio_play_error)
                refreshEventAudios(eventId, userId)
            }
        }
        refreshEventAudios(eventId, userId)
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

    private fun formatMediaDate(timestamp: Long): String =
        if (timestamp <= 0L) getString(R.string.event_media_date_unknown)
        else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Date(timestamp))

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

    private fun publishCapturedEventPhoto(photo: File) {
        EventMediaStore.publishFile(
            context = applicationContext,
            eventId = eventId,
            type = EventMediaType.PHOTO,
            file = photo,
            authorUid = mediaOwnerUid,
            authorName = mediaAuthorName,
            mimeType = "image/jpeg"
        ) { result ->
            photo.delete()
            handleMediaPublished(EventMediaType.PHOTO, result)
        }
    }

    private fun publishEventPhoto(uri: Uri) {
        val mimeType = contentResolver.getType(uri)
            ?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: "image/jpeg"
        EventMediaStore.publishUri(
            context = applicationContext,
            eventId = eventId,
            type = EventMediaType.PHOTO,
            uri = uri,
            authorUid = mediaOwnerUid,
            authorName = mediaAuthorName,
            mimeType = mimeType
        ) { result -> handleMediaPublished(EventMediaType.PHOTO, result) }
    }

    private fun handleMediaPublished(type: EventMediaType, result: Result<EventMedia>) {
        if (isFinishing || isDestroyed) return
        result.onSuccess {
            when (type) {
                EventMediaType.PHOTO -> {
                    refreshEventPhotos(eventId)
                    showMessage(R.string.event_photo_saved)
                }
                EventMediaType.AUDIO -> {
                    refreshEventAudios(eventId, mediaOwnerUid)
                    showMessage(R.string.event_audio_published)
                }
                EventMediaType.VIDEO -> {
                    refreshEventVideos(eventId)
                    showMessage(R.string.event_video_published)
                }
            }
        }.onFailure { error ->
            showMessage(mediaPublishErrorMessage(error))
        }
    }

    private fun mediaPublishErrorMessage(error: Throwable): Int {
        val causes = generateSequence(error) { it.cause }.toList()
        val storageError = causes.filterIsInstance<StorageException>().firstOrNull()
        val firestoreError = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        val publishError = causes.filterIsInstance<EventMediaPublishException>().firstOrNull()
        Log.e(
            "CampusHubMedia",
            "Falha ao publicar mídia na etapa ${publishError?.stage ?: "desconhecida"}; " +
                "storageCode=${storageError?.errorCode ?: "none"}; " +
                "firestoreCode=${firestoreError?.code?.name ?: "none"}",
            error
        )

        storageError?.let {
            return when (it.errorCode) {
                StorageException.ERROR_QUOTA_EXCEEDED -> R.string.event_media_publish_quota_error
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND -> R.string.event_media_publish_bucket_error
                StorageException.ERROR_NOT_AUTHORIZED -> R.string.event_media_publish_permission_error
                StorageException.ERROR_NOT_AUTHENTICATED -> R.string.event_media_publish_auth_error
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> R.string.event_media_publish_network_error
                else -> if (causes.any { cause -> cause is FirebaseNetworkException }) {
                    R.string.event_media_publish_network_error
                } else {
                    R.string.event_media_publish_error
                }
            }
        }
        if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return R.string.event_media_publish_permission_error
        }
        if (causes.any { it is FirebaseNetworkException }) {
            return R.string.event_media_publish_network_error
        }
        return R.string.event_media_publish_error
    }

    private fun refreshEventPhotos(eventId: String) {
        EventMediaStore.list(eventId, EventMediaType.PHOTO) { result ->
            if (isFinishing || isDestroyed) return@list
            result.onSuccess { photos ->
                eventPhotosAdapter?.submitPhotos(photos)
                eventPhotosEmptyMessage?.isVisible = photos.isEmpty()
                eventPhotosRecycler?.isVisible = photos.isNotEmpty()
            }.onFailure {
                eventPhotosAdapter?.submitPhotos(emptyList())
                eventPhotosEmptyMessage?.setText(R.string.event_media_load_error)
                eventPhotosEmptyMessage?.isVisible = true
                eventPhotosRecycler?.isVisible = false
            }
        }
    }

    private fun confirmDeleteMedia(media: EventMedia) {
        if (!EventMediaStore.isOwnedBy(media, mediaOwnerUid) || media.id in deletingMediaIds) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.event_media_delete_prompt))
            .setNegativeButton(getString(R.string.event_cancel), null)
            .setPositiveButton(getString(R.string.event_delete)) { _, _ -> deleteEventMedia(media) }
            .show()
    }

    private fun deleteEventMedia(media: EventMedia) {
        if (media.id in deletingMediaIds) return
        deletingMediaIds += media.id
        if (media.type == EventMediaType.AUDIO && currentlyPlayingAudioId == media.id) {
            stopAudioPlayback(refresh = false)
        }
        EventMediaStore.delete(media, mediaOwnerUid) { result ->
            deletingMediaIds -= media.id
            if (isFinishing || isDestroyed) return@delete
            result.onSuccess {
                when (media.type) {
                    EventMediaType.PHOTO -> refreshEventPhotos(media.eventId)
                    EventMediaType.AUDIO -> refreshEventAudios(media.eventId, mediaOwnerUid)
                    EventMediaType.VIDEO -> refreshEventVideos(media.eventId)
                }
                showMessage(R.string.event_media_deleted)
            }.onFailure { showMessage(R.string.event_media_delete_error) }
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
        pendingVideoCapture?.delete()
        pendingVideoCapture = null
        videoIoExecutor.shutdown()
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
        findViewById<View>(android.R.id.content).showCampusSnackbar(stringId)
    }

    private fun createDetailCard(title: String? = null): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.shape_medium)
            cardElevation = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_width)
            strokeColor = getColor(R.color.color_border)
            setCardBackgroundColor(getColor(R.color.color_surface))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = resources.getDimensionPixelSize(R.dimen.space_4)
            setPadding(inset, inset, inset, inset)
        }
        card.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_3)
        })
        title?.let { label ->
            content.addView(TextView(this).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_CampusHub_Title)
                setCampusFont(bold = true)
                setTextColor(getColor(R.color.color_text_title))
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.space_3)
            })
        }
        return content
    }

    private fun createBadge(text: String, backgroundColor: Int, textColor: Int): TextView = TextView(this).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_CampusHub_Label)
        setCampusFont(bold = true)
        setTextColor(getColor(textColor))
        background = GradientDrawable().apply {
            setColor(getColor(backgroundColor))
            cornerRadius = resources.getDimension(R.dimen.shape_large)
        }
        setPadding(resources.getDimensionPixelSize(R.dimen.space_3), resources.getDimensionPixelSize(R.dimen.space_2), resources.getDimensionPixelSize(R.dimen.space_3), resources.getDimensionPixelSize(R.dimen.space_2))
    }

    private fun addDetailInfo(container: LinearLayout, label: String, value: String) {
        val icon = if (label == getString(R.string.event_date_label)) R.drawable.ic_calendar else R.drawable.ic_location
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        row.addView(android.widget.ImageView(this).apply {
            setImageResource(icon)
            imageTintList = getColorStateList(R.color.color_primary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.icon_size_standard),
            resources.getDimensionPixelSize(R.dimen.icon_size_standard)
        ))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@EventDetailActivity).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_CampusHub_Caption)
                setCampusFont(bold = true)
                setTextColor(getColor(R.color.color_text_subtitle))
            })
            addView(TextView(this@EventDetailActivity).apply {
                text = value
                setTextAppearance(R.style.TextAppearance_CampusHub_Body)
                setCampusFont()
                setLineSpacing(resources.getDimensionPixelSize(R.dimen.space_1).toFloat(), 1f)
            }, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.space_1)
            })
        }
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.space_3)
        })
        container.addView(row)
    }

    private fun addDetailDivider(container: LinearLayout) {
        container.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.color_border))
        }, LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(R.dimen.stroke_width)).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.space_3)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_3)
        })
    }

    private fun createSectionNote(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_CampusHub_Caption)
        setCampusFont()
        setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.space_2))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.saveMapState(outState)
        pendingVideoCapture?.absolutePath?.let { outState.putString(STATE_PENDING_VIDEO_CAPTURE, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

}
