package com.herbornsoftware.photobooth

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.herbornsoftware.photobooth.core.GoPro
import kotlinx.android.synthetic.main.image_preview.view.*

class ImagePreviewAdapter(val images: MutableList<GoPro.GoProFile> = mutableListOf()) : RecyclerView.Adapter<ImagePreviewAdapter.ImagePreviewViewHolder>() {

    var onRemoveClicked: (image: GoPro.GoProFile) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ImagePreviewViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.image_preview, parent, false))

    override fun onBindViewHolder(holder: ImagePreviewViewHolder, position: Int) {
        val image = images[position]
        val view = holder.itemView
        Glide.with(view.context).load(image.url).into(view.previewImage)
        view.deleteBtn.setOnClickListener {
            onRemoveClicked(image)
        }
    }

    override fun getItemCount() = images.size

    fun deleteImage(image: GoPro.GoProFile) =
        deleteImage(images.indexOf(image))

    fun deleteImage(index: Int): Int {
        images.removeAt(index)
        notifyItemRemoved(index)
        return index
    }

    fun addImage(index: Int, image: GoPro.GoProFile) {
        images.add(index, image)
        notifyItemInserted(index)
    }

    fun clear() {
        while (images.size > 0) {
            deleteImage(0)
        }
    }

    class ImagePreviewViewHolder(view: View) : RecyclerView.ViewHolder(view)
}