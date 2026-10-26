package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.retrofilm.R

private val ChipGap = 8.dp
private val RowPadding = PaddingValues(horizontal = 16.dp)

private fun brandLabel(brand: String): Int = when (brand) {
    "kodak" -> R.string.film_brand_kodak
    "fuji" -> R.string.film_brand_fuji
    "polaroid" -> R.string.film_brand_polaroid
    "ilford" -> R.string.film_brand_ilford
    else -> R.string.film_brand_other
}

/** Dwa rzędy chipów: Ulubione + marki, a pod nimi rodzaje filmów wybranej marki. */
@Suppress("LongParameterList") // stan i zdarzenia filtra przekazywane jawnie; grupowanie w obiekt później
@Composable
fun FilmFilterBar(
    films: List<FilmOption>,
    brand: String,
    type: String?,
    onlyFavorites: Boolean,
    showPresets: Boolean,
    onBrand: (String) -> Unit,
    onType: (String?) -> Unit,
    onFavorites: () -> Unit,
    onPresets: () -> Unit
) {
    val brands = FilmGroups.brandsPresent(films)
    val types = FilmGroups.typesIn(films, brand)
    Column(verticalArrangement = Arrangement.spacedBy(ChipGap)) {
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
            item(key = "presets") {
                FilterChip(
                    selected = showPresets,
                    onClick = onPresets,
                    label = { Text(stringResource(R.string.film_presets)) }
                )
            }
            item(key = "favorites") {
                FilterChip(
                    selected = onlyFavorites && !showPresets,
                    onClick = onFavorites,
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    label = { Text(stringResource(R.string.film_favorites)) }
                )
            }
            items(brands, key = { it }) { b ->
                FilterChip(
                    selected = !onlyFavorites && !showPresets && b == brand,
                    onClick = { onBrand(b) },
                    label = { Text(stringResource(brandLabel(b))) }
                )
            }
        }
        if (!onlyFavorites && !showPresets && types.size > 1) {
            LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                item(key = "all") {
                    FilterChip(
                        selected = type == null,
                        onClick = { onType(null) },
                        label = { Text(stringResource(R.string.film_all)) }
                    )
                }
                items(types, key = { it }) { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { onType(t) },
                        label = { Text(categoryLabel(t)) }
                    )
                }
            }
        }
    }
}
