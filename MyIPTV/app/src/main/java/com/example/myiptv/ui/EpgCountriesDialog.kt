package com.example.myiptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiptv.data.EpgCategoryOption
import com.example.myiptv.data.EpgCountrySelection
import com.example.myiptv.ui.theme.MyIptvPalette

@Composable
fun EpgCountriesDialog(
    selection: EpgCountrySelection,
    onApply: (Set<String>, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var countries by rememberSaveable(selection.profileId) {
        mutableStateOf(selection.selectedCountries.toList())
    }
    var categories by rememberSaveable(selection.profileId) {
        mutableStateOf(selection.selectedCategories.toList())
    }
    var expandedCountry by rememberSaveable { mutableStateOf<String?>(null) }
    // Applied countries stay first, while draft switches do not move rows under the finger.
    val options = remember(selection) { selection.orderedOptions }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MyIptvPalette.Background, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Pays et catégories EPG", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${options.count { it.code in countries }} pays · " +
                        "${options.sumOf { country ->
                            if (country.code in countries) {
                                country.categories.count { it.key in categories }
                            } else {
                                0
                            }
                        }} catégories actives",
                    color = MyIptvPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = {
                            countries = options.map { it.code }
                            categories = options.flatMap { it.categories }.map { it.key }
                        },
                        modifier = Modifier.tvFocusBorder(MaterialTheme.shapes.small),
                    ) { Text("Tout activer") }
                    TextButton(
                        onClick = { countries = emptyList() },
                        modifier = Modifier.tvFocusBorder(MaterialTheme.shapes.small),
                    ) {
                        Text("Tout désactiver")
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.forEach { country ->
                        val countryEnabled = country.code in countries
                        val enabledCategoryCount = if (countryEnabled) {
                            country.categories.count { it.key in categories }
                        } else {
                            0
                        }
                        item(key = "country:${country.code}") {
                            Surface(
                                color = if (countryEnabled) {
                                    MyIptvPalette.ActiveSurface
                                } else {
                                    MyIptvPalette.Card
                                },
                                border = BorderStroke(1.dp, MyIptvPalette.Border),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(countryLabel(country.code))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "${country.epgChannels} EPG",
                                                color = MyIptvPalette.Positive,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "$enabledCategoryCount/${country.categories.size} catégories",
                                                color = MyIptvPalette.TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            expandedCountry = if (expandedCountry == country.code) {
                                                null
                                            } else {
                                                country.code
                                            }
                                        },
                                        modifier = Modifier.tvFocusBorder(MaterialTheme.shapes.small),
                                    ) {
                                        Text(if (expandedCountry == country.code) "Masquer" else "Catégories")
                                    }
                                    Switch(
                                        checked = countryEnabled,
                                        modifier = Modifier.tvFocusBorder(MaterialTheme.shapes.large),
                                        onCheckedChange = { enabled ->
                                            countries = if (enabled) {
                                                countries + country.code
                                            } else {
                                                countries - country.code
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        if (expandedCountry == country.code) {
                            items(
                                selection.orderedCategories(country),
                                key = { "category:${it.key}" },
                            ) { category ->
                                CategorySwitch(
                                    category = category,
                                    enabled = countryEnabled,
                                    checked = countryEnabled && category.key in categories,
                                    onCheckedChange = { enabled ->
                                        categories = if (enabled) {
                                            categories + category.key
                                        } else {
                                            categories - category.key
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    "Un pays OFF met toutes ses catégories sur OFF. Leur choix précédent revient quand le pays repasse ON.",
                    color = MyIptvPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MenuButton(text = "Annuler", onClick = onDismiss)
                    MenuButton(
                        text = "Appliquer",
                        onClick = { onApply(countries.toSet(), categories.toSet()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySwitch(
    category: EpgCategoryOption,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = if (checked) MyIptvPalette.ActiveSurface else MyIptvPalette.Surface,
        border = BorderStroke(1.dp, MyIptvPalette.Border),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(start = 22.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    color = if (enabled) MyIptvPalette.TextPrimary else MyIptvPalette.Disabled,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${category.epgChannels} EPG",
                    color = MyIptvPalette.Positive,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                modifier = Modifier.tvFocusBorder(MaterialTheme.shapes.large, enabled),
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

internal fun countryLabel(code: String): String {
    val name = when (code) {
        "FR" -> "France"
        "ES" -> "Espagne"
        "IT" -> "Italie"
        "DE" -> "Allemagne"
        "UK", "GB" -> "Royaume-Uni"
        "US", "USA" -> "États-Unis"
        "CA" -> "Canada"
        "CH" -> "Suisse"
        "BE" -> "Belgique"
        "NL" -> "Pays-Bas"
        "DK" -> "Danemark"
        "SE" -> "Suède"
        "NO" -> "Norvège"
        "IE" -> "Irlande"
        "AU" -> "Australie"
        else -> return code
    }
    return "$name · $code"
}
