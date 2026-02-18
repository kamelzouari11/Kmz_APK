package com.kmz.miniserver

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(isRunning: Boolean, ip: String, onToggle: () -> Unit, onRefreshIp: () -> Unit) {
    val context = LocalContext.current
    val logoBitmap = remember {
        try {
            context.assets.open("logo.png").use { inputStream ->
                BitmapFactory.decodeStream(inputStream).asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.verticalGradient(
                                            listOf(Color(0xFF0F0F0F), Color(0xFF1A1A1A))
                                    )
                            ),
            contentAlignment = Alignment.Center
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            if (logoBitmap != null) {
                Image(
                        bitmap = logoBitmap,
                        contentDescription = "Logo",
                        modifier = Modifier.size(120.dp),
                        contentScale = ContentScale.Fit
                )
            } else {
                // Espaceur ou icône par défaut si pas de logo
                Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.DarkGray
                )
            }

            Text(
                    "Mini SERVER",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
            )

            Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                            if (isRunning) "DÉMARRÉ" else "ARRÊTÉ",
                            color = if (isRunning) Color(0xFF00E676) else Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("http://$ip:${AppConstants.PORT}", color = Color.White, fontSize = 18.sp)
                    IconButton(onClick = onRefreshIp) {
                        Icon(Icons.Default.Refresh, null, tint = Color.Cyan)
                    }
                    if (ip == "127.0.0.1") {
                        Text(
                                "Attention: Utilisez l'IP locale (ex: 192.168.x.x) pour les autres appareils",
                                color = Color(0xFFFFAB40),
                                fontSize = 10.sp
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dossier de sauvegarde :", color = Color.Gray, fontSize = 12.sp)
                Text(
                        "Download/${AppConstants.FOLDER_NAME}",
                        color = Color.LightGray,
                        fontSize = 14.sp
                )
            }

            Button(
                    onClick = onToggle,
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor =
                                            if (isRunning) Color(0xFFFF5252) else Color(0xFF00E676)
                            ),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(30.dp)
            ) {
                Text(
                        if (isRunning) "STOP" else "START",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                )
            }
        }
    }
}

@Composable
fun MiniServerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme =
                    darkColorScheme(primary = Color(0xFF00E676), background = Color(0xFF0F0F0F)),
            content = content
    )
}
