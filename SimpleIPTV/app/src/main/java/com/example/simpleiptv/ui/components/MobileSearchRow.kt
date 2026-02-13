package com.example.simpleiptv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.ui.viewmodel.MainViewModel

@Composable
fun MobileSearchRow(viewModel: MainViewModel) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
    ) {
        TvInput(
                value = viewModel.searchQuery,
                onValueChange = {
                    viewModel.searchQuery = it
                    viewModel.refreshChannels(debounce = true)
                },
                label = "Filtrer les chaînes...",
                focusManager = LocalFocusManager.current,
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.padding(4.dp)
        )
    }
}
