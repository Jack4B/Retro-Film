package app.retrofilm

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.retrofilm.core.ui.RetroFilmTheme
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
                RetroFilmTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val state = remember { MainState(graph, typeface) }
                        RetroFilmNavHost { navigate -> MainScreen(state, onSettings = { navigate(Route.Settings) }) }
                    }
                }
            }
        }
    }

    /** Caveat z core:ui – ten sam krój co podpis w podglądzie ramki. */
    private fun signatureTypeface(): Typeface = resources.getFont(app.retrofilm.core.ui.R.font.caveat)
}
