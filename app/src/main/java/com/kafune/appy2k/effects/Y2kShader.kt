package com.kafune.appy2k.effects

import android.graphics.RuntimeShader
import org.intellij.lang.annotations.Language

/**
 * Über-shader AGSL com toda a estética digicam/y2k em um passe só.
 *
 * Todos os tamanhos em pixel são multiplicados pelo uniform `px`
 * (escala relativa a uma imagem de referência de 1200px no menor lado),
 * pra que o preview em baixa resolução e o export em alta fiquem idênticos.
 *
 * Efeitos, na ordem do pipeline:
 *  1. softness   — blur de digicam (downscale/upscale fake)
 *  2. ca         — aberração cromática radial (R/B deslocados)
 *  3. ghost      — ghosting/double exposure (vibe shoegaze)
 *  4. bloom      — halation quente nos highlights (flash estourado)
 *  5. blockiness — blocos de chroma 8x8 (artefato JPEG fake pro preview;
 *                  o export ainda passa por compressão JPEG real)
 *  6. grade CCD  — crush de preto, fade, cast ciano/magenta, saturação
 *  7. vignette
 *  8. poster     — posterização + ordered dither (banding de Orkut)
 *  9. grain      — ruído de sensor pequeno, mais forte nas sombras
 */
@Language("AGSL")
const val Y2K_SHADER_SRC = """
uniform shader src;
uniform float2 res;
uniform float px;
uniform float softness;
uniform float ca;
uniform float bloom;
uniform float ghost;
uniform float2 ghostDir;
uniform float crush;
uniform float fade;
uniform float saturation;
uniform float colorCast;
uniform float grain;
uniform float vignette;
uniform float poster;
uniform float blockiness;
uniform float seed;

const float3 LUMA = float3(0.299, 0.587, 0.114);

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Amostra com aberração cromática radial.
float3 fetch(float2 uv) {
    float2 d = (uv - res * 0.5) / max(res.x, res.y);
    float2 off = d * ca * 7.0 * px;
    float r = src.eval(uv + off).r;
    float g = src.eval(uv).g;
    float b = src.eval(uv - off).b;
    return float3(r, g, b);
}

// Blur em cruz barato = softness de digicam.
float3 sampleSoft(float2 uv) {
    float3 c = fetch(uv);
    float r = softness * 2.8 * px;
    float3 n = fetch(uv + float2(r, 0.0)) + fetch(uv - float2(r, 0.0))
             + fetch(uv + float2(0.0, r)) + fetch(uv - float2(0.0, r));
    return mix(c, (c * 2.0 + n) / 6.0, clamp(softness, 0.0, 1.0));
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord;
    float3 c = sampleSoft(uv);

    // --- ghosting / motion trail (shoegaze) ---
    if (ghost > 0.001) {
        float3 g1 = fetch(uv + ghostDir * px);
        float3 g2 = fetch(uv + ghostDir * px * 1.7);
        float3 gs = (g1 + g2) * 0.5;
        c = mix(c, max(c, gs), ghost * 0.6);
    }

    // --- bloom / halation (flash estourado) ---
    if (bloom > 0.001) {
        float3 acc = float3(0.0);
        for (int i = 0; i < 6; i++) {
            float a = 1.0471976 * float(i);
            float2 o1 = float2(cos(a), sin(a)) * 6.0 * px;
            float2 o2 = float2(cos(a + 0.5236), sin(a + 0.5236)) * 14.0 * px;
            float3 s1 = src.eval(uv + o1).rgb;
            float3 s2 = src.eval(uv + o2).rgb;
            acc += s1 * smoothstep(0.55, 0.95, dot(s1, LUMA));
            acc += s2 * smoothstep(0.55, 0.95, dot(s2, LUMA)) * 0.7;
        }
        acc /= 10.2;
        // halation levemente quente
        c += acc * bloom * float3(1.08, 0.97, 0.86);
    }

    // --- blocos de chroma (JPEG fake) ---
    if (blockiness > 0.001) {
        float bs = 8.0 * px;
        float2 buv = (floor(uv / bs) + 0.5) * bs;
        float3 bc = src.eval(buv).rgb;
        float yl = dot(c, LUMA);
        float3 bChroma = bc - dot(bc, LUMA);
        c = mix(c, clamp(float3(yl) + bChroma, 0.0, 1.0), blockiness * 0.7);
    }

    // --- color science de CCD ---
    float l0 = dot(c, LUMA);
    // pretos esmagados
    float k = crush * 0.09;
    c = clamp((c - k) / (1.0 - k), 0.0, 1.0);
    // fade / pretos lavados
    c = mix(c, c * 0.86 + float3(0.10, 0.11, 0.13), fade);
    // cast: sombras pra ciano/verde, highlights pra magenta/quente
    float sh = 1.0 - smoothstep(0.0, 0.55, l0);
    float hi = smoothstep(0.55, 1.0, l0);
    c += colorCast * (sh * float3(-0.045, 0.02, 0.055) + hi * float3(0.05, -0.015, 0.035));
    // saturação estranha (pode passar de 1.0)
    float l1 = dot(c, LUMA);
    c = mix(float3(l1), c, saturation);

    // --- vinheta ---
    float2 vd = (uv - res * 0.5) / (0.5 * max(res.x, res.y));
    c *= 1.0 - vignette * 0.45 * smoothstep(0.35, 1.3, dot(vd, vd));

    // --- posterização + ordered dither (banding de Orkut) ---
    if (poster > 0.001) {
        float levels = mix(48.0, 10.0, poster);
        float2 bp = mod(floor(fragCoord / max(px, 1.0)), 2.0);
        float bay = mix(mix(0.0, 2.0, bp.x), mix(3.0, 1.0, bp.x), bp.y) / 4.0 - 0.375;
        c = floor(clamp(c, 0.0, 1.0) * levels + 0.5 + bay * poster) / levels;
    }

    // --- grain de sensor pequeno ---
    float gn = hash(fragCoord + float2(seed * 91.7, seed * 57.3)) - 0.5;
    c += gn * grain * (0.05 + 0.12 * (1.0 - dot(clamp(c, 0.0, 1.0), LUMA)));

    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/** Menor lado de referência: params px são calibrados pra uma imagem 1200px. */
const val REFERENCE_MIN_DIM = 1200f

fun buildY2kShader(): RuntimeShader = RuntimeShader(Y2K_SHADER_SRC)

/** Aplica todos os uniforms de [params] no shader, pra uma superfície w×h. */
fun RuntimeShader.applyParams(params: EffectParams, width: Float, height: Float) {
    val px = minOf(width, height) / REFERENCE_MIN_DIM
    val angleRad = Math.toRadians(params.ghostAngle.toDouble())
    val ghostLen = 16f * params.ghost
    setFloatUniform("res", width, height)
    setFloatUniform("px", px)
    setFloatUniform("softness", params.softness)
    setFloatUniform("ca", params.ca)
    setFloatUniform("bloom", params.bloom)
    setFloatUniform("ghost", params.ghost)
    setFloatUniform(
        "ghostDir",
        (Math.cos(angleRad) * ghostLen).toFloat(),
        (Math.sin(angleRad) * ghostLen).toFloat()
    )
    setFloatUniform("crush", params.crush)
    setFloatUniform("fade", params.fade)
    setFloatUniform("saturation", params.saturation)
    setFloatUniform("colorCast", params.colorCast)
    setFloatUniform("grain", params.grain)
    setFloatUniform("vignette", params.vignette)
    setFloatUniform("poster", params.poster)
    setFloatUniform("blockiness", params.blockiness)
    setFloatUniform("seed", params.seed)
}
