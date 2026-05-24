package fr.kmz.projects.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class SousLotWithArticles(
    @Embedded val sousLot: SousLot,
    @Relation(
        parentColumn = "id",
        entityColumn = "sousLotId",
        entity = Article::class
    )
    val articles: List<Article>
) {
    fun calculerTotal(): Long = articles.sumOf { it.calculerTotal() }
}

data class LotWithSousLots(
    @Embedded val lot: Lot,
    @Relation(
        parentColumn = "id",
        entityColumn = "lotId",
        entity = SousLot::class
    )
    val sousLots: List<SousLotWithArticles>
) {
    fun calculerTotal(): Long = sousLots.sumOf { it.calculerTotal() }
}
