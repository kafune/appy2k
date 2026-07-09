package com.kafune.appy2k.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kafune.appy2k.ui.theme.CyanY2k
import com.kafune.appy2k.ui.theme.Ink
import com.kafune.appy2k.ui.theme.PinkY2k

@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onPickPhoto: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickPhoto(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0F2E), Ink, Color(0xFF0F1A24))
                )
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "appy2k",
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = CyanY2k,
        )
        Text(
            text = "✧ y2k • shoegaze • emo ✧",
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = PinkY2k,
        )
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text("abrir foto", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onOpenCamera,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PinkY2k),
        ) {
            Text("câmera ⚡", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        }
        Spacer(Modifier.height(48.dp))
        Text(
            text = "sua digicam de 2003 mora aqui",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
