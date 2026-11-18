package com.example.musicplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tətbiqin rəng palitrası - Tünd mövzu (Dark Theme) üçün təyin olunub.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7986CB),     // Əsas rəng (məsələn, düymələr üçün)
    onPrimary = Color.White,        // Əsas rəngin üzərindəki mətn rəngi
    background = Color(0xFF1B2A41),  // Tətbiqin arxa fon rəngi
    onBackground = Color.White       // Arxa fon üzərindəki mətn rəngi
)

/**
 * MusicPlayerTheme - Bütün UI komponentlərinə vahid dizayn və rəng stilini tətbiq edən Composable funksiya.
 */
@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors, // Yuxarıda təyin etdiyimiz tünd rəngləri istifadə et
        content = content         // Daxilinə ötürülən bütün UI elementləri bu dizayna bürünəcək
    )
}
