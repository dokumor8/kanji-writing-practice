package com.example.kanjipractice.ui.licences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kanjipractice.BuildConfig
import com.example.kanjipractice.domain.AppScript

/**
 * Attribution and licences.
 *
 * Not decoration: the bundled stroke diagrams are CC BY-SA 3.0 and the card data
 * is CC BY-SA 4.0, both of which require credit to travel with the distribution.
 * The SVG files keep their own copyright headers inside the APK, but a user
 * should not have to unzip it to find out who made the material.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Licences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Kanji Practice " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("This app")
            Text(
                text = "The code in this app is released into the public domain " +
                    "under the Unlicense. That does not extend to the card data or " +
                    "the stroke diagrams, which keep the licences listed below.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { uriHandler.openUri("https://unlicense.org/") },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(text = "https://unlicense.org/", style = MaterialTheme.typography.labelSmall)
            }

            Section("Card data")
            AppScript.licences.forEach { entry ->
                Entry(
                    title = entry.title,
                    body = entry.body,
                    link = entry.link,
                    onOpen = uriHandler::openUri,
                )
            }

            Section("Software")
            Entry(
                title = "AndroidX, Jetpack Compose, Room, Hilt, Kotlin, OkHttp",
                body = "Licensed under the Apache License 2.0.",
                link = "https://www.apache.org/licenses/LICENSE-2.0",
                onOpen = uriHandler::openUri,
            )
            Entry(
                title = "ML Kit Digital Ink Recognition",
                body = "Copyright Google. Used under the ML Kit Terms of Service, " +
                    "which incorporate the Google APIs Terms of Service. The " +
                    "Japanese recognition model is downloaded from Google on first " +
                    "use and is not redistributed with this app.",
                link = "https://developers.google.com/ml-kit/terms",
                onOpen = uriHandler::openUri,
            )

            Section("What leaves your device")
            Text(
                text = "Your handwriting does not. Recognition runs entirely " +
                    "on-device, and neither the strokes nor the results are sent " +
                    "anywhere.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Two things do use the network. The Japanese recognition " +
                    "model is downloaded once, from Google, and ML Kit sends Google " +
                    "metrics about how the recognition API performs in this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uriHandler.openUri("https://developers.google.com/ml-kit/terms") }) {
                Text("ML Kit terms and privacy")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 10.dp))
}

@Composable
private fun Entry(title: String, body: String, link: String, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onOpen(link) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(text = link, style = MaterialTheme.typography.labelSmall)
        }
    }
}
