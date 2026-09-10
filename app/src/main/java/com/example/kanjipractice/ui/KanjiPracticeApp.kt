package com.example.kanjipractice.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kanjipractice.ui.browse.BrowseScreen
import com.example.kanjipractice.ui.deck.DeckListScreen
import com.example.kanjipractice.ui.review.ReviewScreen

private object Routes {
    const val DECK = "deck"
    const val REVIEW = "review"
    const val BROWSE = "browse"
}

@Composable
fun KanjiPracticeApp(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DECK) {
        composable(Routes.DECK) {
            DeckListScreen(
                onStartReview = { navController.navigate(Routes.REVIEW) },
                onBrowse = { navController.navigate(Routes.BROWSE) },
            )
        }
        composable(Routes.REVIEW) {
            // Leaving pops this entry, so the next visit builds a fresh
            // ReviewViewModel and therefore a fresh session.
            ReviewScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.BROWSE) {
            BrowseScreen(onBack = { navController.popBackStack() })
        }
    }
}
