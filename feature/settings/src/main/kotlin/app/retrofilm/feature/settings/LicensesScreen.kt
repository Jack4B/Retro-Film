package app.retrofilm.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Łączy twarde łamania wierszy w akapitach (puste wiersze zostają), żeby tekst zawijał się do ekranu. */
internal fun reflowLicense(text: String): String = text
    .replace("\r\n", "\n")
    .split(Regex("\n\\s*\n"))
    .joinToString("\n\n") { para -> para.lines().joinToString(" ") { it.trim() }.trim() }

/** Wczytuje tekst licencji z assets (pliki dołączone przez core:ui). */
@Composable
fun rememberLicenseText(license: ThirdPartyLicense): String {
    val context = LocalContext.current
    return remember(license) {
        runCatching {
            reflowLicense(context.assets.open(license.assetPath).bufferedReader().use { it.readText() })
        }.getOrDefault("Nie udało się wczytać tekstu licencji.")
    }
}

/**
 * Ekran „Licencje i autorzy” (trasa [SettingsRoutes.LICENSES]). Bezstanowy: [selected] wskazuje
 * licencję, której pełny tekst jest rozwinięty; dotknięcie pozycji wywołuje [onSelect].
 */
@Composable
fun LicensesScreen(
    selected: ThirdPartyLicense?,
    onBack: () -> Unit,
    onSelect: (ThirdPartyLicense) -> Unit,
    modifier: Modifier = Modifier,
    licenseText: @Composable (ThirdPartyLicense) -> String = { rememberLicenseText(it) }
) {
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar("Licencje i autorzy", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            items(ThirdPartyLicense.entries, key = { it.name }) { lic ->
                NavRow(
                    title = lic.title,
                    subtitle = "${lic.author} · ${lic.license}",
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    onClick = { onSelect(lic) },
                    clickLabel = if (lic == selected) "zwiń tekst licencji" else "pokaż tekst licencji"
                )
                if (lic == selected) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = licenseText(lic),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
