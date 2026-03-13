package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fr.kmz.projects.data.model.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Insert suspend fun insert(article: Article): Long

    @Update suspend fun update(article: Article)

    @Delete suspend fun delete(article: Article)

    @Query("DELETE FROM articles") suspend fun clearAllArticles()

    @Query("SELECT * FROM articles WHERE sousLotId = :sousLotId ORDER BY dateCreation DESC")
    fun getArticlesBySousLotId(sousLotId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE id = :id") suspend fun getArticleById(id: Long): Article?
}
