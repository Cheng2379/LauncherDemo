package com.example.launcherdemo.adapter

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.databinding.BindingAdapter

/**
 *
 * @author cheng
 * @since 2025/4/17
 */

@BindingAdapter("imageDrawable")
fun bindingImageDrawable(
    imageView: ImageView,
    drawable: Drawable?
) {
    drawable?.let {
        imageView.setImageDrawable(it)
    }
    imageView.clipToOutline = true
}

@BindingAdapter("imageBitmap")
fun bindingImageBitmap(
    imageView: ImageView,
    bitmap: Bitmap?
) {
    bitmap?.let {
        imageView.setImageBitmap(it)
    }
    imageView.clipToOutline = true
}