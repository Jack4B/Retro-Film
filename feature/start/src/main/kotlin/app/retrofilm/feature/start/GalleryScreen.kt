package app.retrofilm.feature.start

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.core.ui.photo.Photo

@Immutable
data class GalleryPhoto(val id: String, val label: String, val seed: Int)

@Immutable
data class GalleryUiState(val photos: List<GalleryPhoto>, val selectedIds: Set<String> = emptySet()) {
    val selectedCount get() = selectedIds.size
}

/** Ekran Galeria: wybór jednego lub wielu zdjęć. Bezstanowy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: GalleryUiState,
    onToggle: (String) -> Unit,
    onClearSelection: () -> Unit,
    onOpenSystemPicker: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selectedCount == 0) {
                            stringResource(R.string.gallery_title)
                        } else {
                            stringResource(R.string.gallery_selected_title, state.selectedCount)
                        },
                        maxLines = 1,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    if (state.selectedCount > 0) {
                        val clearCd = stringResource(R.string.gallery_clear_cd)
                        TextButton(
                            onClick = onClearSelection,
                            modifier = Modifier.minimumInteractiveComponentSize().semantics {
                                contentDescription =
                                    clearCd
                            }
                        ) { Text(stringResource(R.string.gallery_clear)) }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onContinue,
                        enabled = state.selectedCount > 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            when (state.selectedCount) {
                                0 -> stringResource(R.string.gallery_pick_photos)
                                1 -> stringResource(R.string.gallery_edit_photo)
                                else -> stringResource(R.string.gallery_edit_series, state.selectedCount)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(onClick = onOpenSystemPicker, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.gallery_open_system_picker))
                    }
                }
            }
        }
    ) { padding ->
        if (state.photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gallery_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 4.dp,
                end = 4.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom =
                    padding.calculateBottomPadding() + 4.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.photos, key = { it.id }) { photo ->
                val selected = photo.id in state.selectedIds
                val order = if (selected) state.selectedIds.indexOf(photo.id) + 1 else 0
                val photoCd = stringResource(R.string.gallery_photo_cd, photo.label)
                val photoState = if (selected) {
                    stringResource(R.string.gallery_photo_selected, order)
                } else {
                    stringResource(R.string.gallery_photo_not_selected)
                }
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(if (selected) 16.dp else 6.dp))
                        .then(
                            if (selected) {
                                Modifier.border(
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                    RoundedCornerShape(16.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle(photo.id) })
                        .semantics {
                            contentDescription = photoCd
                            stateDescription = photoState
                        }
                ) {
                    Photo(contentDescription = null, key = photo.seed, modifier = Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape)) {
                        if (selected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
