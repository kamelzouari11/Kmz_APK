package com.example.simpleradio.upnp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleradio.ui.components.UpnpManager

@Composable
fun UpnpButton(upnpManager: UpnpManager, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
            onClick = {
                showDialog = true
                upnpManager.startDiscovery()
            },
            modifier = modifier
    ) {
        Icon(
                imageVector = Icons.Default.SettingsInputAntenna,
                contentDescription = "UPnP Devices",
                tint =
                        if (upnpManager.selectedDevice != null) MaterialTheme.colorScheme.primary
                        else Color.White
        )
    }

    if (showDialog) {
        AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    upnpManager.stopDiscovery()
                },
                title = { Text("Appareils UPnP / DLNA") },
                text = {
                    Column {
                        if (upnpManager.devices.isEmpty()) {
                            Box(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                items(upnpManager.devices) { device ->
                                    ListItem(
                                            headlineContent = { Text(device.friendlyName) },
                                            supportingContent = { Text(device.remoteIp) },
                                            modifier =
                                                    Modifier.clickable {
                                                        upnpManager.connectToDevice(device)
                                                        showDialog = false
                                                    },
                                            trailingContent = {
                                                if (upnpManager.selectedDevice?.uuid == device.uuid
                                                ) {
                                                    Icon(
                                                            Icons.Default.Cast,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (upnpManager.selectedDevice != null) {
                        TextButton(
                                onClick = {
                                    upnpManager.disconnect()
                                    showDialog = false
                                }
                        ) { Text("Déconnecter", color = MaterialTheme.colorScheme.error) }
                    }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Fermer") } }
        )
    }
}
