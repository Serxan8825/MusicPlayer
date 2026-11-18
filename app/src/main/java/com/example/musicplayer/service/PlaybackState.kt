package com.example.musicplayer.service

import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.Song

/**
 * PlaybackState - Musiqi pleyerinin daxili vəziyyətini saxlayan model.
 * Bu məlumatlar MusicService tərəfindən idarə olunur və ViewModel-ə ötürülür.
 */
data class PlaybackState(
    // Hal-hazırda seçilmiş mahnı (susmaya görə siyahıdakı ilk mahnı)
    val song: Song = Playlist.songs.first(),
    
    // Mahnı hal-hazırda çalınırsa true, dayandırılıbsa (pause) false
    val isPlaying: Boolean = false,
    
    // İnternetdən yüklənmə (buffering) prosesi gedirsə true
    val isBuffering: Boolean = false,
    
    // Mahnının hazırkı saniyəsi (millisaniyə ilə, məs: 5000ms = 5-ci saniyə)
    val positionMs: Long = 0L,
    
    // Mahnının ümumi müddəti (millisaniyə ilə)
    val durationMs: Long = 0L,
    
    // Əgər mahnı yüklənərkən xəta baş veribsə true
    val hasError: Boolean = false
)
