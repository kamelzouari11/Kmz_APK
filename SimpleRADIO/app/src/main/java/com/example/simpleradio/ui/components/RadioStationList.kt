package com.example.simpleradio.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.simpleradio.data.local.entities.RadioStationEntity
import kotlin.math.roundToInt

/**
 * RadioStationList avec drag-and-drop fluide.
 * En mode réorganisation, appui long + glisser déplace l'item en temps réel.
 * L'écriture en base n'a lieu qu'une seule fois au relâchement.
 */
@Composable
fun RadioStationList(
    currentRadioList: List<RadioStationEntity>,
    playingRadio: RadioStationEntity?,
    resultsListState: LazyListState,
    listFocusRequester: FocusRequester,
    onRadioSelected: (RadioStationEntity) -> Unit,
    onAddFavorite: (RadioStationEntity) -> Unit,
    onMove: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isReorderMode: Boolean = false,
    allFavoriteUuids: Set<String> = emptySet()
) {
    if (isReorderMode && onMove != null) {
        DraggableRadioList(
            externalList = currentRadioList,
            playingRadio = playingRadio,
            onRadioSelected = onRadioSelected,
            onAddFavorite = onAddFavorite,
            onMove = onMove,
            modifier = modifier,
            allFavoriteUuids = allFavoriteUuids
        )
    } else {
        LazyColumn(
            state = resultsListState,
            modifier = modifier.fillMaxSize().focusRequester(listFocusRequester),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(currentRadioList) { _, radio ->
                MainItem(
                    title = radio.name,
                    subtitle = "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                    iconUrl = radio.favicon,
                    isPlaying = playingRadio?.stationuuid == radio.stationuuid,
                    onClick = { onRadioSelected(radio) },
                    onAddFavorite = { onAddFavorite(radio) },
                    isReorderMode = false,
                    isFavorite = allFavoriteUuids.contains(radio.stationuuid)
                )
            }
            item { Spacer(modifier = Modifier.height(120.dp)) }
        }
    }
}

@Composable
private fun DraggableRadioList(
    externalList: List<RadioStationEntity>,
    playingRadio: RadioStationEntity?,
    onRadioSelected: (RadioStationEntity) -> Unit,
    onAddFavorite: (RadioStationEntity) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    allFavoriteUuids: Set<String> = emptySet()
) {
    // Liste locale mutable — réorganisée instantanément pendant le drag
    // sans toucher au ViewModel ni à la base de données
    var localList by remember(externalList) { mutableStateOf(externalList.toList()) }

    // UUID de l'item en cours de drag
    var draggedUuid by remember { mutableStateOf<String?>(null) }

    // Déplacement visuel accumulé (en pixels) de l'item draggé
    var dragOffsetY by remember { mutableStateOf(0f) }

    // Hauteur réelle d'un item (mesurée au runtime)
    var itemHeightPx by remember { mutableStateOf(0f) }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(localList, key = { _, radio -> radio.stationuuid }) { index, radio ->
            val isDragged = draggedUuid == radio.stationuuid
            val elevation by animateDpAsState(if (isDragged) 8.dp else 0.dp, label = "elev")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Mesure la hauteur d'un item dès la première composition
                    .onGloballyPositioned { coords ->
                        if (itemHeightPx == 0f && coords.size.height > 0) {
                            // height de l'item + spacing (12dp ≈ mais on prend la hauteur réelle)
                            itemHeightPx = coords.size.height.toFloat()
                        }
                    }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            translationY = dragOffsetY
                            shadowElevation = elevation.toPx()
                        }
                    }
                    .background(
                        if (isDragged) Color(0x33FFFFFF) else Color.Transparent
                    )
                    .pointerInput(radio.stationuuid) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedUuid = radio.stationuuid
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y

                                // Hauteur effective avec le spacing entre items
                                val h = (itemHeightPx + 12f).coerceAtLeast(80f)

                                // Index actuel de l'item draggé dans la liste locale
                                val currentIdx = localList.indexOfFirst { it.stationuuid == draggedUuid }
                                if (currentIdx < 0) return@detectDragGesturesAfterLongPress

                                // Combien de "cases" a-t-on dépassé ?
                                val steps = (dragOffsetY / h).roundToInt()

                                if (steps != 0) {
                                    val targetIdx = (currentIdx + steps).coerceIn(0, localList.size - 1)
                                    if (targetIdx != currentIdx) {
                                        // Réordonne la liste locale immédiatement (fluide, pas de DB)
                                        localList = localList.toMutableList().also { list ->
                                            val item = list.removeAt(currentIdx)
                                            list.add(targetIdx, item)
                                        }
                                        // Recalibrage de l'offset pour éviter les sauts
                                        dragOffsetY -= steps * h
                                    }
                                }
                            },
                            onDragEnd = {
                                // Ecriture en base : on compare la liste locale avec l'originale
                                // pour trouver l'item qui a bougé et appeler onMove(from, to)
                                val uuid = draggedUuid
                                if (uuid != null) {
                                    val newIdx = localList.indexOfFirst { it.stationuuid == uuid }
                                    val oldIdx = externalList.indexOfFirst { it.stationuuid == uuid }
                                    if (newIdx >= 0 && oldIdx >= 0 && newIdx != oldIdx) {
                                        onMove(oldIdx, newIdx)
                                    }
                                }
                                draggedUuid = null
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                // Annulation : on revient à la liste d'origine
                                localList = externalList.toList()
                                draggedUuid = null
                                dragOffsetY = 0f
                            }
                        )
                    }
            ) {
                MainItem(
                    title = radio.name,
                    subtitle = "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                    iconUrl = radio.favicon,
                    isPlaying = playingRadio?.stationuuid == radio.stationuuid,
                    onClick = { if (draggedUuid == null) onRadioSelected(radio) },
                    onAddFavorite = { onAddFavorite(radio) },
                    isReorderMode = true,
                    isDragging = isDragged,
                    isFavorite = allFavoriteUuids.contains(radio.stationuuid),
                    onMoveUp = {
                        val i = localList.indexOfFirst { it.stationuuid == radio.stationuuid }
                        if (i > 0) {
                            localList = localList.toMutableList().also { list ->
                                val item = list.removeAt(i)
                                list.add(i - 1, item)
                            }
                            onMove(i, i - 1)
                        }
                    },
                    onMoveDown = {
                        val i = localList.indexOfFirst { it.stationuuid == radio.stationuuid }
                        if (i < localList.size - 1) {
                            localList = localList.toMutableList().also { list ->
                                val item = list.removeAt(i)
                                list.add(i + 1, item)
                            }
                            onMove(i, i + 1)
                        }
                    }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}
