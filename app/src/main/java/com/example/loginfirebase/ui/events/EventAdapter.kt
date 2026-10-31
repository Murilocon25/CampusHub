package com.example.loginfirebase.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.google.android.material.chip.Chip
import com.google.android.material.card.MaterialCardView

class EventAdapter(private val onOpen: (CampusEvent) -> Unit) :
    ListAdapter<CampusEvent, EventAdapter.EventViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder = EventViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
    )

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) = holder.bind(getItem(position))

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val category: Chip = itemView.findViewById(R.id.event_category)
        private val status: Chip = itemView.findViewById(R.id.event_status)
        private val title: TextView = itemView.findViewById(R.id.event_title)
        private val date: TextView = itemView.findViewById(R.id.event_date)
        private val place: TextView = itemView.findViewById(R.id.event_place)
        private val open: View = itemView.findViewById(R.id.event_open)

        fun bind(event: CampusEvent) {
            val context = itemView.context
            category.text = event.category.ifBlank { context.getString(R.string.events_category_fallback) }
            title.text = event.title
            date.text = context.getString(R.string.event_date, event.displayDate())
            place.text = context.getString(R.string.event_place, event.place.ifBlank { context.getString(R.string.not_informed) })
            val ended = event.hasEnded()
            status.text = context.getString(if (ended) R.string.event_status_ended else R.string.event_status_upcoming)
            status.setChipBackgroundColorResource(if (ended) R.color.color_surface_variant else R.color.color_primary_container)
            status.setTextColor(context.getColor(if (ended) R.color.color_text_subtitle else R.color.color_on_primary_container))
            card.contentDescription = context.getString(R.string.event_card_description, event.title)
            open.setOnClickListener { onOpen(event) }
            card.setOnClickListener { onOpen(event) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CampusEvent>() {
            override fun areItemsTheSame(oldItem: CampusEvent, newItem: CampusEvent) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CampusEvent, newItem: CampusEvent) = oldItem == newItem
        }
    }
}
