package com.example.loginfirebase.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.EventComment
import com.google.android.material.button.MaterialButton
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
        private val actions: LinearLayout = itemView.findViewById(R.id.comment_actions)
        private val edit: MaterialButton = itemView.findViewById(R.id.comment_edit)
        private val delete: MaterialButton = itemView.findViewById(R.id.comment_delete)

        fun bind(comment: EventComment) {
            val context = itemView.context
            val date = comment.createdAt?.let {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(it)
            } ?: context.getString(R.string.event_comment_pending)
            authorDate.text = context.getString(R.string.event_comment_author_date, comment.authorName, date)
            commentText.text = comment.text
            actions.visibility = if (comment.authorUid == currentUserId) View.VISIBLE else View.GONE
            edit.setOnClickListener { onEdit(comment) }
            delete.setOnClickListener { onDelete(comment) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<EventComment>() {
            override fun areItemsTheSame(oldItem: EventComment, newItem: EventComment) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: EventComment, newItem: EventComment) = oldItem == newItem
        }
    }
}
