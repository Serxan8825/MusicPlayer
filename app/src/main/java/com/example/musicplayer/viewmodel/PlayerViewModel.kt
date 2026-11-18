package com.example.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.Song
import com.example.musicplayer.service.MusicService
import com.example.musicplayer.service.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * PlayerUiState - Ekranın (UI) hazırkı vəziyyətini təmsil edən məlumat modeli.
 */
data class PlayerUiState(
    val song: Song? = null,             // Hazırda çalınan mahnı
    val isConnected: Boolean = false,   // Servisə qoşulubmu?
    val isPlaying: Boolean = false,     // Mahnı çalınırsa true
    val isBuffering: Boolean = false,   // Yüklənmə (loading) gedirsə true
    val hasError: Boolean = false,      // Xəta baş veribsə true
    val progress: Float = 0f,           // Mahnının gedişatı (0.0 - 1.0 arası)
    val positionText: String = "0:00",  // Keçən vaxtın mətni (məs: "1:20")
    val durationText: String = "0:00"   // Mahnının ümumi müddəti (məs: "3:45")
)

/**
 * PlayerViewModel - UI ilə MusicService arasında vasitəçidir.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // Bağlanmış MusicService obyektini saxlamaq üçün Flow
    private val service = MutableStateFlow<MusicService?>(null)
    
    // İstifadəçi tərəfindən Seekbar (proqres bar) sürüşdürüləndə müvəqqəti vaxtı saxlayır
    private val seekFraction = MutableStateFlow<Float?>(null)

    // Servisə qoşulma prosesini idarə edən obyekt
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            // Servisə uğurla qoşulduqda Binder vasitəsilə servis obyektini alırıq
            service.value = (binder as MusicService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Servis gözlənilmədən kəsildikdə
            service.value = null
        }
    }

    /**
     * uiState - Ekranın (Compose UI) müşahidə etdiyi əsas məlumat axını.
     * Servisdən gələn məlumatları və istifadəçinin Seekbar hərəkətlərini birləşdirir.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlayerUiState> =
        combine(
            // Servisin daxili vəziyyətini izləyirik
            service.flatMapLatest { it?.state ?: flowOf<PlaybackState?>(null) },
            seekFraction
        ) { playback, seek ->
            // Gələn xam məlumatları UI-ın başa düşəcəyi formata salırıq
            mapToUiState(playback, seek)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000), // Ekran bağlanandan 5 saniyə sonra izləməni dayandır
                initialValue = PlayerUiState()
            )

    init {
        // ViewModel yaradılan kimi MusicService-ə qoşulmağa başlayırıq
        val app = getApplication<Application>()
        app.bindService(
            Intent(app, MusicService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    // Play/Pause düyməsinə kliklədikdə
    fun onPlayPauseClick() {
        service.value?.togglePlayPause()
    }

    // Növbəti mahnı düyməsinə kliklədikdə
    fun onNextClick() {
        service.value?.next()
    }

    // Əvvəlki mahnı düyməsinə kliklədikdə
    fun onPreviousClick() {
        service.value?.previous()
    }

    // Seekbar (proqres bar) sürüşdürüləndə çağırılır
    fun onSeekChange(fraction: Float) {
        seekFraction.value = fraction
    }

    // Seekbar sürüşdürülməsi bitəndə mahnını həmin vaxta keçiririk
    fun onSeekFinished() {
        val fraction = seekFraction.value ?: return
        val s = service.value
        val duration = s?.state?.value?.durationMs ?: 0L
        s?.seekTo((fraction * duration).toLong())
        seekFraction.value = null // Müvəqqəti vəziyyəti sıfırlayırıq
    }

    /**
     * Servisdən gələn PlaybackState-i PlayerUiState-ə çevirir.
     */
    private fun mapToUiState(playback: PlaybackState?, seek: Float?): PlayerUiState {
        if (playback == null) return PlayerUiState()

        val duration = playback.durationMs

        // Əgər istifadəçi barı sürüşdürürsə həmin mövqeni, yoxsa mahnının hazırkı mövqeyini göstəririk
        val fraction = seek ?: if (duration > 0) {
            (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        val shownPositionMs = (fraction * duration).toLong()

        return PlayerUiState(
            song = playback.song,
            isConnected = true,
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            hasError = playback.hasError,
            progress = fraction,
            positionText = formatTime(shownPositionMs),
            durationText = formatTime(duration)
        )
    }

    /**
     * Millisaniyəni "Dəqiqə:Saniyə" (məs: 3:45) formatına salır.
     */
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun onCleared() {
        // ViewModel yaddaşdan silinəndə servis bağlantısını kəsirik (resurs sızmasının qarşısını almaq üçün)
        getApplication<Application>().unbindService(connection)
        super.onCleared()
    }
}
