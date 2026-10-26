package com.example.loginfirebase.ui.events

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.R
import java.io.File
import java.util.concurrent.Executors

class EventPhotoAdapter : RecyclerView.Adapter<EventPhotoAdapter.PhotoViewHolder>() {
    private val photos = mutableListOf<File>()

    fun submitPhotos(items: List<File>) {
        photos.clear()
        photos.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val context = parent.context
        val imageSize = (112 * context.resources.displayMetrics.density).toInt()
        val image = ImageView(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, imageSize)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.color_surface))
            contentDescription = context.getString(R.string.event_photo_content_description)
            clipToOutline = true
        }
        return PhotoViewHolder(image)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        val path = photo.absolutePath
        val targetSize = (224 * holder.itemView.resources.displayMetrics.density).toInt()
        holder.image.tag = path
        holder.image.setImageBitmap(null)
        decoder.execute {
            val bitmap = decodeThumbnail(photo, targetSize)
            mainHandler.post {
                if (holder.image.tag == path && bitmap != null) {
                    holder.image.setImageBitmap(bitmap)
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

    class PhotoViewHolder(val image: ImageView) : RecyclerView.ViewHolder(image)

    companion object {
        private val decoder = Executors.newFixedThreadPool(2)
        private val mainHandler = Handler(Looper.getMainLooper())

        private fun decodeThumbnail(file: File, targetSize: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > targetSize * 2 ||
                bounds.outHeight / sampleSize > targetSize * 2
            ) {
                sampleSize *= 2
            }
            return BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        }
    }
}
