package com.kafune.appy2k

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kafune.appy2k.ui.CameraScreen
import com.kafune.appy2k.ui.EditorScreen
import com.kafune.appy2k.ui.HomeScreen
import com.kafune.appy2k.ui.theme.Appy2kTheme

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    data class Editor(val source: Uri) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Appy2kTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            onOpenCamera = { screen = Screen.Camera },
            onPickPhoto = { uri -> screen = Screen.Editor(uri) },
        )
        is Screen.Camera -> CameraScreen(
            onCaptured = { uri -> screen = Screen.Editor(uri) },
            onBack = { screen = Screen.Home },
        )
        is Screen.Editor -> EditorScreen(
            source = s.source,
            onBack = { screen = Screen.Home },
        )
    }
}
