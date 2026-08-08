package com.kmz.shazamplayer.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        onLoadCsv: () -> Unit,
        onExit: () -> Unit,
        years: List<String>,
        months: List<String>,
        selectedYear: String,
        selectedMonth: String,
        magicArtistValue: String,
        shazamArtistValue: String,
        shazamTitleValue: String,
        isActuallyPlaying: Boolean,
        sleepTimerMinutes: Int,
        onYearChange: (String) -> Unit,
        onMonthChange: (String) -> Unit,
        onMagicArtistInputChange: (String) -> Unit,
        onShazamArtistInputChange: (String) -> Unit,
        onShazamTitleInputChange: (String) -> Unit,
        onApply: () -> Unit,
        onMagicSearch: (String) -> Unit,
        onSetSleepTimer: (Int) -> Unit,
        onBackToPlaylist: () -> Unit
) {
    val context = LocalContext.current
    var showTimerDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (isActuallyPlaying) {
            // If playing, go back to PLAYLIST instead of exiting
            onBackToPlaylist()
        } else {
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    "Shazam Player",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Shortcut to Playlist (Screen B)
                IconButton(onClick = onBackToPlaylist) {
                    Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = "Playlist",
                            tint = Color(0xFF00FF88)
                    )
                }

                IconButton(onClick = { showTimerDialog = true }) {
                    Icon(
                            if (sleepTimerMinutes > 0) Icons.Default.Timer
                            else Icons.Outlined.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerMinutes > 0) Color(0xFF00FF88) else Color.White
                    )
                }

                IconButton(onClick = onExit) {
                    Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Shutdown",
                            tint = Color.Red,
                            modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (showTimerDialog) {
            AlertDialog(
                    onDismissRequest = { showTimerDialog = false },
                    title = { Text("Sommeil (minutes)", color = Color.White) },
                    text = {
                        Column {
                            listOf(0, 10, 20, 30, 40).forEach { mins ->
                                TextButton(
                                        onClick = {
                                            onSetSleepTimer(mins)
                                            showTimerDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                    @Suppress("DEPRECATION")
                                    Text(
                                            if (mins == 0) "Désactiver" else "$mins min",
                                            color = Color.White
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTimerDialog = false }) { Text("Fermer") }
                    },
                    containerColor = Color(0xFF222222),
                    titleContentColor = Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
                onClick = onLoadCsv,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088FF)),
                shape = RoundedCornerShape(8.dp)
        ) {
            @Suppress("DEPRECATION")
            Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Charger Library CSV", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- NOUVEAU : MAGIC SEARCH ---
        Text(
                "Magic Artist Radio 🪄",
                color = Color(0xFF00FF88),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
                value = magicArtistValue,
                onValueChange = onMagicArtistInputChange,
                label = { Text("Nom de l'artiste (ex: Pink Floyd)", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                            onClick = {
                                if (magicArtistValue.isNotBlank()) onMagicSearch(magicArtistValue)
                            }
                    ) {
                        Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Search",
                                tint = Color(0xFF00FF88)
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                        KeyboardActions(
                                onSearch = {
                                    if (magicArtistValue.isNotBlank())
                                            onMagicSearch(magicArtistValue)
                                }
                        ),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FF88),
                                unfocusedBorderColor = Color.Gray
                        )
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("Filtres", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown("Année", selectedYear, years, onYearChange)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown("Mois", selectedMonth, months, onMonthChange)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
                value = shazamArtistValue,
                onValueChange = onShazamArtistInputChange,
                label = { Text("Artiste") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                        )
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
                value = shazamTitleValue,
                onValueChange = onShazamTitleInputChange,
                label = { Text("Titre") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                        )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp)
        ) { Text("Afficher la Playlist", fontSize = 15.sp) }

        // Bottom spacer (Screen A) to avoid being covered by mini player
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
        label: String,
        selected: String,
        options: List<String>,
        onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.White
                        )
                    }
                },
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                        )
        )
        DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF222222)).fillMaxWidth(0.8f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                )
            }
        }
    }
}
