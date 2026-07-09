package com.kafune.appy2k.effects

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.util.Calendar

/**
 * Timestamp laranja de digicam, desenhado com segmentos (display 7-seg),
 * sem depender de fonte. Formato: "DD MM 'AA" no canto inferior direito,
 * com leve itálico e glow de LED.
 */
object TimestampRenderer {

    private const val ORANGE = 0xFFFF9C2E.toInt()

    // Segmentos: bit 0..6 = A(topo), B(dir-sup), C(dir-inf), D(base),
    //            E(esq-inf), F(esq-sup), G(meio)
    private val DIGIT_SEGMENTS = intArrayOf(
        0b0111111, // 0
        0b0000110, // 1
        0b1011011, // 2
        0b1001111, // 3
        0b1100110, // 4
        0b1101101, // 5
        0b1111101, // 6
        0b0000111, // 7
        0b1111111, // 8
        0b1101111, // 9
    )

    fun draw(canvas: Canvas, width: Int, height: Int, timeMillis: Long = System.currentTimeMillis()) {
        val cal = Calendar.getInstance().apply { setTimeInMillis(timeMillis) }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR) % 100
        val text = "%02d %02d '%02d".format(day, month, year)

        val digitH = minOf(width, height) * 0.055f
        val digitW = digitH * 0.62f
        val advance = digitW * 1.35f
        val margin = minOf(width, height) * 0.06f

        val totalW = text.length * advance
        var x = width - margin - totalW
        val y = height - margin - digitH

        // passe 1: glow, passe 2: segmentos nítidos
        val glow = newPaint().apply {
            maskFilter = BlurMaskFilter(digitH * 0.22f, BlurMaskFilter.Blur.NORMAL)
            alpha = 165
        }
        val solid = newPaint().apply { alpha = 235 }

        for (paint in listOf(glow, solid)) {
            var cx = x
            for (ch in text) {
                when {
                    ch.isDigit() -> drawDigit(canvas, ch - '0', cx, y, digitW, digitH, paint)
                    ch == '\'' -> canvas.drawRect(
                        cx + digitW * 0.3f, y - digitH * 0.18f,
                        cx + digitW * 0.52f, y + digitH * 0.18f, paint
                    )
                    // espaço: só avança
                }
                cx += advance
            }
        }
    }

    private fun newPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ORANGE
        style = Paint.Style.FILL
    }

    private fun drawDigit(
        canvas: Canvas, digit: Int,
        x: Float, y: Float, w: Float, h: Float, paint: Paint,
    ) {
        val segs = DIGIT_SEGMENTS[digit]
        val t = h * 0.16f       // espessura do segmento
        val skew = h * 0.08f    // itálico leve
        val half = h / 2f

        fun seg(bit: Int, horizontal: Boolean, sx: Float, sy: Float, len: Float) {
            if (segs and (1 shl bit) == 0) return
            val path = Path()
            // desloca em x conforme a altura, pro itálico
            fun px(px0: Float, py0: Float) = px0 + skew * (1f - (py0 - y) / h)
            if (horizontal) {
                path.moveTo(px(sx + t * 0.5f, sy), sy)
                path.lineTo(px(sx + len - t * 0.5f, sy), sy)
                path.lineTo(px(sx + len - t, sy + t * 0.5f), sy + t * 0.5f)
                path.lineTo(px(sx + t, sy + t * 0.5f), sy + t * 0.5f)
            } else {
                path.moveTo(px(sx, sy + t * 0.5f), sy + t * 0.5f)
                path.lineTo(px(sx + t * 0.5f, sy + t), sy + t)
                path.lineTo(px(sx + t * 0.5f, sy + len - t), sy + len - t)
                path.lineTo(px(sx, sy + len - t * 0.5f), sy + len - t * 0.5f)
                path.lineTo(px(sx - t * 0.5f, sy + len - t), sy + len - t)
                path.lineTo(px(sx - t * 0.5f, sy + t), sy + t)
            }
            path.close()
            canvas.drawPath(path, paint)
        }

        seg(0, true, x, y, w)                        // A topo
        seg(6, true, x, y + half - t * 0.5f, w)      // G meio
        seg(3, true, x, y + h - t * 0.5f, w)         // D base
        seg(5, false, x, y, half)                    // F esq-sup
        seg(4, false, x, y + half, half)             // E esq-inf
        seg(1, false, x + w, y, half)                // B dir-sup
        seg(2, false, x + w, y + half, half)         // C dir-inf
    }
}
