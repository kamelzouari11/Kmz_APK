package com.kmz.shoppinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kmz.shoppinglist.data.Priority
import com.kmz.shoppinglist.ui.theme.*

/** Dialog de sélection de priorité */
@Composable
fun PriorityDialog(
        currentPriority: Priority,
        onPrioritySelected: (Priority) -> Unit,
        onDismiss: () -> Unit
) {
        Dialog(onDismissRequest = onDismiss) {
                Surface(modifier = Modifier.clip(RoundedCornerShape(16.dp)), color = DarkGray) {
                        Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                        text = "Priorité",
                                        color = White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Priority.values().forEach { priority ->
                                                PriorityOption(
                                                        priority = priority,
                                                        isSelected = priority == currentPriority,
                                                        onClick = { onPrioritySelected(priority) }
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun PriorityOption(priority: Priority, isSelected: Boolean, onClick: () -> Unit) {
        val color = getPriorityColor(priority)
        Box(
                modifier =
                        Modifier.size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) color else color.copy(alpha = 0.2f))
                                .border(if (isSelected) 2.dp else 0.dp, White, CircleShape)
                                .clickable { onClick() },
                contentAlignment = Alignment.Center
        ) {
                if (!isSelected) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
                }
        }
}

fun getPriorityColor(priority: Priority): Color =
        when (priority) {
                Priority.URGENT -> Color(0xFFF44336) // Rouge
                Priority.IMPORTANT -> Color(0xFFFFB300) // Jaune-Orange
                Priority.NORMAL -> Color(0xFF4CAF50) // Vert
                Priority.OPTIONAL -> MediumGray // Gris Moyen
        }

/** Dialog de confirmation de suppression */
@Composable
fun ConfirmDeleteDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
) {
        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkGray,
                title = { Text(title, color = White, fontWeight = FontWeight.Bold) },
                text = { Text(message, color = TextGray) },
                confirmButton = {
                        TextButton(onClick = onConfirm) {
                                Text("Supprimer", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Annuler", color = TextGray) }
                }
        )
}

/** Dialog pour ajouter une catégorie ou un article */
@Composable
fun AddItemDialog(
        title: String,
        placeholder: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit
) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkGray,
                title = { Text(title, color = White, fontWeight = FontWeight.Bold) },
                text = {
                        OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text(placeholder, color = TextGray) },
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = White,
                                                unfocusedTextColor = White,
                                                focusedBorderColor = AccentBlue,
                                                unfocusedBorderColor = MediumGray,
                                                cursorColor = AccentBlue
                                        ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )
                },
                confirmButton = {
                        Button(
                                onClick = { if (text.isNotBlank()) onConfirm(text) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) { Text("Ajouter", color = White) }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Annuler", color = TextGray) }
                }
        )
}

@Composable
fun PriorityFilterButton(
        filterPriority: Priority,
        onFilterClick: (Priority) -> Unit,
        modifier: Modifier = Modifier,
        size: androidx.compose.ui.unit.Dp = 36.dp,
        iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
        val nextPriority =
                when (filterPriority) {
                        Priority.URGENT -> Priority.IMPORTANT
                        Priority.IMPORTANT -> Priority.NORMAL
                        Priority.NORMAL -> Priority.OPTIONAL
                        Priority.OPTIONAL -> Priority.URGENT
                }
        Box(
                modifier =
                        modifier.size(size)
                                .clip(CircleShape)
                                .background(getPriorityColor(filterPriority))
                                .clickable { onFilterClick(nextPriority) },
                contentAlignment = Alignment.Center
        ) {
                Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtre Priorité",
                        tint = White,
                        modifier = Modifier.size(iconSize)
                )
        }
}

@Composable
fun BottomActionButtons(
        onFilterClick: (Priority) -> Unit,
        onMicClick: () -> Unit,
        onAddClick: () -> Unit,
        filterPriority: Priority,
        modifier: Modifier = Modifier,
        showFilter: Boolean = true
) {
        Box(
                modifier = modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.BottomCenter
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        if (showFilter) {
                                PriorityFilterButton(
                                        filterPriority = filterPriority,
                                        onFilterClick = onFilterClick,
                                        size = 56.dp,
                                        iconSize = 28.dp
                                )
                        } else {
                                Spacer(modifier = Modifier.size(56.dp))
                        }
                        FloatingActionButton(
                                onClick = onMicClick,
                                containerColor = AccentGreen,
                                contentColor = White,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Saisie vocale",
                                        modifier = Modifier.size(28.dp)
                                )
                        }
                        FloatingActionButton(
                                onClick = onAddClick,
                                containerColor = AccentBlue,
                                contentColor = White,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Ajouter",
                                        modifier = Modifier.size(28.dp)
                                )
                        }
                }
        }
}

/** En-tête d'écran factorisé */
@Composable
fun ScreenHeader(
        title: String,
        subtitle: String,
        onBackClick: () -> Unit,
        isIconMode: Boolean,
        onIconModeChange: (Boolean) -> Unit,
        filterPriority: Priority,
        onFilterPriorityChange: (Priority) -> Unit,
        extraButtons: @Composable RowScope.() -> Unit = {}
) {
        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(DarkGray)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                color = Color.Transparent
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBackClick) {
                                        Icon(
                                                Icons.Default.ArrowBack,
                                                contentDescription = "Retour",
                                                tint = White
                                        )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                        Text(
                                                text = title,
                                                color = White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(text = subtitle, color = TextGray, fontSize = 11.sp)
                                }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                PriorityFilterButton(
                                        filterPriority = filterPriority,
                                        onFilterClick = onFilterPriorityChange,
                                        size = 56.dp,
                                        iconSize = 36.dp
                                )
                                extraButtons()
                                Spacer(modifier = Modifier.width(10.dp))
                                IconButton(
                                        onClick = { onIconModeChange(!isIconMode) },
                                        modifier =
                                                Modifier.size(56.dp)
                                                        .background(AccentBlue, CircleShape)
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isIconMode) Icons.Default.List
                                                        else Icons.Default.ShoppingCart,
                                                contentDescription = "Changer de vue",
                                                tint = White,
                                                modifier = Modifier.size(36.dp)
                                        )
                                }
                        }
                }
        }
}

/** En-tête de l'écran principal (Categories) */
@Composable
fun MainScreenHeader(
        title: String,
        onExportClick: () -> Unit,
        onImportClick: () -> Unit,
        onIconManagerClick: () -> Unit
) {
        Surface(
                modifier = Modifier.fillMaxWidth().background(DarkGray).padding(8.dp),
                color = Color.Transparent
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = title,
                                color = White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onExportClick) {
                                        Icon(Icons.Default.Upload, "Exporter", tint = White)
                                }
                                IconButton(onClick = onImportClick) {
                                        Icon(Icons.Default.Download, "Importer", tint = White)
                                }
                                IconButton(
                                        onClick = onIconManagerClick,
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(AccentViolet, CircleShape)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Gérer les icônes",
                                                tint = White,
                                                modifier = Modifier.size(22.dp)
                                        )
                                }
                        }
                }
        }
}

/** En-tête d'écran simple avec titre centré et retour */
@Composable
fun SimpleScreenHeader(title: String, onBackClick: () -> Unit) {
        Surface(
                modifier = Modifier.fillMaxWidth().background(DarkGray).padding(8.dp),
                color = Color.Transparent
        ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                                Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Retour",
                                        tint = White
                                )
                        }

                        Text(
                                text = title,
                                color = White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                        )
                }
        }
}
