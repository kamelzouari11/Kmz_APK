package com.example.myiptv.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.myiptv.ui.theme.MyIptvPalette
import kotlinx.coroutines.launch

/** One programme per viewport. Long text scrolls intact within its page. */
@Composable
internal fun EpgProgramPager(
    pageKey: Any,
    count: Int,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    content: @Composable (Int) -> Unit,
) {
    if (count == 0) return
    var selectedPage by rememberSaveable(pageKey, initialPage) { mutableStateOf(initialPage) }
    val page = selectedPage.coerceIn(0, count - 1)
    val scroll = remember(pageKey, page) { ScrollState(0) }
    val scope = rememberCoroutineScope()
    Column(
        modifier.onPreviewKeyEvent { event ->
            val direction = when {
                event.key == Key.DirectionDown && scroll.canScrollForward -> 1
                event.key == Key.DirectionUp && scroll.canScrollBackward -> -1
                else -> 0
            }
            if (direction == 0) false else {
                if (event.type == KeyEventType.KeyDown) {
                    scope.launch { scroll.animateScrollBy(direction * 160f) }
                }
                true
            }
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            key(pageKey, page) { content(page) }
        }
        if (scroll.maxValue > 0) {
            Text("↑ ↓ Faire défiler le texte", color = MyIptvPalette.TextSecondary)
        }
        Surface(color = MyIptvPalette.Surface, shape = MaterialTheme.shapes.medium) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuButton(
                    text = "Précédent", enabled = page > 0,
                    onClick = { selectedPage = page - 1 }, modifier = Modifier.weight(1f),
                    maxLines = Int.MAX_VALUE,
                )
                Text("${page + 1} / $count", color = MyIptvPalette.Primary)
                MenuButton(
                    text = "Suivant", enabled = page < count - 1,
                    onClick = { selectedPage = page + 1 }, modifier = Modifier.weight(1f),
                    maxLines = Int.MAX_VALUE,
                )
            }
        }
    }
}
