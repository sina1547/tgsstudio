package ir.sina.tgsstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.sina.tgsstudio.data.AppPrefs
import ir.sina.tgsstudio.ui.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPrefs(this)
        setContent {
            var theme by remember { mutableStateOf(prefs.themeSetting) }
            var accent by remember { mutableStateOf(prefs.themeAccent) }
            StudioTheme(theme, accent) {
                StudioApp(
                    onThemeChange = { prefs.themeSetting = it; theme = it },
                    onAccentChange = { prefs.themeAccent = it; accent = it },
                )
            }
        }
    }
}
