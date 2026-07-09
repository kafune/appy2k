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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kafune.appy2k.effects.EffectParams
import com.kafune.appy2k.effects.PRESETS
import com.kafune.appy2k.effects.buildY2kShader
import com.kafune.appy2k.effects.applyParams
import com.kafune.appy2k.effects.scaledBy
import com.kafune.appy2k.export.Exporter
import com.kafune.appy2k.util.loadBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kafune.appy2k.effects.TimestampRenderer

/** Resolução do preview em tela (o export decodifica de novo em alta). */
private const val PREVIEW_LONG_EDGE = 1600

@Composable
fun EditorScreen(
    source: Uri,
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

    var presetIndex by rememberSaveable { mutableIntStateOf(1) }
    val preset = PRESETS[presetIndex]
    var intensity by remember(presetIndex) { mutableFloatStateOf(1f) }
    var timestampOn by remember(presetIndex) { mutableStateOf(preset.params.timestamp) }
    var showOriginal by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val shader = remember { buildY2kShader() }
    val params: EffectParams = preset.params.scaledBy(intensity)
    val effectActive = presetIndex != 0 && intensity > 0.001f && !showOriginal

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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "timestamp 📟",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(checked = timestampOn, onCheckedChange = { timestampOn = it })
            }
        }
    }
}
