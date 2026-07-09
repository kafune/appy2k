package com.kafune.appy2k.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kafune.appy2k.effects.PRESETS
import com.kafune.appy2k.effects.applyParams
import com.kafune.appy2k.effects.buildY2kShader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun CameraScreen(
    onCaptured: (Uri, Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // flash ligado por padrão: flash estourado É a estética
    var flashOn by remember { mutableStateOf(true) }
    var presetIndex by remember { mutableIntStateOf(1) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    LaunchedEffect(flashOn) {
        imageCapture.flashMode =
            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    // filtro em tempo real no preview: RenderEffect direto na view,
    // re-aplicado num loop curto pra animar o grain (seed muda a cada tick)
    LaunchedEffect(presetIndex, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        if (presetIndex == 0) {
            pv.setRenderEffect(null)
            return@LaunchedEffect
        }
        val shader = buildY2kShader()
        var seed = 0f
        while (isActive) {
            if (pv.width > 0 && pv.height > 0) {
                val params = PRESETS[presetIndex].params.copy(seed = seed)
                shader.applyParams(params, pv.width.toFloat(), pv.height.toFloat())
                pv.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "src"))
                seed = (seed + 1f) % 1000f
            }
            delay(90)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        // COMPATIBLE = TextureView: RenderEffect não pega em
                        // SurfaceView (o modo PERFORMANCE padrão)
                        pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(pv.surfaceProvider)
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView = pv
                    }
                },
            )
        } else if (permissionDenied) {
            Text(
                "preciso da câmera pra isso funcionar :(\nlibera nas configurações do app",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) {
                Text("← voltar", fontFamily = FontFamily.Monospace, color = Color.White)
            }
            TextButton(onClick = { flashOn = !flashOn }) {
                Text(
                    if (flashOn) "⚡ flash ON" else "flash off",
                    fontFamily = FontFamily.Monospace,
                    color = if (flashOn) MaterialTheme.colorScheme.tertiary else Color.Gray,
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            // presets ao vivo — a foto capturada abre no editor já com ele
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                items(PRESETS.size) { index ->
                    FilterChip(
                        selected = index == presetIndex,
                        onClick = { presetIndex = index },
                        label = {
                            Text(
                                PRESETS[index].name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            labelColor = Color.White,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            // botão de captura
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 40.dp)
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .background(Color.White, CircleShape)
                    .clickable {
                        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                    onCaptured(Uri.fromFile(file), presetIndex)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            },
                        )
                    },
            )
        }

        Text(
            "REC ●",
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 16.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color.Red,
        )
    }
}
