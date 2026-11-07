package io.github.seungminshin97.japeace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seungminshin97.japeace.presentation.HomeRoute
import io.github.seungminshin97.japeace.presentation.HomeViewModel
import io.github.seungminshin97.japeace.presentation.theme.JapeaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dependencies = createDependencies()
        setContent {
            JapeaceTheme {
                val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(dependencies))
                HomeRoute(homeViewModel)
            }
        }
    }
}
