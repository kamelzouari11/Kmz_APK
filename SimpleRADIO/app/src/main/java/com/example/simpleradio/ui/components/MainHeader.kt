package com.example.simpleradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MainHeader(
        onExportFavorites: () -> Unit,
        onImportFavorites: () -> Unit,
        onPowerOff: () -> Unit,
        onRecentClick: () -> Unit,
        onRefreshClick: () -> Unit,
        onPlayerClick: () -> Unit,
        showPlayerButton: Boolean,
        playerIsPlaying: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).padding(12.dp)) {
        // LIGNE 1: Icône 48dp + Upload + Download + Power
        Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icône Radio 48dp
            Icon(
                    Icons.Default.Radio,
                    "SimpleRADIO",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
            )

            // Bouton Upload
            var isUploadFocused by remember { mutableStateOf(false) }
            IconButton(
                    onClick = onExportFavorites,
                    modifier =
                            Modifier.size(48.dp)
                                    .onFocusChanged { isUploadFocused = it.isFocused }
                                    .background(
                                            if (isUploadFocused) Color.White else Color.Transparent,
                                            CircleShape
                                    )
            ) {
                Icon(
                        Icons.Default.CloudUpload,
                        "Upload",
                        tint = if (isUploadFocused) Color.Black else Color.White
                )
            }

            // Bouton Download
            var isDownloadFocused by remember { mutableStateOf(false) }
            IconButton(
                    onClick = onImportFavorites,
                    modifier =
                            Modifier.size(48.dp)
                                    .onFocusChanged { isDownloadFocused = it.isFocused }
                                    .background(
                                            if (isDownloadFocused) Color.White
                                            else Color.Transparent,
                                            CircleShape
                                    )
            ) {
                Icon(
                        Icons.Default.CloudDownload,
                        "Download",
                        tint = if (isDownloadFocused) Color.Black else Color.White
                )
            }

            // Bouton Power
            var isPowerFocused by remember { mutableStateOf(false) }
            IconButton(
                    onClick = onPowerOff,
                    modifier =
                            Modifier.size(48.dp)
                                    .onFocusChanged { isPowerFocused = it.isFocused }
                                    .background(
                                            if (isPowerFocused) Color.White else Color.Transparent,
                                            CircleShape
                                    )
            ) {
                Icon(
                        Icons.Default.PowerSettingsNew,
                        "Quitter",
                        tint = if (isPowerFocused) Color.Red else Color.Red
                )
            }
        }

        // LIGNE 2: Recent + Refresh + Player (avec texte pour Recent/Refresh)
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton Recent (pillule avec texte)
                var isRecentFocused by remember { mutableStateOf(false) }
                Surface(
                        onClick = onRecentClick,
                        modifier =
                                Modifier.height(48.dp).onFocusChanged {
                                    isRecentFocused = it.isFocused
                                },
                        color = if (isRecentFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                Icons.Default.History,
                                "Récents",
                                tint = if (isRecentFocused) Color.Black else Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                                "Récents",
                                color = if (isRecentFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bouton Refresh (pillule avec texte)
                var isRefreshFocused by remember { mutableStateOf(false) }
                Surface(
                        onClick = onRefreshClick,
                        modifier =
                                Modifier.height(48.dp).onFocusChanged {
                                    isRefreshFocused = it.isFocused
                                },
                        color = if (isRefreshFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                Icons.Default.Refresh,
                                "Refresh",
                                tint = if (isRefreshFocused) Color.Black else Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                                "Refresh",
                                color = if (isRefreshFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bouton Player (icône seulement)
                if (showPlayerButton) {
                    IconButton(onClick = onPlayerClick, modifier = Modifier.size(48.dp)) {
                        Icon(
                                if (playerIsPlaying) Icons.Filled.PlayCircleFilled
                                else Icons.Default.PlayCircleOutline,
                                "Player",
                                tint = Color.White
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}
