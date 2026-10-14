package com.example.loginfirebase

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth

class EventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireUser()) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }

        val root = buildCampusScreen(getString(R.string.events_title))
        val body = root.tag as LinearLayout

        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

        fun makeText(
            value: String,
            size: Float,
            color: Int,
            bold: Boolean = false
        ) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

        body.addView(
            makeText(getString(R.string.events_intro), 15f, getColor(R.color.text_subtitle)),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) }
        )

        body.addView(
            makeText("Todos os eventos", 19f, getColor(R.color.text_title), bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        )

        val searchInput = EditText(this).apply {
            hint = "Buscar pelo título do evento"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isSingleLine = true
            textSize = 15f
            setTextColor(getColor(R.color.text_title))
            setHintTextColor(getColor(R.color.text_hint))
            background = getDrawable(R.drawable.edittext_background)
            setPadding(dp(16), dp(4), dp(16), dp(4))
            minHeight = dp(54)
            contentDescription = "Buscar eventos pelo título"
        }
        body.addView(searchInput, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(20)
        })

        body.addView(
            makeText("REFINE A BUSCA", 12f, getColor(R.color.text_subtitle), bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        )

        val filtersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        fun createFilterColumn(label: String): Pair<LinearLayout, Spinner> {
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val labelView = makeText(label, 13f, getColor(R.color.text_body), bold = true)
            column.addView(labelView, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            })
            val spinnerContainer = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), getColor(R.color.login_field_stroke))
                }
            }
            val spinner = Spinner(this).apply {
                background = ColorDrawable(Color.TRANSPARENT)
                minimumHeight = dp(50)
                setPadding(dp(8), 0, dp(32), 0)
            }
            spinnerContainer.addView(spinner, FrameLayout.LayoutParams(-1, dp(50)))
            spinnerContainer.addView(
                TextView(this).apply {
                    text = "▾"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(getColor(R.color.text_subtitle))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(dp(34), -1, Gravity.END or Gravity.CENTER_VERTICAL)
            )
            column.addView(spinnerContainer, LinearLayout.LayoutParams(-1, -2))
            return column to spinner
        }

        val (categoryColumn, categoryFilter) = createFilterColumn("Categoria")
        val (statusColumn, statusFilter) = createFilterColumn("Situação")
        filtersRow.addView(categoryColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(8)
        })
        filtersRow.addView(statusColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(8)
        })
        body.addView(filtersRow, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(22)
        })

        val resultsLabel = makeText("Carregando eventos...", 14f, getColor(R.color.text_subtitle), bold = true)
        body.addView(resultsLabel, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })

        val eventList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(eventList)

        fun showNotice(message: String) {
            eventList.removeAllViews()
            val notice = MaterialCardView(this).apply {
                setCardBackgroundColor(Color.WHITE)
                strokeColor = getColor(R.color.divider)
                strokeWidth = dp(1)
                radius = dp(16).toFloat()
                cardElevation = dp(1).toFloat()
                setContentPadding(dp(18), dp(22), dp(18), dp(22))
            }
            notice.addView(makeText(message, 15f, getColor(R.color.text_subtitle)))
            eventList.addView(notice, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
        }

        fun addEventCard(event: CampusEvent) {
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(Color.WHITE)
                strokeColor = getColor(R.color.divider)
                strokeWidth = dp(1)
                radius = dp(18).toFloat()
                cardElevation = dp(2).toFloat()
                setContentPadding(dp(16), dp(16), dp(16), dp(14))
            }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            val badges = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            fun badge(label: String, backgroundColor: Int, textColor: Int) = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(textColor)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply {
                    setColor(backgroundColor)
                    cornerRadius = dp(30).toFloat()
                }
            }
            val categoryName = event.category.ifBlank { "Evento" }
            badges.addView(badge(categoryName, getColor(R.color.screen_background), getColor(R.color.text_body)))
            val statusLabel = if (event.hasEnded()) "ENCERRADO" else "PRÓXIMO"
            val statusColor = if (event.hasEnded()) getColor(R.color.divider) else Color.rgb(235, 244, 237)
            val statusTextColor = if (event.hasEnded()) getColor(R.color.text_subtitle) else Color.rgb(48, 105, 62)
            badges.addView(badge(statusLabel, statusColor, statusTextColor), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(8)
            })
            content.addView(badges)

            content.addView(
                makeText(event.title, 18f, getColor(R.color.text_title), bold = true),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(12)
                    bottomMargin = dp(14)
                }
            )

            fun addMetadata(label: String, value: String) {
                content.addView(makeText(label.uppercase(), 10f, getColor(R.color.text_hint), bold = true))
                content.addView(
                    makeText(value.ifBlank { "Não informado" }, 14f, getColor(R.color.text_body)),
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
                )
            }
            addMetadata("Data e horário", event.date)
            addMetadata("Local", event.place)

            val detailsButton = MaterialButton(this).apply {
                text = getString(R.string.event_details_button)
                isAllCaps = false
                textSize = 14f
                cornerRadius = dp(12)
                insetTop = 0
                insetBottom = 0
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.login_button))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    startActivity(
                        Intent(this@EventsActivity, EventDetailActivity::class.java)
                            .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                    )
                }
            }
            content.addView(detailsButton, LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(4)
            })
            card.addView(content)
            eventList.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            })
        }

        showNotice(getString(R.string.events_loading))

        FirebaseFirestoreProvider.db.collection("events").get()
            .addOnSuccessListener { snapshot ->
                val events = snapshot.documents.map { it.toCampusEvent() }

                val categories = listOf("Todas") +
                    events.map { it.category }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                categoryFilter.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    categories
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                val statuses = listOf("Todas", "Próximos", "Encerrados")
                statusFilter.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    statuses
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                fun renderEvents() {
                    eventList.removeAllViews()

                    val query = searchInput.text.toString().trim()
                    val selectedCategory = categoryFilter.selectedItem?.toString()
                        ?: "Todas"
                    val selectedStatus = statusFilter.selectedItem?.toString()
                        ?: "Todas"

                    val filteredEvents = events.filter { event ->
                        val matchesTitle = event.title.contains(query, ignoreCase = true)
                        val matchesCategory = selectedCategory == "Todas" ||
                            event.category == selectedCategory
                        val matchesStatus = when (selectedStatus) {
                            "Próximos" -> !event.hasEnded()
                            "Encerrados" -> event.hasEnded()
                            else -> true
                        }
                        matchesTitle && matchesCategory && matchesStatus
                    }

                    if (filteredEvents.isEmpty()) {
                        resultsLabel.text = "Nenhum evento encontrado"
                        showNotice(
                            if (events.isEmpty()) getString(R.string.events_empty)
                            else "Nenhum evento corresponde à busca e aos filtros. Tente mudar os critérios."
                        )
                        return
                    }

                    val countLabel = if (filteredEvents.size == 1) "evento encontrado" else "eventos encontrados"
                    resultsLabel.text = "${filteredEvents.size} $countLabel"
                    filteredEvents.forEach { event ->
                        addEventCard(event)
                    }
                }

                renderEvents()

                searchInput.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?, start: Int, count: Int, after: Int
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?, start: Int, before: Int, count: Int
                    ) {
                        renderEvents()
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                })

                val filterListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        renderEvents()
                    }
                }

                categoryFilter.onItemSelectedListener = filterListener
                statusFilter.onItemSelectedListener = filterListener
            }
            .addOnFailureListener {
                resultsLabel.text = "Não foi possível carregar os eventos"
                showNotice(getString(R.string.events_load_error))
            }
    }
}

class MyEventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val root = buildCampusScreen(getString(R.string.my_events_title))
        val body = root.tag as LinearLayout
        body.addCampusText(getString(R.string.my_events_subtitle), 15f)

        val eventList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(eventList)
        eventList.addCampusText(getString(R.string.my_events_loading))

        FirebaseFirestoreProvider.db
            .registrations(user.uid)
            .get()
            .addOnSuccessListener { registrations ->
                eventList.removeAllViews()

                val eventIds = registrations.documents.map { it.id }

                if (eventIds.isEmpty()) {
                    eventList.addCampusText(getString(R.string.my_events_empty))
                    return@addOnSuccessListener
                }

                eventIds.forEach { eventId ->
                    FirebaseFirestoreProvider.db
                        .collection("events")
                        .document(eventId)
                        .get()
                        .addOnSuccessListener { eventDocument ->
                            if (eventDocument.exists()) {
                                val event = eventDocument.toCampusEvent()

                                eventList.addCampusText(
                                    getString(R.string.my_event_item, event.title, event.date, event.place),
                                    17f,
                                    bold = true
                                )
                                eventList.addCampusButton(getString(R.string.my_event_open)) {
                                    startActivity(
                                        Intent(this, EventDetailActivity::class.java)
                                            .putExtra(
                                                EventDetailActivity.EXTRA_EVENT_ID,
                                                event.id
                                            )
                                    )
                                }
                            } else {
                                eventList.addCampusText(getString(R.string.my_event_missing))
                            }
                        }
                        .addOnFailureListener {
                            eventList.addCampusText(getString(R.string.my_event_load_error))
                        }
                }
            }
            .addOnFailureListener {
                eventList.removeAllViews()
                eventList.addCampusText(getString(R.string.my_events_load_error))
            }
    }
}

object FirebaseFirestoreProvider { val db by lazy { com.google.firebase.firestore.FirebaseFirestore.getInstance() } }
