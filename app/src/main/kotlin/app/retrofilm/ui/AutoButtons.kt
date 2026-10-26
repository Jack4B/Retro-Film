package app.retrofilm.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.AutoScene
import kotlinx.coroutines.launch

private val SidePad = 16.dp
private val MinTouch = 48.dp

@Composable
internal fun PrepButton(state: MainState) {
    val scope = rememberCoroutineScope()
    val undo = state.canUndoPrep
    OutlinedButton(
        onClick = { scope.launch { state.togglePrep() } },
        modifier = Modifier.padding(horizontal = SidePad).fillMaxWidth().heightIn(min = MinTouch)
    ) {
        Text(stringResource(if (undo) R.string.adjust_prep_undo else R.string.adjust_prep))
    }
}

@Composable
internal fun AutoButton(state: MainState) {
    val scope = rememberCoroutineScope()
    val undo = state.canUndoAuto
    OutlinedButton(
        onClick = { scope.launch { state.toggleAuto() } },
        modifier = Modifier.padding(horizontal = SidePad).fillMaxWidth().heightIn(min = MinTouch)
    ) {
        val scene = stringResource(
            when (state.autoScene) {
                AutoScene.Portrait -> R.string.scene_portrait
                AutoScene.Landscape -> R.string.scene_landscape
                AutoScene.Dominant -> R.string.scene_dominant
                AutoScene.General -> R.string.scene_general
            }
        )
        Text(if (undo) stringResource(R.string.adjust_auto_undo, scene) else stringResource(R.string.adjust_auto))
    }
}
