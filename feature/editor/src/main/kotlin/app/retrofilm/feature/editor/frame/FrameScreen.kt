package app.retrofilm.feature.editor.frame

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.core.ui.photo.Photo
import app.retrofilm.feature.editor.R
import kotlin.math.roundToInt

@Suppress("MagicNumber") // kolory ARGB
enum class FrameBackground(@StringRes val label: Int, val color: Color) {
    White(R.string.frame_bg_white, Color(0xFFFFFFFF)),
    Cream(R.string.frame_bg_cream, Color(0xFFF3EBDD)),
    Black(R.string.frame_bg_black, Color(0xFF111111))
}

@Suppress("MagicNumber") // kolory ARGB
enum class SignatureColor(@StringRes val label: Int, val color: Color) {
    Navy(R.string.sig_color_navy, Color(0xFF1F2A55)),
    Black(R.string.sig_color_black, Color(0xFF111111)),
    Sepia(R.string.sig_color_sepia, Color(0xFF704214)),
    White(R.string.sig_color_white, Color(0xFFFFFFFF))
}

/** Ustawienia wspólne dla całej serii. Ramka domyślnie WYŁĄCZONA. */
@Immutable
data class FrameSettings(
    val enabled: Boolean = false,
    val signatureEnabled: Boolean = false,
    val aspect: FrameAspect = FrameAspect.Square,
    val margin: Float = FrameGeometry.DEFAULT_MARGIN,
    val background: FrameBackground = FrameBackground.White,
    val signatureColor: SignatureColor = SignatureColor.Navy
)

@Immutable
data class SeriesPhoto(val id: String, val seed: Int, val aspect: Float, val signatureOverride: String? = null)

@Immutable
data class FrameUiState(
    val settings: FrameSettings = FrameSettings(),
    /** Podpis wspólny dla serii. */
    val seriesSignature: String = "",
    val photos: List<SeriesPhoto>,
    val currentIndex: Int = 0,
    val previewMatrix: ColorMatrix? = null
) {
    val current get() = photos[currentIndex]
    val isOverride get() = current.signatureOverride != null

    /** Podpis efektywny dla bieżącego zdjęcia. */
    val signature get() = current.signatureOverride ?: seriesSignature
}

const val SIGNATURE_LIMIT = FrameGeometry.SIGNATURE_MAX_CHARS

/** Ekran Ramka i podpis. Bezstanowy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameScreen(
    state: FrameUiState,
    onBack: () -> Unit,
    onSettingsChange: (FrameSettings) -> Unit,
    onSignatureChange: (String) -> Unit,
    onOverrideChange: (Boolean) -> Unit,
    onSelectPhoto: (Int) -> Unit,
    onApplyToSeries: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val series = state.photos.size > 1
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.frame_title),
                        maxLines = 1,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            )
        },
        floatingActionButton = {
            if (series && s.enabled) {
                ExtendedFloatingActionButton(
                    onClick = onApplyToSeries,
                    icon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                    text = { Text(stringResource(R.string.frame_apply_series, state.photos.size)) }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                FramePreview(s, state.signature, state.current.aspect, state.current.seed, state.previewMatrix)
            }
            // Komunikat tuż pod podglądem: podpis za długi nie jest rysowany (patrz RAMKA-SPEC).
            val fitStatus = rememberSignatureFitStatus(s, state.signature, state.current.aspect)
            if (fitStatus == SignatureFitStatus.TooLong) SignatureTooLongBanner()
            if (series) SeriesStrip(state, onSelectPhoto)

            ToggleRow(
                stringResource(R.string.frame_toggle),
                stringResource(if (s.enabled) R.string.frame_on else R.string.frame_off_default),
                s.enabled
            ) {
                onSettingsChange(s.copy(enabled = it))
            }
            val frameAlpha = if (s.enabled) 1f else 0.38f
            Column(Modifier.alpha(frameAlpha), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.frame_aspect), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FrameAspect.entries.forEachIndexed { i, a ->
                        val aspectCd = stringResource(R.string.frame_aspect_cd, a.label)
                        SegmentedButton(
                            selected = s.aspect == a,
                            onClick = { onSettingsChange(s.copy(aspect = a)) },
                            enabled = s.enabled,
                            shape = SegmentedButtonDefaults.itemShape(i, FrameAspect.entries.size),
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = aspectCd
                            }
                        ) { Text(a.label, maxLines = 1) }
                    }
                }
                val pct = (s.margin * 100).roundToInt()
                val thicknessCd = stringResource(R.string.frame_thickness_cd)
                val thicknessState = stringResource(R.string.editor_percent_state, pct)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.frame_thickness),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.editor_percent, pct),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = s.margin * 100,
                    onValueChange = { onSettingsChange(s.copy(margin = it.roundToInt() / 100f)) },
                    valueRange = 0f..15f,
                    steps = 14,
                    enabled = s.enabled,
                    modifier = Modifier.height(48.dp).semantics {
                        contentDescription = thicknessCd
                        stateDescription = thicknessState
                    }
                )
                Text(stringResource(R.string.frame_color), style = MaterialTheme.typography.titleSmall)
                SwatchRow(FrameBackground.entries, s.background, {
                    it.color
                }, { stringResource(it.label) }, s.enabled) {
                    onSettingsChange(s.copy(background = it))
                }
            }

            ToggleRow(
                stringResource(R.string.frame_signature_toggle),
                stringResource(if (s.signatureEnabled) R.string.frame_signature_band else R.string.frame_signature_off),
                s.signatureEnabled,
                enabled = s.enabled
            ) {
                onSettingsChange(s.copy(signatureEnabled = it))
            }
            val sigActive = s.enabled && s.signatureEnabled
            Column(Modifier.alpha(if (sigActive) 1f else 0.38f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SignatureField(state.signature, sigActive, onSignatureChange)
                if (series) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(
                                state.isOverride,
                                enabled = sigActive,
                                role = Role.Checkbox,
                                onValueChange = onOverrideChange
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = state.isOverride, onCheckedChange = null, enabled = sigActive)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.frame_signature_override))
                    }
                }
                Text(stringResource(R.string.frame_signature_color), style = MaterialTheme.typography.titleSmall)
                SwatchRow(SignatureColor.entries, s.signatureColor, {
                    it.color
                }, { stringResource(it.label) }, sigActive) {
                    onSettingsChange(s.copy(signatureColor = it))
                }
            }
        }
    }
}

@Composable
private fun SignatureTooLongBanner() {
    val a11y = stringResource(SIGNATURE_TOO_LONG_A11Y)
    val message = stringResource(SIGNATURE_TOO_LONG_MESSAGE)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = a11y
                liveRegion = LiveRegionMode.Polite
                error(message)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.size(12.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SignatureField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val atLimit = value.length >= SIGNATURE_LIMIT
    val counterCd = pluralStringResource(
        R.plurals.frame_signature_counter_cd,
        SIGNATURE_LIMIT,
        value.length,
        SIGNATURE_LIMIT
    )
    val fieldCd = stringResource(R.string.frame_signature_field_cd)
    val limitError = stringResource(R.string.frame_signature_limit_error, SIGNATURE_LIMIT)
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.replace("\n", " ").take(SIGNATURE_LIMIT)) },
        enabled = enabled,
        singleLine = true,
        isError = atLimit,
        label = { Text(stringResource(R.string.frame_signature_label)) },
        placeholder = { Text(stringResource(R.string.frame_signature_placeholder)) },
        trailingIcon = {
            Text(
                stringResource(R.string.frame_signature_counter, value.length, SIGNATURE_LIMIT),
                color = if (atLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp).semantics {
                    contentDescription = counterCd
                }
            )
        },
        supportingText = {
            if (atLimit) {
                Text(stringResource(R.string.frame_signature_limit, SIGNATURE_LIMIT))
            } else {
                Text(stringResource(R.string.frame_signature_hint))
            }
        },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = fieldCd
            if (atLimit) error(limitError)
        }
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun <T> SwatchRow(
    items: List<T>,
    selected: T,
    color: (T) -> Color,
    label: @Composable (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val isSel = item == selected
            val itemLabel = label(item)
            Box(
                Modifier.size(48.dp)
                    .selectable(isSel, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(item) })
                    .semantics { contentDescription = itemLabel },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(color(item))
                        .border(
                            BorderStroke(
                                if (isSel) 3.dp else 1.dp,
                                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSel) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (color(item).luminanceApprox() >
                                0.5f
                            ) {
                                Color.Black
                            } else {
                                Color.White
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f

private fun Color.luminanceApprox() = LUMA_R * red + LUMA_G * green + LUMA_B * blue

@Composable
private fun SeriesStrip(state: FrameUiState, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(state.photos, key = { _, p -> p.id }) { i, p ->
            val sel = i == state.currentIndex
            val photoCd = if (p.signatureOverride != null) {
                stringResource(R.string.frame_series_photo_override_cd, i + 1, state.photos.size)
            } else {
                stringResource(R.string.frame_series_photo_cd, i + 1, state.photos.size)
            }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    .then(
                        if (sel) {
                            Modifier.border(
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                RoundedCornerShape(10.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .selectable(sel, role = Role.Tab, onClick = { onSelect(i) })
                    .semantics {
                        contentDescription = photoCd
                    }
            ) {
                Photo(null, key = p.seed, modifier = Modifier.fillMaxSize())
                if (p.signatureOverride != null) {
                    Box(
                        Modifier.align(
                            Alignment.TopEnd
                        ).padding(4.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
