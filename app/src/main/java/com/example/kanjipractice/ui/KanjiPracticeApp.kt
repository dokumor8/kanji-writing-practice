package com.example.kanjipractice.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kanjipractice.ui.browse.BrowseScreen
import com.example.kanjipractice.ui.deck.DeckListScreen
import com.example.kanjipractice.ui.licences.LicencesScreen
import com.example.kanjipractice.ui.review.ReviewScreen
import com.example.kanjipractice.ui.settings.SettingsScreen

private object Routes {
    const val DECK = "deck"
    const val REVIEW = "review"
    const val BROWSE = "browse"
    const val SETTINGS = "settings"
    const val LICENCES = "licences"
}

@Composable
fun KanjiPracticeApp(navController: NavHostController = rememberNavController()) {
    // Screens that bring their own Scaffold already sit on a themed background,
    // but the deck screen does not: with no Surface above it Compose's default
    // content colour is plain black, which is all but invisible on the dark theme.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        NavHost(navController = navController, startDestination = Routes.DECK) {
        composable(Routes.DECK) {
            DeckListScreen(
                onStartReview = { navController.navigate(Routes.REVIEW) },
                onBrowse = { navController.navigate(Routes.BROWSE) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
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
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLicences = { navController.navigate(Routes.LICENCES) },
            )
        }
            composable(Routes.LICENCES) {
                LicencesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
