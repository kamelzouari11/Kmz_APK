package fr.kmz.projects.data.repository

import fr.kmz.projects.data.db.ArticleDao
import fr.kmz.projects.data.db.LotDao
import fr.kmz.projects.data.db.SousLotDao
import fr.kmz.projects.data.model.Article
import fr.kmz.projects.data.model.Lot
import fr.kmz.projects.data.model.LotWithSousLots
import fr.kmz.projects.data.model.SousLot
import fr.kmz.projects.data.model.SousLotWithArticles
import kotlinx.coroutines.flow.Flow

class RenovationRepository(
    private val lotDao: LotDao,
    private val sousLotDao: SousLotDao,
    private val articleDao: ArticleDao
) {
    // Lot operations
    fun getAllLots(): Flow<List<Lot>> = lotDao.getAllLots()

    // new: lots with sous-lots including their articles to compute totals
    fun getAllLotsWithSousLots(): Flow<List<LotWithSousLots>> = lotDao.getAllLotsWithSousLots()

    suspend fun getLotWithSousLots(id: Long): LotWithSousLots? = lotDao.getLotWithSousLots(id)

    suspend fun insertLot(lot: Lot): Long = lotDao.insert(lot)

    suspend fun updateLot(lot: Lot) = lotDao.update(lot)

    suspend fun deleteLot(lot: Lot) = lotDao.delete(lot)

    suspend fun validateLot(lotId: Long, isValidated: Boolean) =
        lotDao.updateValidation(lotId, isValidated)

    // SousLot operations
    fun getSousLotsByLotId(lotId: Long): Flow<List<SousLot>> =
        sousLotDao.getSousLotsByLotId(lotId)

    // new: include articles for each sous-lot under a lot
    fun getSousLotsWithArticlesByLotId(lotId: Long): Flow<List<SousLotWithArticles>> =
        sousLotDao.getSousLotsWithArticlesByLotId(lotId)

    suspend fun insertSousLot(sousLot: SousLot): Long = sousLotDao.insert(sousLot)

    suspend fun updateSousLot(sousLot: SousLot) = sousLotDao.update(sousLot)

    suspend fun deleteSousLot(sousLot: SousLot) = sousLotDao.delete(sousLot)

    suspend fun validateSousLot(sousLotId: Long, isValidated: Boolean) =
        sousLotDao.updateValidation(sousLotId, isValidated)

    suspend fun getSousLotWithArticles(sousLotId: Long): SousLotWithArticles? =
        sousLotDao.getSousLotWithArticles(sousLotId)

    // Article operations
    fun getArticlesBySousLotId(sousLotId: Long): Flow<List<Article>> =
        articleDao.getArticlesBySousLotId(sousLotId)

    suspend fun insertArticle(article: Article): Long = articleDao.insert(article)

    suspend fun updateArticle(article: Article) = articleDao.update(article)

    suspend fun deleteArticle(article: Article) = articleDao.delete(article)
}
