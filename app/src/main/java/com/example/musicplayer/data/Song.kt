package com.example.musicplayer.data

/**
 * Song - Hər bir mahnının məlumatlarını saxlayan model (Data Class).
 */
data class Song(
    val id: Int,               // Mahnının unikal nömrəsi
    val title: String,         // Mahnının adı
    val artist: String,        // İfaçının adı
    val url: String,           // Musiqi faylının internet linki (MP3)
    val coverColors: List<Long> // Dizayn üçün istifadə olunan rəng kodları (Gradient üçün)
)

/**
 * Playlist - Tətbiqdə göstərilən mahnıların siyahısını saxlayan obyekt.
 */
object Playlist {
    val songs = listOf(
        Song(
            1, "Love Song", "Love Artist",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            listOf(0xFF1E3C72, 0xFFFF7E5F, 0xFFFEB47B)
        ),
        Song(
            2, "Sunset Drive", "Night Rider",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            listOf(0xFF42275A, 0xFFE96443, 0xFF904E95)
        ),
        Song(
            3, "Ocean Eyes", "Blue Waves",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            listOf(0xFF0F2027, 0xFF2C7DA0, 0xFF61A5C2)
        ),
        Song(
            4, "Midnight City", "Neon Lights",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            listOf(0xFF141E30, 0xFF7F00FF, 0xFFE100FF)
        )
    )
}
