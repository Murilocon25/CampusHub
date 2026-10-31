package com.example.loginfirebase.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.EventComment
import android.widget.PopupMenu
import java.text.SimpleDateFormat
import java.util.Locale

class CommentAdapter(
    private val currentUserId: String,
    private val onEdit: (EventComment) -> Unit,
    private val onDelete: (EventComment) -> Unit
) : ListAdapter<EventComment, CommentAdapter.CommentViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder = CommentViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
    )

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) = holder.bind(getItem(position))

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val authorDate: TextView = itemView.findViewById(R.id.comment_author_date)
        private val commentText: TextView = itemView.findViewById(R.id.comment_text)
        private val avatar: TextView = itemView.findViewById(R.id.comment_avatar)
        private val actions: ImageButton = itemView.findViewById(R.id.comment_actions)

        fun bind(comment: EventComment) {
            val context = itemView.context
            val date = comment.createdAt?.let { relativeTime(context, it) }
                ?: context.getString(R.string.event_comment_pending)
            authorDate.text = context.getString(R.string.event_comment_author_date, comment.authorName, date)
            avatar.text = comment.authorName.trim().split(Regex("\\s+"))
                .filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull()?.uppercase() }
                .joinToString("").ifBlank { "?" }
            commentText.text = comment.text
            actions.visibility = if (comment.authorUid == currentUserId) View.VISIBLE else View.GONE
            actions.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    inflate(R.menu.comment_actions)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.comment_edit -> { onEdit(comment); true }
                            R.id.comment_delete -> { onDelete(comment); true }
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<EventComment>() {
            override fun areItemsTheSame(oldItem: EventComment, newItem: EventComment) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: EventComment, newItem: EventComment) = oldItem == newItem
        }
    }

    private fun relativeTime(context: android.content.Context, date: java.util.Date): String {
        val elapsed = (System.currentTimeMillis() - date.time).coerceAtLeast(0L)
        return when {
            elapsed < 60_000L -> context.getString(R.string.relative_time_now)
            elapsed < 3_600_000L -> context.resources.getQuantityString(
                R.plurals.relative_minutes_ago, (elapsed / 60_000L).toInt(), (elapsed / 60_000L).toInt()
            )
            elapsed < 86_400_000L -> context.resources.getQuantityString(
                R.plurals.relative_hours_ago, (elapsed / 3_600_000L).toInt(), (elapsed / 3_600_000L).toInt()
            )
            elapsed < 2_592_000_000L -> context.resources.getQuantityString(
                R.plurals.relative_days_ago, (elapsed / 86_400_000L).toInt(), (elapsed / 86_400_000L).toInt()
            )
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(date)
        }
    }
}
