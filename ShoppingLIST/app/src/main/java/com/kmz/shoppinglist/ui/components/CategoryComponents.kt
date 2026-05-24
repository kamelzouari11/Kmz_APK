package com.kmz.shoppinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.ui.theme.*

/** En-tête de groupe de catégorie avec expand/collapse */
@Composable
fun CategoryGroupHeader(
        category: Category,
        articleCount: Int,
        isExpanded: Boolean,
        onClick: () -> Unit
) {
    Card(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = MediumGray),
            shape = RoundedCornerShape(12.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Nom de la catégorie (réduit pour tenir sur une ligne)
            Text(
                    text = category.name,
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
            )

            // Badge nombre d'articles
            Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(TextDarkGray),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = articleCount.toString(),
                        color = White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Icône expand/collapse
            Icon(
                    imageVector =
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Réduire" else "Développer",
                    tint = TextGray,
                    modifier = Modifier.size(24.dp)
            )
        }
    }
}
