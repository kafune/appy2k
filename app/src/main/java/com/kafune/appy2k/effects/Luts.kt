package com.kafune.appy2k.effects

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.pow

/**
 * LUTs 3D emulando a color science de digicams específicas, geradas
 * parametricamente em runtime (nada de assets binários).
 *
 * Layout da textura: N fatias de N×N lado a lado numa faixa (N*N)×N.
 * Fatia = canal azul; dentro da fatia, x = vermelho, y = verde.
 * O shader faz lookup bilinear na fatia + blend entre duas fatias
 * (trilinear na prática).
 */
object Luts {

    const val SIZE = 32

    const val NONE = "none"

    data class CameraLut(val id: String, val name: String)

    /** Ordem também usada nos chips da UI. */
    val ALL = listOf(
        CameraLut(NONE, "sem lut"),
        CameraLut("sony_ccd", "Sony CCD"),
        CameraLut("canon_ixus", "Canon IXUS"),
        CameraLut("kodak_easyshare", "Kodak"),
        CameraLut("fuji_finepix", "Fuji"),
    )

    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private val shaderCache = mutableMapOf<String, BitmapShader>()

    fun shader(id: String): BitmapShader = synchronized(this) {
        shaderCache.getOrPut(id) {
            BitmapShader(bitmap(id), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
    }

    private fun bitmap(id: String): Bitmap =
        bitmapCache.getOrPut(id) { generate(transformFor(id)) }

    private fun transformFor(id: String): (Float, Float, Float) -> FloatArray = when (id) {
        // Cyber-shot: frio, sombras teal, saturação de CCD punchy
        "sony_ccd" -> look(
            wbR = 0.97f, wbG = 1.00f, wbB = 1.06f,
            gamma = 0.94f, contrast = 0.18f, saturation = 1.18f,
            shadowTint = floatArrayOf(-0.03f, 0.012f, 0.045f),
            highlightTint = floatArrayOf(0.015f, 0.0f, 0.03f),
        )
        // IXUS: quente nos médios, highlight puxando magenta
        "canon_ixus" -> look(
            wbR = 1.05f, wbG = 1.00f, wbB = 0.97f,
            gamma = 0.92f, contrast = 0.22f, saturation = 1.12f,
            shadowTint = floatArrayOf(-0.01f, 0.0f, 0.02f),
            highlightTint = floatArrayOf(0.045f, -0.012f, 0.03f),
        )
        // EasyShare: amarelo quente, vermelhos gritados
        "kodak_easyshare" -> look(
            wbR = 1.09f, wbG = 1.03f, wbB = 0.90f,
            gamma = 0.90f, contrast = 0.15f, saturation = 1.25f,
            shadowTint = floatArrayOf(0.02f, 0.008f, -0.015f),
            highlightTint = floatArrayOf(0.05f, 0.02f, -0.02f),
        )
        // FinePix: esverdeado, contraste macio
        "fuji_finepix" -> look(
            wbR = 0.97f, wbG = 1.04f, wbB = 1.00f,
            gamma = 1.03f, contrast = 0.08f, saturation = 1.06f,
            shadowTint = floatArrayOf(-0.015f, 0.025f, 0.01f),
            highlightTint = floatArrayOf(0.0f, 0.015f, 0.01f),
        )
        else -> { r, g, b -> floatArrayOf(r, g, b) } // identidade
    }

    /**
     * Um "look" genérico de digicam: white balance -> gamma ->
     * contraste em S -> tint por faixa de luma -> saturação.
     */
    private fun look(
        wbR: Float, wbG: Float, wbB: Float,
        gamma: Float, contrast: Float, saturation: Float,
        shadowTint: FloatArray, highlightTint: FloatArray,
    ): (Float, Float, Float) -> FloatArray = { r0, g0, b0 ->
        var r = (r0 * wbR).coerceIn(0f, 1f).pow(gamma)
        var g = (g0 * wbG).coerceIn(0f, 1f).pow(gamma)
        var b = (b0 * wbB).coerceIn(0f, 1f).pow(gamma)

        fun sCurve(x: Float): Float {
            val smooth = x * x * (3f - 2f * x)
            return x + (smooth - x) * contrast * (1f - abs(x - 0.5f))
        }
        r = sCurve(r); g = sCurve(g); b = sCurve(b)

        val luma = 0.299f * r + 0.587f * g + 0.114f * b
        val sw = (1f - luma) * (1f - luma)
        val hw = luma * luma
        r += shadowTint[0] * sw + highlightTint[0] * hw
        g += shadowTint[1] * sw + highlightTint[1] * hw
        b += shadowTint[2] * sw + highlightTint[2] * hw

        val l2 = 0.299f * r + 0.587f * g + 0.114f * b
        floatArrayOf(
            (l2 + (r - l2) * saturation).coerceIn(0f, 1f),
            (l2 + (g - l2) * saturation).coerceIn(0f, 1f),
            (l2 + (b - l2) * saturation).coerceIn(0f, 1f),
        )
    }

    private fun generate(transform: (Float, Float, Float) -> FloatArray): Bitmap {
        val n = SIZE
        val width = n * n
        val pixels = IntArray(width * n)
        val step = 1f / (n - 1)
        for (z in 0 until n) {          // azul -> fatia
            val b = z * step
            for (y in 0 until n) {      // verde -> linha
                val g = y * step
                val rowBase = y * width + z * n
                for (x in 0 until n) {  // vermelho -> coluna
                    val out = transform(x * step, g, b)
                    pixels[rowBase + x] = Color.rgb(
                        (out[0] * 255f + 0.5f).toInt(),
                        (out[1] * 255f + 0.5f).toInt(),
                        (out[2] * 255f + 0.5f).toInt(),
                    )
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, n, Bitmap.Config.ARGB_8888)
    }
}
