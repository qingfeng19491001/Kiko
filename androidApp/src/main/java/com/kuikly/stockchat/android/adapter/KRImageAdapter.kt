package com.kuikly.stockchat.android.adapter

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.kuikly.stockchat.android.StockChatApplication
import com.tencent.kuikly.core.render.android.KuiklyRenderViewContext
import com.tencent.kuikly.core.render.android.adapter.HRImageLoadOption
import com.tencent.kuikly.core.render.android.adapter.IKRImageAdapter

/**
 * 图片加载适配器：支持 http(s)、assets、file 以及 base64 四类图片源。
 */
object KRImageAdapter : IKRImageAdapter {

    override fun fetchDrawable(
        imageLoadOption: HRImageLoadOption,
        callback: (drawable: Drawable?) -> Unit,
    ) {
        when {
            imageLoadOption.isBase64() -> loadBase64(imageLoadOption, callback)
            imageLoadOption.isWebUrl() || imageLoadOption.isAssets() || imageLoadOption.isFile() ->
                loadWithGlide(imageLoadOption, callback)
            else -> callback(null)
        }
    }

    override fun getDrawableWidth(kuiklyRenderViewContext: KuiklyRenderViewContext, drawable: Drawable): Float {
        return drawable.intrinsicWidth.toFloat()
    }

    override fun getDrawableHeight(kuiklyRenderViewContext: KuiklyRenderViewContext, drawable: Drawable): Float {
        return drawable.intrinsicHeight.toFloat()
    }

    private fun loadWithGlide(option: HRImageLoadOption, callback: (Drawable?) -> Unit) {
        val src = if (option.isAssets()) {
            "file:///android_asset/" + option.src.substring(HRImageLoadOption.SCHEME_ASSETS.length)
        } else {
            option.src
        }
        val request: RequestBuilder<Drawable> = if (src.endsWith(".gif")) {
            @Suppress("UNCHECKED_CAST")
            Glide.with(StockChatApplication.instance).asGif().load(src) as RequestBuilder<Drawable>
        } else {
            Glide.with(StockChatApplication.instance).asDrawable().load(src)
        }
        if (option.needResize) {
            request.override(option.requestWidth, option.requestHeight)
            when (option.scaleType) {
                ImageView.ScaleType.CENTER_CROP -> request.centerCrop()
                ImageView.ScaleType.FIT_CENTER -> request.fitCenter()
                else -> Unit
            }
        }
        request.into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                callback(resource)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                callback(null)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                callback(null)
            }
        })
    }

    private fun loadBase64(option: HRImageLoadOption, callback: (Drawable?) -> Unit) {
        execOnSubThread {
            try {
                val payload = option.src.substringAfter(',')
                val bytes = Base64.decode(payload, Base64.DEFAULT)
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                callback(BitmapDrawable(Resources.getSystem(), bitmap))
            } catch (e: Throwable) {
                callback(null)
            }
        }
    }
}
