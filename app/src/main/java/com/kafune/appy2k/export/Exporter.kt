package com.kafune.appy2k.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.MediaStore
import com.kafune.appy2k.effects.EffectParams
import com.kafune.appy2k.effects.JpegCrusher
import com.kafune.appy2k.effects.ShaderBitmapRenderer
import com.kafune.appy2k.effects.TimestampRenderer
import com.kafune.appy2k.util.loadBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pipeline de export, em ordem:
 *  1. decodifica na resolução de digicam do preset (ex: 1600px)
 *  2. roda o über-shader na GPU em resolução cheia
 *  3. desenha o timestamp 7-seg por cima (fora do shader, como uma
 *     digicam de verdade faria)
 *  4. compressão JPEG real em N gerações
 *  5. salva em Pictures/appy2k via MediaStore
 */
object Exporter {

    suspend fun export(context: Context, source: Uri, params: EffectParams): Uri =
        withContext(Dispatchers.Default) {
            val input = loadBitmap(context, source, params.outputLongEdge)
            var result = ShaderBitmapRenderer.render(input, params)
            input.recycle()

            if (params.timestamp) {
                TimestampRenderer.draw(
                    Canvas(result), result.width, result.height,
                    yearOffset = params.timestampYearOffset,
                )
            }

            result = JpegCrusher.crush(result, params.jpegQuality, params.jpegPasses)
            saveToGallery(context, result)
        }

    private fun saveToGallery(context: Context, bitmap: Bitmap): Uri {
        val name = "appy2k_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/appy2k")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore.insert falhou")
        resolver.openOutputStream(uri)!!.use { out ->
            // qualidade alta aqui: a degradação já foi aplicada pelo crusher
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
