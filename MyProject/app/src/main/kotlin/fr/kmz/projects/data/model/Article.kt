package fr.kmz.projects.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = SousLot::class,
            parentColumns = ["id"],
            childColumns = ["sousLotId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Article(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sousLotId: Long,
    val nom: String,
    val quantite: Int = 1,
    val unite: String = "", // ex: m², m, kg, l
    val prixUnitaire: Long = 0, // en centimes pour éviter les décimales
    val dateCreation: Long = System.currentTimeMillis()
) {
    fun calculerTotal(): Long = quantite * prixUnitaire
}
