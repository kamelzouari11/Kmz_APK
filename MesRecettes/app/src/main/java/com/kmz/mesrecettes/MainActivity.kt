package com.kmz.mesrecettes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmz.mesrecettes.data.AppDatabase
import com.kmz.mesrecettes.ui.MainScreen
import com.kmz.mesrecettes.ui.RecipeViewModel
import com.kmz.mesrecettes.ui.RecipeViewModelFactory
import com.kmz.mesrecettes.ui.theme.MesRecettesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)

        setContent {
            MesRecettesTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: RecipeViewModel =
                            viewModel(factory = RecipeViewModelFactory(database.recipeDao()))
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
