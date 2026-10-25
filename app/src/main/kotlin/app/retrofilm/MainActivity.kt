package app.retrofilm

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.retrofilm.core.ui.RetroFilmTheme
import app.retrofilm.core.ui.ThemeMode
import app.retrofilm.core.ui.photo.LocalPhotoRenderer
import app.retrofilm.engine.AppGraph
import app.retrofilm.ui.MainScreen
import app.retrofilm.ui.MainState
import app.retrofilm.ui.RetroFilmNavHost
import app.retrofilm.ui.Route

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(this)
        val typeface = signatureTypeface()
        setContent {
            CompositionLocalProvider(LocalPhotoRenderer provides graph.photoRenderer) {
                val dynamicColor by graph.appSettings.dynamicColor.collectAsState(initial = false)
                val themeMode by graph.appSettings.themeMode.collectAsState(initial = ThemeMode.Dark)
                val dark = themeMode.isDark(isSystemInDarkTheme())
                SideEffect { applySystemBars(dark) }
                RetroFilmTheme(dynamicColor = dynamicColor, darkTheme = dark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val state = remember { MainState(graph, typeface) }
                        RetroFilmNavHost(graph, dynamicColor, themeMode) { navigate ->
                            MainScreen(state, onSettings = { navigate(Route.Settings) })
                        }
                    }
                }
            }
        }
    }

    /** Pasek stanu i nawigacji przezroczyste; ikony ciemne w jasnym motywie, jasne w ciemnym. */
    private fun applySystemBars(dark: Boolean) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** Caveat z core:ui – ten sam krój co podpis w podglądzie ramki. */
    private fun signatureTypeface(): Typeface = resources.getFont(app.retrofilm.core.ui.R.font.caveat)
}
