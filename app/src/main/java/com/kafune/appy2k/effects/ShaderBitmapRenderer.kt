package com.kafune.appy2k.effects

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader

/**
 * Renderiza um Bitmap através de um RuntimeShader na GPU, offscreen.
 * (RuntimeShader não funciona em Canvas de software, então o caminho é
 * HardwareRenderer -> ImageReader -> wrapHardwareBuffer.)
 */
object ShaderBitmapRenderer {

    fun render(source: Bitmap, params: EffectParams): Bitmap {
        val width = source.width
        val height = source.height

        val shader = buildY2kShader().apply {
            applyParams(params, width.toFloat(), height.toFloat())
            setInputShader(
                "src",
                BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            )
        }

        val imageReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val renderNode = RenderNode("y2k").apply { setPosition(0, 0, width, height) }
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(imageReader.surface)
            renderer.setContentRoot(renderNode)

            val canvas = renderNode.beginRecording(width, height)
            val paint = Paint().also { it.shader = shader }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            renderNode.endRecording()

            renderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()

            val image = imageReader.acquireNextImage()
                ?: error("HardwareRenderer não produziu imagem")
            image.use {
                val buffer = it.hardwareBuffer
                    ?: error("Imagem sem HardwareBuffer")
                buffer.use { hb ->
                    val hwBitmap = Bitmap.wrapHardwareBuffer(hb, null)
                        ?: error("wrapHardwareBuffer falhou")
                    // mutável: o timestamp é desenhado por cima depois
                    return hwBitmap.copy(Bitmap.Config.ARGB_8888, true)
                }
            }
        } finally {
            renderer.destroy()
            renderNode.discardDisplayList()
            imageReader.close()
        }
    }
}
