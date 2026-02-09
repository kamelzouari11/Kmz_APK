package com.example.simpleiptv.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable
fun ProfileManagerDialog(
        profiles: List<ProfileEntity>,
        onDismiss: () -> Unit,
        onSelectProfile: (ProfileEntity) -> Unit,
        onEdit: (ProfileEntity) -> Unit,
        onDeleteProfile: (ProfileEntity) -> Unit,
        onAdd: () -> Unit,
        onPurge: () -> Unit
) {
        var profileToDelete by remember { mutableStateOf<ProfileEntity?>(null) }

        if (profileToDelete != null) {
                AlertDialog(
                        onDismissRequest = { profileToDelete = null },
                        title = { Text("Supprimer le profil ?") },
                        text = {
                                Text(
                                        "Êtes-vous sûr de vouloir supprimer '${profileToDelete?.profileName}' ?\nCette action est irréversible."
                                )
                        },
                        confirmButton = {
                                var isFocused by remember { mutableStateOf(false) }
                                Button(
                                        onClick = {
                                                profileToDelete?.let { onDeleteProfile(it) }
                                                profileToDelete = null
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                if (isFocused) Color.White
                                                                else Color.Red,
                                                        contentColor =
                                                                if (isFocused) Color.Black
                                                                else Color.White
                                                ),
                                        modifier =
                                                Modifier.onFocusChanged { isFocused = it.isFocused }
                                                        .scale(if (isFocused) 1.05f else 1f)
                                                        .border(
                                                                if (isFocused) 2.dp else 0.dp,
                                                                Color.White,
                                                                MaterialTheme.shapes.extraSmall
                                                        )
                                ) { Text("Supprimer") }
                        },
                        dismissButton = {
                                var isFocused by remember { mutableStateOf(false) }
                                TextButton(
                                        onClick = { profileToDelete = null },
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        contentColor =
                                                                if (isFocused) Color.Black
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                ),
                                        modifier =
                                                Modifier.onFocusChanged { isFocused = it.isFocused }
                                                        .scale(if (isFocused) 1.05f else 1f)
                                                        .background(
                                                                if (isFocused) Color.White
                                                                else Color.Transparent,
                                                                MaterialTheme.shapes.extraSmall
                                                        )
                                ) { Text("Annuler") }
                        }
                )
        }

        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Gérer les profils") },
                confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Purger (Sweep) duplicates
                                var isPurgeFocused by remember { mutableStateOf(false) }
                                TextButton(
                                        onClick = onPurge,
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        contentColor =
                                                                if (isPurgeFocused) Color.Black
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                ),
                                        modifier =
                                                Modifier.onFocusChanged {
                                                                isPurgeFocused = it.isFocused
                                                        }
                                                        .scale(if (isPurgeFocused) 1.05f else 1f)
                                                        .background(
                                                                if (isPurgeFocused) Color.White
                                                                else Color.Transparent,
                                                                MaterialTheme.shapes.extraSmall
                                                        )
                                ) { Text("Purger") }

                                // Nouveau Profil
                                var isAddFocused by remember { mutableStateOf(false) }
                                TextButton(
                                        onClick = onAdd,
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        contentColor =
                                                                if (isAddFocused) Color.Black
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                ),
                                        modifier =
                                                Modifier.onFocusChanged {
                                                                isAddFocused = it.isFocused
                                                        }
                                                        .scale(if (isAddFocused) 1.05f else 1f)
                                                        .background(
                                                                if (isAddFocused) Color.White
                                                                else Color.Transparent,
                                                                MaterialTheme.shapes.extraSmall
                                                        )
                                ) { Text("Nouveau Profil") }
                        }
                },
                dismissButton = {
                        var isFocused by remember { mutableStateOf(false) }
                        TextButton(
                                onClick = onDismiss,
                                colors =
                                        ButtonDefaults.textButtonColors(
                                                contentColor =
                                                        if (isFocused) Color.Black
                                                        else MaterialTheme.colorScheme.primary
                                        ),
                                modifier =
                                        Modifier.onFocusChanged { isFocused = it.isFocused }
                                                .scale(if (isFocused) 1.05f else 1f)
                                                .background(
                                                        if (isFocused) Color.White
                                                        else Color.Transparent,
                                                        MaterialTheme.shapes.extraSmall
                                                )
                        ) { Text("Fermer") }
                },
                text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(profiles) { profile ->
                                        var isSelectFocused by remember { mutableStateOf(false) }
                                        var isEditFocused by remember { mutableStateOf(false) }
                                        var isDeleteFocused by remember { mutableStateOf(false) }

                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Surface(
                                                        modifier =
                                                                Modifier.weight(1f)
                                                                        .onFocusChanged {
                                                                                isSelectFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .scale(
                                                                                if (isSelectFocused)
                                                                                        1.02f
                                                                                else 1f
                                                                        )
                                                                        .clickable {
                                                                                onSelectProfile(
                                                                                        profile
                                                                                )
                                                                        }
                                                                        .focusable(),
                                                        shape = MaterialTheme.shapes.small,
                                                        color =
                                                                if (isSelectFocused)
                                                                        Color.White.copy(
                                                                                alpha = 0.9f
                                                                        )
                                                                else if (profile.isSelected)
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                else Color.Transparent
                                                ) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                modifier = Modifier.padding(8.dp)
                                                        ) {
                                                                RadioButton(
                                                                        selected =
                                                                                profile.isSelected,
                                                                        onClick = null,
                                                                        colors =
                                                                                RadioButtonDefaults
                                                                                        .colors(
                                                                                                selectedColor =
                                                                                                        if (isSelectFocused
                                                                                                        )
                                                                                                                Color.Black
                                                                                                        else
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .primary,
                                                                                                unselectedColor =
                                                                                                        if (isSelectFocused
                                                                                                        )
                                                                                                                Color.Gray
                                                                                                        else
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .onSurfaceVariant
                                                                                        )
                                                                )
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        start = 8.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                profile.profileName,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleMedium,
                                                                                color =
                                                                                        if (isSelectFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.Unspecified
                                                                        )
                                                                        Text(
                                                                                profile.url,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        if (isSelectFocused
                                                                                        )
                                                                                                Color.DarkGray
                                                                                        else
                                                                                                Color.Gray,
                                                                                maxLines = 1
                                                                        )
                                                                }
                                                        }
                                                }

                                                Spacer(Modifier.width(8.dp))

                                                IconButton(
                                                        onClick = { onEdit(profile) },
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isEditFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .scale(
                                                                                if (isEditFocused)
                                                                                        1.1f
                                                                                else 1f
                                                                        )
                                                                        .background(
                                                                                if (isEditFocused)
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.2f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Edit,
                                                                "Modifier",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                }

                                                IconButton(
                                                        onClick = { profileToDelete = profile },
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isDeleteFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .scale(
                                                                                if (isDeleteFocused)
                                                                                        1.1f
                                                                                else 1f
                                                                        )
                                                                        .background(
                                                                                if (isDeleteFocused)
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.2f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Delete,
                                                                "Supprimer",
                                                                tint = Color.Red
                                                        )
                                                }
                                        }
                                }
                        }
                }
        )
}
