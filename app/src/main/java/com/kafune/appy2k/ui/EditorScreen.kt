package com.kafune.appy2k.ui

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kafune.appy2k.effects.EffectParams
import com.kafune.appy2k.effects.Luts
import com.kafune.appy2k.effects.PRESETS
import com.kafune.appy2k.effects.TimestampRenderer
import com.kafune.appy2k.effects.applyParams
import com.kafune.appy2k.effects.buildY2kShader
import com.kafune.appy2k.effects.scaledBy
import com.kafune.appy2k.export.Exporter
import com.kafune.appy2k.util.loadBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolução do preview em tela (o export decodifica de novo em alta). */
private const val PREVIEW_LONG_EDGE = 1600

/** Slider de ajuste fino: rótulo, faixa, e como ler/escrever no EffectParams. */
private class SliderSpec(
    val label: String,
    val max: Float,
    val get: (EffectParams) -> Float,
    val set: (EffectParams, Float) -> EffectParams,
)

private val FINE_SLIDERS = listOf(
    SliderSpec("suavidade", 1f, { it.softness }, { p, v -> p.copy(softness = v) }),
    SliderSpec("aberração", 1f, { it.ca }, { p, v -> p.copy(ca = v) }),
    SliderSpec("bloom", 1.5f, { it.bloom }, { p, v -> p.copy(bloom = v) }),
    SliderSpec("ghosting", 1f, { it.ghost }, { p, v -> p.copy(ghost = v) }),
    SliderSpec("preto esmagado", 1f, { it.crush }, { p, v -> p.copy(crush = v) }),
    SliderSpec("fade", 1f, { it.fade }, { p, v -> p.copy(fade = v) }),
    SliderSpec("saturação", 1.6f, { it.saturation }, { p, v -> p.copy(saturation = v) }),
    SliderSpec("cast CCD", 1f, { it.colorCast }, { p, v -> p.copy(colorCast = v) }),
    SliderSpec("grain", 1f, { it.grain }, { p, v -> p.copy(grain = v) }),
    SliderSpec("vinheta", 1f, { it.vignette }, { p, v -> p.copy(vignette = v) }),
    SliderSpec("dither", 1f, { it.poster }, { p, v -> p.copy(poster = v) }),
    SliderSpec("jpeg fake", 1f, { it.blockiness }, { p, v -> p.copy(blockiness = v) }),
    SliderSpec("lut mix", 1f, { it.lutMix }, { p, v -> p.copy(lutMix = v) }),
)

/** Eras do timestamp: rótulo + offset de ano aplicado à data de hoje. */
private val TIMESTAMP_ERAS = listOf("hoje" to 0, "’03" to -23, "’99" to -27)

@Composable
fun EditorScreen(
    source: Uri,
    initialPreset: Int = 1,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source) {
        bitmap = withContext(Dispatchers.IO) {
            loadBitmap(context, source, PREVIEW_LONG_EDGE)
        }
    }

    var presetIndex by rememberSaveable {
        mutableIntStateOf(initialPreset.coerceIn(0, PRESETS.size - 1))
    }
    val preset = PRESETS[presetIndex]
    var intensity by remember(presetIndex) { mutableFloatStateOf(1f) }
    // base = preset × intensidade; os ajustes finos editam a partir daí
    // (trocar de preset ou mexer no "vibe" re-deriva e descarta os ajustes)
    var params by remember(presetIndex, intensity) {
        mutableStateOf(preset.params.scaledBy(intensity))
    }
    var timestampOn by remember(presetIndex) { mutableStateOf(preset.params.timestamp) }
    var eraOffset by remember(presetIndex) {
        mutableIntStateOf(preset.params.timestampYearOffset)
    }
    var showAdjustments by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val shader = remember { buildY2kShader() }
    val effectActive = presetIndex != 0 && !showOriginal

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // barra do topo
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("← voltar", fontFamily = FontFamily.Monospace)
            }
            TextButton(
                enabled = !saving && bitmap != null,
                onClick = {
                    saving = true
                    val exportParams = params.copy(
                        timestamp = timestampOn,
                        timestampYearOffset = eraOffset,
                        jpegPasses = if (intensity > 0.001f) preset.params.jpegPasses else 0,
                    )
                    scope.launch {
                        try {
                            Exporter.export(context, source, exportParams)
                            Toast.makeText(context, "salvo na galeria ✧", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "deu ruim: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(
                    if (saving) "salvando…" else "salvar ✓",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // preview — segurar na imagem mostra a original
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    showOriginal = true
                                    tryAwaitRelease()
                                    showOriginal = false
                                }
                            )
                        },
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                renderEffect = if (effectActive) {
                                    shader.applyParams(params, size.width, size.height)
                                    RenderEffect
                                        .createRuntimeShaderEffect(shader, "src")
                                        .asComposeRenderEffect()
                                } else {
                                    null
                                }
                            },
                    )
                    if (timestampOn && !showOriginal) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawIntoCanvas { canvas ->
                                TimestampRenderer.draw(
                                    canvas.nativeCanvas,
                                    size.width.toInt(),
                                    size.height.toInt(),
                                    yearOffset = eraOffset,
                                )
                            }
                        }
                    }
                }
            }
        }

        // presets
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(PRESETS.size) { index ->
                val p = PRESETS[index]
                FilterChip(
                    selected = index == presetIndex,
                    onClick = { presetIndex = index },
                    label = {
                        Text(p.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        // controles
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "vibe",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    enabled = presetIndex != 0,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "timestamp 📟",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (timestampOn) {
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TIMESTAMP_ERAS.forEach { (label, offset) ->
                                FilterChip(
                                    selected = eraOffset == offset,
                                    onClick = { eraOffset = offset },
                                    label = {
                                        Text(
                                            label,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Switch(checked = timestampOn, onCheckedChange = { timestampOn = it })
            }

            TextButton(
                onClick = { showAdjustments = !showAdjustments },
                enabled = presetIndex != 0,
            ) {
                Text(
                    if (showAdjustments) "ajustes finos ▴" else "ajustes finos ▾",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }

            if (showAdjustments && presetIndex != 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // câmera (LUT 3D)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Text(
                            "câmera",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(Luts.ALL.size) { i ->
                                val lut = Luts.ALL[i]
                                FilterChip(
                                    selected = params.lut == lut.id,
                                    onClick = { params = params.copy(lut = lut.id) },
                                    label = {
                                        Text(
                                            lut.name,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    FINE_SLIDERS.forEach { spec ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                spec.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(0.32f),
                            )
                            Slider(
                                value = spec.get(params).coerceIn(0f, spec.max),
                                onValueChange = { params = spec.set(params, it) },
                                valueRange = 0f..spec.max,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
