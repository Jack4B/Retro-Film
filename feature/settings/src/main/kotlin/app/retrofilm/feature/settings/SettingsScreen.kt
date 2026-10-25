package app.retrofilm.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.core.ui.ThemeMode

private val MinTouch = 48.dp

/**
 * Ekran Ustawienia (trasa [SettingsRoutes.SETTINGS]). Bezstanowy: stan w [state], akcje przez callbacki.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenLicense: (ThirdPartyLicense) -> Unit,
    modifier: Modifier = Modifier,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(stringResource(R.string.settings_title), onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            state.themeMode?.let { mode -> item { ThemeModeRow(mode, onThemeModeChange) } }
            if (state.dynamicColorSupported) {
                item { DynamicColorRow(state.dynamicColor, onDynamicColorChange) }
            }
            item { SectionHeader(stringResource(R.string.settings_section_filters)) }
            item {
                NavRow(
                    title = stringResource(R.string.settings_filters),
                    subtitle = stringResource(R.string.settings_filters_subtitle),
                    icon = { Icon(Icons.Outlined.FilterVintage, contentDescription = null) },
                    onClick = onOpenFilters
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_licenses)) }
            items(ThirdPartyLicense.entries, key = { it.name }) { lic ->
                NavRow(
                    title = stringResource(lic.title),
                    subtitle = "${lic.author} · ${lic.license}",
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    onClick = { onOpenLicense(lic) },
                    clickLabel = stringResource(R.string.settings_show_license)
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_privacy)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_note)) },
                    leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    headlineContent = { Text(stringResource(R.string.settings_version)) },
                    supportingContent = { Text(state.versionName) },
                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}

@Composable
private fun ThemeModeRow(selected: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.System to R.string.settings_theme_system,
        ThemeMode.Dark to R.string.settings_theme_dark,
        ThemeMode.Light to R.string.settings_theme_light
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.heightIn(min = MinTouch)
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun DynamicColorRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val toggleState = stringResource(if (checked) R.string.settings_on else R.string.settings_off)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { stateDescription = toggleState },
        headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
        supportingContent = { Text(stringResource(R.string.settings_dynamic_color_subtitle)) },
        leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
internal fun NavRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    clickLabel: String? = null
) {
    val label = clickLabel ?: stringResource(R.string.settings_open)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon,
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}
