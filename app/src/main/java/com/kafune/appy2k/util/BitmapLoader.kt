package com.kafune.appy2k.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File

/**
 * Carrega um bitmap de content:// ou file://, já com rotação EXIF aplicada
 * (ImageDecoder cuida disso), limitado a [maxLongEdge] no lado maior.
 */
fun loadBitmap(context: Context, uri: Uri, maxLongEdge: Int): Bitmap {
    val source = if (uri.scheme == "file") {
        ImageDecoder.createSource(File(uri.path!!))
    } else {
        ImageDecoder.createSource(context.contentResolver, uri)
    }
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
        val w = info.size.width
        val h = info.size.height
        val longEdge = maxOf(w, h)
        if (longEdge > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / longEdge
            decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
    }
}
