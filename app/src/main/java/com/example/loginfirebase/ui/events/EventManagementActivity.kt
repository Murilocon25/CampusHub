package com.example.loginfirebase.ui.events

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.model.toCampusEvent
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.EventRepository
import com.example.loginfirebase.data.repository.UserRole
import com.example.loginfirebase.ui.common.CampusStateView
import com.example.loginfirebase.ui.common.showCampusSnackbar
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventManagementActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    }

    private val repository = EventRepository()
    private val authRepository = AuthRepository()
    private lateinit var eventId: String
    private lateinit var stateView: CampusStateView
    private lateinit var participantList: RecyclerView
    private lateinit var adapter: ParticipantAdapter
    private lateinit var attendanceRequestList: RecyclerView
    private lateinit var attendanceRequestAdapter: AttendanceRequestAdapter
    private lateinit var search: TextInputEditText
    private var event: CampusEvent? = null
    private var participants: List<DocumentSnapshot> = emptyList()
    private var attendanceRequests: List<DocumentSnapshot> = emptyList()
    private var eventListener: ListenerRegistration? = null
    private var participantsListener: ListenerRegistration? = null
    private var attendanceRequestsListener: ListenerRegistration? = null
    private var canObserve = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        if (eventId.isBlank() || authRepository.currentUser == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_event_management)
        stateView = findViewById(R.id.event_manager_state)
        participantList = findViewById(R.id.event_manager_participants)
        attendanceRequestList = findViewById(R.id.event_manager_attendance_requests)
        search = findViewById(R.id.event_manager_search)
        adapter = ParticipantAdapter()
        attendanceRequestAdapter = AttendanceRequestAdapter()
        participantList.layoutManager = LinearLayoutManager(this)
        participantList.adapter = adapter
        attendanceRequestList.layoutManager = LinearLayoutManager(this)
        attendanceRequestList.adapter = attendanceRequestAdapter
        findViewById<MaterialToolbar>(R.id.event_manager_toolbar).setNavigationOnClickListener { finish() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.event_manager_publish).setOnClickListener {
            publishEvent()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.event_manager_end).setOnClickListener {
            confirmEndEvent()
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = adapter.filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        loadEvent()
    }

    private fun loadEvent() {
        val user = authRepository.currentUser ?: return finish()
        stateView.showMessage(getString(R.string.event_manager_loading))
        repository.getProfile(user.uid)
            .addOnSuccessListener { profile ->
                if (UserRole.fromFirestore(profile.getString("role")) != UserRole.ORGANIZER) {
                    showAccessDenied()
                    return@addOnSuccessListener
                }
                repository.getEvent(eventId)
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.exists() || snapshot.getString("creatorUid") != user.uid) {
                            showAccessDenied()
                            return@addOnSuccessListener
                        }
                        event = snapshot.toCampusEvent()
                        findViewById<TextView>(R.id.event_manager_event_title).text = event?.title.orEmpty()
                        stateView.hideMessage()
                        updateEventActions()
                        canObserve = true
                        attachLiveUpdates()
                        loadParticipants()
                    }
                    .addOnFailureListener { showAccessDenied() }
            }
            .addOnFailureListener { showAccessDenied() }
    }

    private fun showAccessDenied() {
        stateView.showMessage(getString(R.string.event_manager_access_denied))
        participantList.isVisible = false
        findViewById<View>(R.id.event_manager_attendance_requests_section).isVisible = false
        findViewById<View>(R.id.event_manager_actions).isVisible = false
    }

    private fun updateEventActions() {
        val current = event ?: return
        findViewById<View>(R.id.event_manager_publish).isVisible = current.status.firestoreValue == "DRAFT"
        findViewById<View>(R.id.event_manager_end).isVisible = current.status.firestoreValue == "PUBLISHED"
        val canCheckIn = current.status.firestoreValue in setOf("PUBLISHED", "ENDED")
        findViewById<View>(R.id.event_manager_checkins).isVisible = canCheckIn
        findViewById<View>(R.id.event_manager_limited_features).isVisible = current.status.firestoreValue == "PUBLISHED"
    }

    private fun publishEvent() {
        val publishButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.event_manager_publish)
        publishButton.isEnabled = false
        repository.publishEvent(eventId)
            .addOnSuccessListener {
                showMessage(R.string.event_manager_published)
                loadEvent()
            }
            .addOnFailureListener { error ->
                publishButton.isEnabled = true
                showMessage(publicationErrorMessage(error), Snackbar.LENGTH_LONG)
            }
    }

    private fun publicationErrorMessage(error: Exception): String {
        if (error !is FirebaseFirestoreException) return getString(R.string.event_manager_action_error)

        return when (error.code) {
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> getString(R.string.event_manager_publish_sign_in_error)
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> getString(R.string.event_manager_publish_rules_error)
            FirebaseFirestoreException.Code.NOT_FOUND -> getString(R.string.event_manager_publish_event_missing)
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> getString(R.string.event_manager_publish_network_error)
            else -> getString(R.string.event_manager_action_error)
        }
    }

    private fun confirmEndEvent() {
        AlertDialog.Builder(this)
            .setMessage(R.string.event_manager_end)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.event_manager_end) { _, _ ->
                repository.endEvent(eventId)
                    .addOnSuccessListener {
                        showMessage(R.string.event_manager_ended)
                        loadEvent()
                    }
                    .addOnFailureListener { showMessage(R.string.event_manager_action_error) }
            }
            .show()
    }

    private fun loadParticipants() {
        if (event == null) return
        stateView.showMessage(getString(R.string.event_manager_loading))
        repository.getParticipants(eventId)
            .addOnSuccessListener { snapshot ->
                showParticipants(snapshot.documents)
            }
            .addOnFailureListener {
                stateView.showMessage(getString(R.string.event_manager_load_error), getString(R.string.state_retry)) {
                    loadParticipants()
                }
            }
    }

    private fun attachLiveUpdates() {
        val uid = authRepository.currentUser?.uid ?: return
        if (eventListener == null) {
            eventListener = repository.observeEvent(eventId) { result ->
                result.onSuccess { snapshot ->
                    if (!snapshot.exists() || snapshot.getString("creatorUid") != uid) {
                        showAccessDenied()
                        return@onSuccess
                    }
                    event = snapshot.toCampusEvent()
                    findViewById<TextView>(R.id.event_manager_event_title).text = event?.title.orEmpty()
                    updateEventActions()
                    renderMetrics()
                }.onFailure {
                    stateView.showMessage(getString(R.string.event_manager_load_error))
                }
            }
        }
        if (participantsListener == null) {
            participantsListener = repository.observeParticipants(eventId) { result ->
                result.onSuccess { snapshot -> showParticipants(snapshot.documents) }
                    .onFailure { stateView.showMessage(getString(R.string.event_manager_load_error)) }
            }
        }
        if (attendanceRequestsListener == null) {
            attendanceRequestsListener = repository.observeAttendanceRequests(eventId) { result ->
                result.onSuccess { snapshot -> showAttendanceRequests(snapshot) }
                    .onFailure { showMessage(R.string.event_manager_attendance_requests_error) }
            }
        }
    }

    private fun showAttendanceRequests(snapshot: QuerySnapshot) {
        attendanceRequests = snapshot.documents.sortedByDescending {
            it.getTimestamp("requestedAt")?.toDate()?.time ?: 0L
        }
        attendanceRequestAdapter.submit(attendanceRequests)
        findViewById<View>(R.id.event_manager_attendance_requests_section).isVisible = attendanceRequests.isNotEmpty()
    }

    private fun confirmAttendanceRequest(snapshot: DocumentSnapshot) {
        val studentUid = snapshot.id
        val organizerUid = authRepository.currentUser?.uid
        if (organizerUid.isNullOrBlank()) {
            showMessage(R.string.event_manager_attendance_decision_error)
            return
        }
        attendanceRequestAdapter.setBusy(studentUid, true)
        repository.confirmAttendanceRequest(eventId, studentUid, organizerUid)
            .addOnSuccessListener {
                attendanceRequestAdapter.setBusy(studentUid, false)
                showMessage(getString(R.string.event_manager_attendance_approved, studentDisplayName(snapshot)))
            }
            .addOnFailureListener {
                attendanceRequestAdapter.setBusy(studentUid, false)
                showMessage(R.string.event_manager_attendance_decision_error)
            }
    }

    private fun studentDisplayName(snapshot: DocumentSnapshot): String =
        snapshot.getString("studentName")?.takeIf(String::isNotBlank)
            ?: getString(R.string.profile_student_fallback)

    private fun showParticipants(items: List<DocumentSnapshot>) {
        participants = items
        adapter.filter(search.text?.toString().orEmpty())
        stateView.isVisible = participants.isEmpty()
        if (participants.isEmpty()) stateView.showMessage(getString(R.string.event_manager_empty))
        else stateView.hideMessage()
        participantList.isVisible = participants.isNotEmpty()
        renderMetrics()
    }

    private fun renderMetrics() {
        val current = event ?: return
        val checkedIn = participants.count { it.getString("status") == "PRESENTE" }
        val occupied = current.confirmedCount.coerceAtLeast(0)
        val available = (current.capacity - occupied - current.reservedCount).coerceAtLeast(0)
        findViewById<TextView>(R.id.event_manager_capacity).text = getString(
            R.string.event_manager_capacity, occupied, current.capacity
        )
        findViewById<TextView>(R.id.event_manager_available).text = getString(R.string.event_manager_available, available)
        findViewById<TextView>(R.id.event_manager_waiting).text = getString(R.string.event_manager_waiting, current.waitingCount)
        findViewById<TextView>(R.id.event_manager_checkins).text = getString(R.string.event_manager_checkins, checkedIn)
    }

    override fun onStart() {
        super.onStart()
        if (canObserve) attachLiveUpdates()
    }

    override fun onStop() {
        eventListener?.remove()
        eventListener = null
        participantsListener?.remove()
        participantsListener = null
        attendanceRequestsListener?.remove()
        attendanceRequestsListener = null
        super.onStop()
    }

    private fun showMessage(message: Int) = findViewById<View>(android.R.id.content).showCampusSnackbar(message)
    private fun showMessage(message: String, duration: Int = Snackbar.LENGTH_SHORT) =
        findViewById<View>(android.R.id.content).showCampusSnackbar(message, duration)

    private inner class ParticipantAdapter : RecyclerView.Adapter<ParticipantAdapter.ParticipantHolder>() {
        private var visibleItems: List<DocumentSnapshot> = emptyList()

        fun filter(query: String) {
            val normalized = query.trim().lowercase(Locale.ROOT)
            visibleItems = if (normalized.isEmpty()) participants else participants.filter { snapshot ->
                snapshot.getString("studentName").orEmpty().lowercase(Locale.ROOT).contains(normalized) ||
                    snapshot.getString("studentEmail").orEmpty().lowercase(Locale.ROOT).contains(normalized)
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantHolder {
            val card = MaterialCardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
                }
                radius = resources.getDimension(R.dimen.shape_medium)
                cardElevation = 0f
                strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_width)
                strokeColor = getColor(R.color.color_border)
                setCardBackgroundColor(getColor(R.color.color_surface))
            }
            val content = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                val inset = resources.getDimensionPixelSize(R.dimen.space_3)
                setPadding(inset, inset, inset, inset)
                addView(TextView(parent.context).apply {
                    id = android.R.id.text1
                    setTextAppearance(R.style.TextAppearance_CampusHub_Body)
                })
                addView(TextView(parent.context).apply {
                    id = android.R.id.text2
                    setTextAppearance(R.style.TextAppearance_CampusHub_Caption)
                })
            }
            card.addView(content)
            return ParticipantHolder(card)
        }

        override fun onBindViewHolder(holder: ParticipantHolder, position: Int) = holder.bind(visibleItems[position])
        override fun getItemCount(): Int = visibleItems.size

        inner class ParticipantHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(snapshot: DocumentSnapshot) {
                val name = snapshot.getString("studentName")?.takeIf(String::isNotBlank)
                    ?: getString(R.string.profile_student_fallback)
                val email = snapshot.getString("studentEmail").orEmpty()
                val status = snapshot.getString("status").orEmpty()
                val registeredAt = snapshot.getTimestamp("registeredAt")?.toDate()?.let { dateFormat.format(it) }
                    ?: getString(R.string.not_informed)
                val checkedIn = status == "PRESENTE"
                itemView.findViewById<TextView>(android.R.id.text1).text = getString(
                    R.string.event_manager_participant, name, registrationStatus(status)
                )
                itemView.findViewById<TextView>(android.R.id.text2).text = getString(
                    R.string.event_manager_participant_detail,
                    email.ifBlank { getString(R.string.not_informed) },
                    registeredAt,
                    getString(if (checkedIn) R.string.event_manager_checked_in else R.string.event_manager_not_checked_in)
                )
            }
        }
    }

    private inner class AttendanceRequestAdapter : RecyclerView.Adapter<AttendanceRequestAdapter.RequestHolder>() {
        private var items: List<DocumentSnapshot> = emptyList()
        private val busyUids = mutableSetOf<String>()

        fun submit(requests: List<DocumentSnapshot>) {
            items = requests
            busyUids.retainAll(requests.mapTo(mutableSetOf()) { it.id })
            notifyDataSetChanged()
        }

        fun setBusy(uid: String, busy: Boolean) {
            if (busy) busyUids += uid else busyUids -= uid
            notifyItemChanged(items.indexOfFirst { it.id == uid }.takeIf { it >= 0 } ?: return)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestHolder {
            val card = MaterialCardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
                }
                radius = resources.getDimension(R.dimen.shape_medium)
                cardElevation = 0f
                strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_width)
                strokeColor = getColor(R.color.color_border)
                setCardBackgroundColor(getColor(R.color.color_surface))
            }
            val content = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                val inset = resources.getDimensionPixelSize(R.dimen.space_2)
                setPadding(inset, inset, inset, inset)
                addView(TextView(parent.context).apply {
                    id = android.R.id.text1
                    setTextAppearance(R.style.TextAppearance_CampusHub_Body)
                })
                addView(TextView(parent.context).apply {
                    id = android.R.id.text2
                    setTextAppearance(R.style.TextAppearance_CampusHub_Caption)
                })
                addView(LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.END
                    addView(com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                        id = R.id.event_manager_attendance_confirm
                        setText(R.string.event_manager_attendance_confirm)
                        isAllCaps = false
                    })
                })
            }
            card.addView(content)
            return RequestHolder(card)
        }

        override fun onBindViewHolder(holder: RequestHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        inner class RequestHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(snapshot: DocumentSnapshot) {
                val uid = snapshot.id
                val name = studentDisplayName(snapshot)
                val email = snapshot.getString("studentEmail").orEmpty()
                val requestedAt = snapshot.getTimestamp("requestedAt")?.toDate()?.let { dateFormat.format(it) }
                    ?: getString(R.string.not_informed)
                itemView.findViewById<TextView>(android.R.id.text1).text = name
                itemView.findViewById<TextView>(android.R.id.text2).text = getString(
                    R.string.event_manager_attendance_request_detail,
                    email.ifBlank { getString(R.string.not_informed) },
                    requestedAt,
                )
                val busy = uid in busyUids
                itemView.findViewById<View>(R.id.event_manager_attendance_confirm).apply {
                    isEnabled = !busy
                    setOnClickListener { confirmAttendanceRequest(snapshot) }
                }
            }
        }
    }

    private fun registrationStatus(status: String): String = when (status) {
        "CONFIRMADA" -> "Confirmada"
        "LISTA_ESPERA" -> "Lista de espera"
        "AGUARDANDO_CONFIRMACAO" -> "Aguardando confirmação"
        "PRESENTE" -> "Presente"
        "CANCELADA" -> "Cancelada"
        else -> status
    }

}
