package app.retrofilm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.retrofilm.core.ui.RetroFilmTheme
import app.retrofilm.core.ui.photo.LocalPhotoRenderer
import app.retrofilm.engine.AppGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(this)
        setContent {
            CompositionLocalProvider(LocalPhotoRenderer provides graph.photoRenderer) {
                RetroFilmTheme {
                    PlaceholderScreen()
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "Retro-Film")
        }
    }
}
