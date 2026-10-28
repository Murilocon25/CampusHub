package com.example.loginfirebase.ui.events

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.R
import com.example.loginfirebase.data.repository.EventMedia
import com.example.loginfirebase.data.repository.EventMediaStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class EventPhotoAdapter(
    private val currentUserUid: String,
    private val onDelete: (EventMedia) -> Unit
) : RecyclerView.Adapter<EventPhotoAdapter.PhotoViewHolder>() {
    private val photos = mutableListOf<EventMedia>()

    fun submitPhotos(items: List<EventMedia>) {
        photos.clear()
        photos.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val context = parent.context
        val imageSize = (112 * context.resources.displayMetrics.density).toInt()
        val image = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, imageSize)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.color_surface))
            contentDescription = context.getString(R.string.event_photo_content_description)
            clipToOutline = true
        }
        val author = TextView(context).apply {
            textSize = 12f
            setTextColor(context.getColor(R.color.color_text_title))
            maxLines = 2
        }
        val date = TextView(context).apply {
            textSize = 11f
            setTextColor(context.getColor(R.color.color_text_subtitle))
            maxLines = 1
        }
        val delete = MaterialButton(context).apply {
            setText(R.string.event_photo_delete)
            isAllCaps = false
            minHeight = (36 * context.resources.displayMetrics.density).toInt()
            insetTop = 0
            insetBottom = 0
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            addView(image)
            addView(author, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 4) })
            addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 2) })
            addView(delete, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 2) })
        }
        val card = MaterialCardView(context).apply {
            radius = dp(context, 10).toFloat()
            cardElevation = 0f
            strokeWidth = dp(context, 1)
            strokeColor = context.getColor(R.color.color_border)
            setCardBackgroundColor(context.getColor(R.color.color_surface))
            addView(content)
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) }
        }
        return PhotoViewHolder(card, image, author, date, delete)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        val context = holder.itemView.context
        val dateText = formatDate(context, photo.createdAtMillis)
        holder.image.tag = photo.id
        holder.image.setImageBitmap(null)
        holder.author.text = photo.authorName
        holder.date.text = dateText
        holder.deleteButton.isVisible = EventMediaStore.isOwnedBy(photo, currentUserUid)
        holder.deleteButton.setOnClickListener { onDelete(photo) }
        holder.itemView.contentDescription = context.getString(
            R.string.event_photo_item_accessibility,
            photo.authorName,
            dateText
        )

        thumbnailCache.get(photo.id)?.let {
            holder.image.setImageBitmap(it)
            return
        }
        val thumbnailFile = try {
            File.createTempFile("event_photo_", ".img", context.cacheDir)
        } catch (_: Exception) {
            return
        }
        EventMediaStore.downloadToFile(photo, thumbnailFile) { result ->
            if (result.isFailure) {
                thumbnailFile.delete()
                return@downloadToFile
            }
            decoder.execute {
                val bitmap = decodeThumbnail(thumbnailFile, dp(context, 224))
                thumbnailFile.delete()
                if (bitmap != null) {
                    thumbnailCache.put(photo.id, bitmap)
                    mainHandler.post {
                        if (holder.image.tag == photo.id) holder.image.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        holder.image.tag = null
        holder.image.setImageBitmap(null)
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = photos.size

    class PhotoViewHolder(
        itemView: MaterialCardView,
        val image: ImageView,
        val author: TextView,
        val date: TextView,
        val deleteButton: MaterialButton
    ) : RecyclerView.ViewHolder(itemView)

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private val thumbnailCache = object : LruCache<String, Bitmap>(12 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
        private val decoder = Executors.newFixedThreadPool(2)
        private val mainHandler = Handler(Looper.getMainLooper())

        private fun formatDate(context: android.content.Context, timestamp: Long): String =
            if (timestamp <= 0L) context.getString(R.string.event_media_date_unknown)
            else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
                .format(Date(timestamp))

        private fun decodeThumbnail(file: File, targetSize: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > targetSize * 2 ||
                bounds.outHeight / sampleSize > targetSize * 2
            ) sampleSize *= 2
            return BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        }
    }
}
