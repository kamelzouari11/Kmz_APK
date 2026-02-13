package com.example.simpleradio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.simpleradio.data.local.entities.RadioStationEntity

/** Reusable radio station list used in both portrait and landscape layouts of BrowseScreen. */
@Composable
fun RadioStationList(
        currentRadioList: List<RadioStationEntity>,
        playingRadio: RadioStationEntity?,
        resultsListState: LazyListState,
        listFocusRequester: FocusRequester,
        onRadioSelected: (RadioStationEntity) -> Unit,
        onAddFavorite: (RadioStationEntity) -> Unit,
        modifier: Modifier = Modifier
) {
    LazyColumn(
            state = resultsListState,
            modifier = modifier.fillMaxSize().focusRequester(listFocusRequester),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(currentRadioList) { radio ->
            MainItem(
                    title = radio.name,
                    subtitle = "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                    iconUrl = radio.favicon,
                    isPlaying = playingRadio?.stationuuid == radio.stationuuid,
                    onClick = { onRadioSelected(radio) },
                    onAddFavorite = { onAddFavorite(radio) }
            )
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}
