package com.example.kanjipractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.kanjipractice.ui.KanjiPracticeApp
import com.example.kanjipractice.ui.theme.KanjiPracticeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KanjiPracticeTheme {
                KanjiPracticeApp()
            }
        }
    }
}
