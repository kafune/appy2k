package com.kafune.appy2k.effects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Artefatos JPEG de verdade: comprime e re-decodifica o bitmap N vezes.
 * Cada "geração" acumula blocking e mosquito noise, igual foto que
 * circulou por MSN -> Fotolog -> Orkut.
 */
object JpegCrusher {

    fun crush(source: Bitmap, quality: Int, passes: Int): Bitmap {
        if (quality >= 95 || passes <= 0) return source
        var current = source
        repeat(passes.coerceIn(1, 4)) {
            val out = ByteArrayOutputStream()
            current.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(5, 95), out)
            val bytes = out.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (current !== source) current.recycle()
            current = decoded
        }
        return current
    }
}
