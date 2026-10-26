package com.example.loginfirebase.ui.events

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.MainActivity
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.model.toEventComment
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.EventRepository
import com.example.loginfirebase.ui.auth.LoginActivity
import com.example.loginfirebase.ui.common.CampusMapView
import com.example.loginfirebase.ui.common.addCampusButton
import com.example.loginfirebase.ui.common.addCampusText
import com.example.loginfirebase.ui.common.createCampusInput
import com.example.loginfirebase.ui.common.showCampusState
import com.google.android.material.button.MaterialButton
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import java.util.Locale

class EventDetailActivity : AppCompatActivity() {
    companion object { const val EXTRA_EVENT_ID = "event_id" }

    private val authRepository = AuthRepository()
    private val eventRepository = EventRepository()
    private lateinit var body: LinearLayout
    private var eventId: String = ""
    private var mapView: CampusMapView? = null
    private var savedMapState: Bundle? = null

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
        body.addCampusText(event.title, 23f, bold = true)
        body.addCampusText(getString(R.string.event_date, event.displayDate()), 16f)
        body.addCampusText(getString(R.string.event_place, event.place.ifBlank { getString(R.string.not_informed) }), 16f)
        event.location?.let { location ->
            body.addCampusText(getString(R.string.event_location_map_title), 19f, bold = true)
            mapView = CampusMapView(this, lifecycle, savedMapState)
            body.addView(mapView, LinearLayout.LayoutParams(-1, dp(230)).apply {
                topMargin = dp(4)
                bottomMargin = dp(8)
            })
            mapView?.getMapAsync { map ->
                val eventPosition = LatLng(location.latitude, location.longitude)
                map.cameraPosition = CameraPosition.Builder()
                    .target(eventPosition)
                    .zoom(16.0)
                    .build()
                map.addMarker(MarkerOptions().position(eventPosition).title(event.title).snippet(event.place))
            }
        }
        body.addCampusText(event.description, 17f)

        val ratingSummary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(ratingSummary)
        loadRatingSummary(event.id, ratingSummary)

        body.addCampusText(getString(R.string.event_comments_title), 20f, bold = true)
        val (commentInputLayout, commentInput) = createCampusInput(
            getString(R.string.event_comment_hint), minLines = 2, maxLines = 5
        )
        body.addView(commentInputLayout)

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
        body.addView(commentsContainer)
        refreshComments()

        body.addCampusButton(getString(R.string.event_comment_publish)) {
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

        val favoriteButton = MaterialButton(this).apply {
            isAllCaps = false
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
        }
        body.addView(favoriteButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
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
        body.addView(ratingSection)
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
        body.addView(registrationButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
            bottomMargin = dp(6)
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
