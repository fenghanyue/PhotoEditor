package io.github.fenghanyue.photoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fenghanyue.photoeditor.ui.HomeScreen
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoEditorTheme {
                HomeScreen(versionName = BuildConfig.VERSION_NAME)
            }
        }
    }
}
