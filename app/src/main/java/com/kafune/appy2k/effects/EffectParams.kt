package com.kafune.appy2k.effects

/**
 * Parâmetros de um preset. Os campos de shader vão direto pros uniforms
 * do [Y2K_SHADER_SRC]; os demais controlam o pipeline de export
 * (compressão JPEG real, resolução de digicam, timestamp).
 */
data class EffectParams(
    // shader
    val softness: Float = 0f,      // 0..1 blur de digicam
    val ca: Float = 0f,            // 0..1 aberração cromática
    val bloom: Float = 0f,         // 0..1.5 halation
    val ghost: Float = 0f,         // 0..1 ghosting shoegaze
    val ghostAngle: Float = 25f,   // direção do ghosting em graus
    val crush: Float = 0f,         // 0..1 pretos esmagados
    val fade: Float = 0f,          // 0..1 pretos lavados
    val saturation: Float = 1f,    // 0..1.6
    val colorCast: Float = 0f,     // 0..1 shift ciano/magenta de CCD
    val grain: Float = 0f,         // 0..1
    val vignette: Float = 0f,      // 0..1
    val poster: Float = 0f,        // 0..1 posterização + dither
    val blockiness: Float = 0f,    // 0..1 blocos de chroma fake
    val lut: String = Luts.NONE,   // id da LUT 3D de câmera (Luts.ALL)
    val lutMix: Float = 1f,        // 0..1 intensidade da LUT
    val seed: Float = 7f,
    // export
    val jpegQuality: Int = 92,     // qualidade da compressão real
    val jpegPasses: Int = 1,       // quantas gerações de recompressão
    val outputLongEdge: Int = 2048,// resolução de saída (1600 = digicam raiz)
    val timestamp: Boolean = false,
    val timestampYearOffset: Int = 0, // ex.: -23 => "modo 2003"
) {
    companion object {
        val NEUTRAL = EffectParams()
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Interpola do neutro até este preset — é o slider mestre de intensidade.
 * Os campos discretos (jpeg, resolução, timestamp) só entram com t > 0.
 */
fun EffectParams.scaledBy(t: Float): EffectParams {
    val n = EffectParams.NEUTRAL
    return copy(
        softness = lerp(n.softness, softness, t),
        ca = lerp(n.ca, ca, t),
        bloom = lerp(n.bloom, bloom, t),
        ghost = lerp(n.ghost, ghost, t),
        crush = lerp(n.crush, crush, t),
        fade = lerp(n.fade, fade, t),
        saturation = lerp(n.saturation, saturation, t),
        colorCast = lerp(n.colorCast, colorCast, t),
        grain = lerp(n.grain, grain, t),
        vignette = lerp(n.vignette, vignette, t),
        poster = lerp(n.poster, poster, t),
        blockiness = lerp(n.blockiness, blockiness, t),
        // a LUT some junto com o resto quando a intensidade cai
        lutMix = lerp(0f, lutMix, t),
        jpegQuality = lerp(n.jpegQuality.toFloat(), jpegQuality.toFloat(), t).toInt(),
    )
}
