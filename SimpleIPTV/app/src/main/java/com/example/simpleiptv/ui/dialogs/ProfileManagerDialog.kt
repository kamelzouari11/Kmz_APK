package com.example.simpleiptv.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.ui.components.FocusableDestructiveButton
import com.example.simpleiptv.ui.components.FocusableTextButton

@Composable
fun ProfileManagerDialog(
        profiles: List<ProfileEntity>,
        onDismiss: () -> Unit,
        onSelectProfile: (ProfileEntity) -> Unit,
        onEdit: (ProfileEntity) -> Unit,
        onDeleteProfile: (ProfileEntity) -> Unit,
        onAdd: () -> Unit,
        onPurge: () -> Unit,
        loadedProfileIds: Set<Int> = emptySet()
) {
        var profileToDelete by remember { mutableStateOf<ProfileEntity?>(null) }

    if (profileToDelete != null) {
        AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = { 
                    Text("Supprimer le profil ?", 
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    ) 
                },
                text = {
                        Text(
                            "Êtes-vous sûr de vouloir supprimer '${profileToDelete?.profileName}' ?\nCette action est irréversible.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                },
                containerColor = Color(0xFF1E1E1E),
                confirmButton = {
                        FocusableDestructiveButton(
                                text = "Supprimer",
                                onClick = {
                                        profileToDelete?.let { onDeleteProfile(it) }
                                        profileToDelete = null
                                    }
                        )
                },
                dismissButton = {
                        FocusableTextButton("Annuler", onClick = { profileToDelete = null })
                }
        )
    }


    AlertDialog(
            onDismissRequest = onDismiss,
            title = { 
                Text("Gérer les profils", 
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                ) 
            },
            containerColor = Color(0xFF1E1E1E),
            confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusableTextButton("Purger", onClick = onPurge)
                        FocusableTextButton("Nouveau Profil", onClick = onAdd)
                    }
            },
            dismissButton = {
                    FocusableTextButton("Fermer", onClick = onDismiss)
            },
            text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileRow(
                                    profile = profile,
                                    isLoaded = loadedProfileIds.contains(profile.id),
                                    onSelect = { onSelectProfile(profile) },
                                    onEdit = { onEdit(profile) },
                                    onDelete = { profileToDelete = profile }
                            )
                        }
                    }
            }
    )

}

@Composable
private fun ProfileRow(
        profile: ProfileEntity,
        isLoaded: Boolean,
        onSelect: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
) {
        var isSelectFocused by remember { mutableStateOf(false) }
        var isEditFocused by remember { mutableStateOf(false) }
        var isDeleteFocused by remember { mutableStateOf(false) }

        Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
        Surface(
                modifier = Modifier.weight(1f)
                        .onFocusChanged { isSelectFocused = it.isFocused }
                        .scale(if (isSelectFocused) 1.02f else 1f)
                        .clickable { onSelect() }
                        .focusable(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = when {
                        isSelectFocused -> Color.White.copy(alpha = 0.2f)
                        profile.isSelected -> Color(0xFFBB86FC).copy(alpha = 0.3f)
                        else -> Color.Transparent
                }
        ) {

                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                        ) {
                                RadioButton(
                                        selected = profile.isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                                selectedColor = if (isSelectFocused) Color.Black else MaterialTheme.colorScheme.primary,
                                                unselectedColor = if (isSelectFocused) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                         Text(
                                                 profile.profileName,
                                                 style = MaterialTheme.typography.titleMedium,
                                                 color = when {
                                                     isSelectFocused -> Color.White
                                                     isLoaded -> Color(0xFFBB86FC)
                                                     else -> Color.White.copy(alpha = 0.9f)
                                                 }
                                         )
                                         if (isLoaded) {
                                                 Spacer(Modifier.width(8.dp))
                                                 Text(
                                                     "(LOADED)",
                                                     style = MaterialTheme.typography.labelSmall,
                                                     color = if (isSelectFocused) Color.White else Color(0xFFBB86FC)
                                                 )
                                         }
                                     }

                                        Text(
                                                profile.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelectFocused) Color.DarkGray else Color.Gray,
                                                maxLines = 1
                                        )
                                }
                        }
                }

                Spacer(Modifier.width(8.dp))

                FocusableIconButton(
                        icon = Icons.Default.Edit,
                        desc = "Modifier",
                        onClick = onEdit,
                        isFocusedState = isEditFocused,
                        onFocusChange = { isEditFocused = it },
                        tintNormal = MaterialTheme.colorScheme.primary
                )

                FocusableIconButton(
                        icon = Icons.Default.Delete,
                        desc = "Supprimer",
                        onClick = onDelete,
                        isFocusedState = isDeleteFocused,
                        onFocusChange = { isDeleteFocused = it },
                        tintNormal = Color.Red
                )
        }
}

    @Composable
    private fun FocusableIconButton(
            icon: androidx.compose.ui.graphics.vector.ImageVector,
            desc: String,
            onClick: () -> Unit,
            isFocusedState: Boolean,
            onFocusChange: (Boolean) -> Unit,
            tintNormal: Color
    ) {
        IconButton(
                onClick = onClick,
                modifier = Modifier
                        .onFocusChanged { onFocusChange(it.isFocused) }
                        .scale(if (isFocusedState) 1.1f else 1f)
                        .background(
                                if (isFocusedState) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                CircleShape
                        )
        ) {
                Icon(icon, desc, tint = if (isFocusedState) Color.White else tintNormal)
        }
    }

