package app.retrofilm.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar("Ustawienia", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SectionHeader("Wygląd") }
            if (state.dynamicColorSupported) {
                item { DynamicColorRow(state.dynamicColor, onDynamicColorChange) }
            }
            item { SectionHeader("Filtry") }
            item {
                NavRow(
                    title = "Filtry",
                    subtitle = "Pobieranie i usuwanie filtrów filmowych",
                    icon = { Icon(Icons.Outlined.FilterVintage, contentDescription = null) },
                    onClick = onOpenFilters
                )
            }
            item { SectionHeader("Licencje i autorzy") }
            items(ThirdPartyLicense.entries, key = { it.name }) { lic ->
                NavRow(
                    title = lic.title,
                    subtitle = "${lic.author} · ${lic.license}",
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    onClick = { onOpenLicense(lic) },
                    clickLabel = "pokaż tekst licencji"
                )
            }
            item { SectionHeader("Prywatność") }
            item {
                ListItem(
                    headlineContent = { Text(PRIVACY_NOTE) },
                    leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    headlineContent = { Text("Wersja aplikacji") },
                    supportingContent = { Text(state.versionName) },
                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}

@Composable
private fun DynamicColorRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text("Dynamiczne kolory (Material You)") },
        supportingContent = { Text("Kolory aplikacji dopasowane do tapety telefonu") },
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
    clickLabel: String = "otwórz"
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick),
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}
