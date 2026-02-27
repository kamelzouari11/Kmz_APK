package fr.kmz.projects.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.kmz.projects.data.model.Article
import fr.kmz.projects.data.repository.RenovationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArticleViewModel(
    private val repository: RenovationRepository,
    private val sousLotId: Long
) : ViewModel() {
    val articles = repository.getArticlesBySousLotId(sousLotId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun createArticle(nom: String, quantite: Int, unite: String, prixUnitaire: Long) {
        viewModelScope.launch {
            val article = Article(
                sousLotId = sousLotId,
                nom = nom,
                quantite = quantite,
                unite = unite,
                prixUnitaire = prixUnitaire
            )
            repository.insertArticle(article)
        }
    }

    fun updateArticle(article: Article) {
        viewModelScope.launch {
            repository.updateArticle(article)
        }
    }

    fun deleteArticle(article: Article) {
        viewModelScope.launch {
            repository.deleteArticle(article)
        }
    }
}
