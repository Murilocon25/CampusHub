package com.example.loginfirebase.ui.events

import android.text.format.Formatter
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.example.loginfirebase.R
import com.example.loginfirebase.data.repository.EventMedia
import com.example.loginfirebase.data.repository.EventMediaStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventVideoAdapter(
    private val currentUserUid: String,
    private val onWatch: (EventMedia) -> Unit,
    private val onDelete: (EventMedia) -> Unit
) :
    RecyclerView.Adapter<EventVideoAdapter.VideoViewHolder>() {
    private val videos = mutableListOf<EventMedia>()

    fun submitVideos(items: List<EventMedia>) {
        videos.clear()
        videos.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val context = parent.context
        val title = TextView(context).apply {
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(context.getColor(R.color.color_text_title))
        }
        val details = TextView(context).apply {
            textSize = 13f
            setTextColor(context.getColor(R.color.color_text_subtitle))
        }
        val watchButton = MaterialButton(context).apply {
            setText(R.string.event_video_watch)
            isAllCaps = false
            minHeight = dp(context, 42)
            insetTop = 0
            insetBottom = 0
        }
        val deleteButton = MaterialButton(context).apply {
            setText(R.string.event_video_delete)
            isAllCaps = false
            minHeight = dp(context, 42)
            insetTop = 0
            insetBottom = 0
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(watchButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(context, 4)
            })
            addView(deleteButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(context, 4)
            })
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(details, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(context, 4)
            })
            addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(context, 6)
            })
        }
        val card = MaterialCardView(context).apply {
            radius = dp(context, 12).toFloat()
            cardElevation = 0f
            strokeWidth = dp(context, 1)
            strokeColor = context.getColor(R.color.color_border)
            setCardBackgroundColor(context.getColor(R.color.color_surface))
            addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                bottomMargin = dp(context, 8)
            }
        }
        return VideoViewHolder(card, title, details, watchButton, deleteButton)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        val context = holder.itemView.context
        val isOwnedByCurrentUser = EventMediaStore.isOwnedBy(video, currentUserUid)
        val authorName = video.authorName
        val date = if (video.createdAtMillis > 0L) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
                .format(Date(video.createdAtMillis))
        } else {
            context.getString(R.string.event_media_date_unknown)
        }
        val duration = formatDuration(video.durationMs ?: 0L)
        val size = Formatter.formatShortFileSize(context, video.sizeBytes)

        holder.title.text = context.getString(R.string.event_video_item_title, authorName, date)
        holder.details.text = context.getString(R.string.event_video_item_details, duration, size)
        holder.itemView.contentDescription = context.getString(
            R.string.event_video_item_accessibility,
            authorName,
            date,
            duration,
            size
        )
        holder.watchButton.setOnClickListener { onWatch(video) }
        holder.deleteButton.isVisible = isOwnedByCurrentUser
        holder.deleteButton.setOnClickListener { onDelete(video) }
    }

    override fun getItemCount(): Int = videos.size

    class VideoViewHolder(
        itemView: MaterialCardView,
        val title: TextView,
        val details: TextView,
        val watchButton: MaterialButton,
        val deleteButton: MaterialButton
    ) : RecyclerView.ViewHolder(itemView)

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3600L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
