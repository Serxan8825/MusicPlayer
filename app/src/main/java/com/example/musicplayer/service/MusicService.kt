package com.example.musicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.musicplayer.MainActivity
import com.example.musicplayer.R
import com.example.musicplayer.data.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * MusicService - Musiqini arxa fonda (background) kəsilmədən çalınmasını təmin edən əsas servis sinfidir.
 */
class MusicService : Service() {

    // ViewModel-in bu servisə bağlana bilməsi və metodlarını çağıra bilməsi üçün Binder sinfi
    inner class LocalBinder : Binder() {
        // Servisin öz obyektini geri qaytarır
        fun getService(): MusicService = this@MusicService
    }

    // Binder obyektini yaradırıq
    private val binder = LocalBinder()

    // Arxa fonda işləyən tapşırıqları (məsələn, vaxt yenilənməsi) idarə etmək üçün Coroutine Scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null // Mahnı saniyəsini izləyən dövri tapşırıq

    // Servisin vəziyyətini (isPlaying, position və s.) saxlayan və UI-a ötürən axın (Flow)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null // Musiqini oxudan əsas obyekt
    private var isPrepared = false // Mahnı tam yüklənib çalınmağa hazırdırmı?
    private var playWhenPrepared = false // Hazır olan kimi çalınmağa başlasınmı?
    private var currentIndex = 0 // Siyahıdakı hazırkı mahnının indeksi
    private var isForeground = false // Servis ön planda (bildirişlə) işləyirmi?

    private lateinit var audioManager: AudioManager // Səs fokusunu (zəng gələndə səsi kəsmək və s.) idarə edir
    private lateinit var notificationManager: NotificationManager // Bildirişləri sistemə göstərir
    private var focusRequest: AudioFocusRequest? = null // Səs fokusu tələbi üçün obyekt

    // Səs atributları - sistemə bu səsin Musiqi məqsədli olduğunu bildiririk
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    /**
     * Servis ilk dəfə yaradılanda (onCreate) sistem servislərini hazırlayırıq.
     */
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel() // Android 8.0+ üçün bildiriş kanalı yaradırıq
        loadSong(0, playWhenReady = false) // İlk mahnını siyahıdan hazırlayırıq
    }

    /**
     * ViewModel servisə bağlanmaq (bind) istədikdə binder-i geri qaytarırıq.
     */
    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Servisə kənardan (bildiriş düymələri və s.) gələn komandaları idarə edir.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAsForeground() // Servisi aktiv vəziyyətə gətir
            ACTION_TOGGLE -> togglePlayPause()  // Musiqini başlat/dayandır
            ACTION_NEXT -> next()               // Növbəti mahnı
            ACTION_PREV -> previous()           // Əvvəlki mahnı
            ACTION_STOP -> stopPlayback()       // Tam dayandır və servisi bağla
        }
        return START_NOT_STICKY // Servis öldürülsə, öz-özünə yenidən başlamasın
    }

    /**
     * Servis tam bağlandıqda resursları azad edirik.
     */
    override fun onDestroy() {
        stopTicker()
        scope.cancel() // Coroutine-ləri dayandır
        player?.release() // MediaPlayer resurslarını sistemə qaytar
        player = null
        super.onDestroy()
    }

    /**
     * Servisin Android tərəfindən öldürülməməsi üçün onu "Ön Plan"a (Foreground) keçiririk.
     */
    private fun ensureForeground() {
        if (isForeground) return
        isForeground = true
        ContextCompat.startForegroundService(
            this,
            Intent(this, MusicService::class.java).setAction(ACTION_START)
        )
    }

    /**
     * Bildirişi göstərir və servisi Foreground rejiminə keçirir.
     */
    private fun startAsForeground() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    /**
     * Musiqini səsləndirir.
     */
    fun play() {
        // Əgər yükləmə xətası varsa, yenidən yükləməyə çalış
        if (_state.value.hasError) {
            loadSong(currentIndex, playWhenReady = true)
            return
        }
        ensureForeground() // Servisin aktiv qalmasını təmin et

        val p = player
        if (p == null || !isPrepared) {
            // Mahnı hələ hazır deyilsə, hazır olan kimi başlaması üçün qeyd edirik
            playWhenPrepared = true
            _state.update { it.copy(isBuffering = true) }
            refreshNotification()
            return
        }
        
        // Səs fokusunu (başqa tətbiqin səsini kəsmək üçün) tələb edirik
        if (!requestAudioFocus()) return

        p.start() // Səsləndirməni başlat
        _state.update { it.copy(isPlaying = true, isBuffering = false) }
        startTicker() // UI-da vaxtın yenilənməsi üçün ticker-i başlat
        refreshNotification() // Bildiriş ikonunu yenilə
    }

    /**
     * Musiqini müvəqqəti dayandırır (Pause).
     */
    fun pause() {
        playWhenPrepared = false
        player?.takeIf { isPrepared && it.isPlaying }?.pause()
        stopTicker() // Vaxtın sayılmasını dayandır
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
        refreshNotification()
    }

    /**
     * Çalırsa dayandırır, dayanıbsa çalır.
     */
    fun togglePlayPause() {
        if (_state.value.isPlaying || playWhenPrepared) pause() else play()
    }

    /**
     * Növbəti mahnını yükləyir.
     */
    fun next() {
        loadSong((currentIndex + 1) % Playlist.songs.size, playWhenReady = true)
    }

    /**
     * Əvvəlki mahnını yükləyir.
     */
    fun previous() {
        val position = if (isPrepared) (player?.currentPosition ?: 0) else 0
        // Əgər mahnı 3 saniyədən çox çalınıbsa, əvvəlinə qaytar, yoxsa əvvəlki mahnıya keç
        if (position > 3000) {
            seekTo(0)
        } else {
            val prev = (currentIndex - 1 + Playlist.songs.size) % Playlist.songs.size
            loadSong(prev, playWhenReady = true)
        }
    }

    /**
     * Mahnını müəyyən saniyəyə aparır (Seek).
     */
    fun seekTo(positionMs: Long) {
        if (!isPrepared) return
        player?.seekTo(positionMs.toInt())
        _state.update { it.copy(positionMs = positionMs) }
    }

    /**
     * Verilmiş indeksdəki mahnını MediaPlayer-ə yükləyir.
     */
    private fun loadSong(index: Int, playWhenReady: Boolean) {
        currentIndex = index
        val song = Playlist.songs[index]

        stopTicker()
        player?.release() // Köhnə MediaPlayer-i təmizləyirik
        isPrepared = false
        playWhenPrepared = playWhenReady
        _state.value = PlaybackState(song = song, isBuffering = playWhenReady)

        if (playWhenReady) ensureForeground()

        player = MediaPlayer().apply {
            // Telefonun yuxu rejiminə keçməsinin qarşısını alırıq (musiqi dayanmasın deyə)
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(audioAttributes)

            // Mahnı tam hazır olduqda çağırılır
            setOnPreparedListener { mp ->
                isPrepared = true
                _state.update { it.copy(durationMs = mp.duration.toLong()) }
                if (playWhenPrepared) {
                    playWhenPrepared = false
                    play()
                }
            }
            // Mahnı bitəndə avtomatik növbəti mahnını çal
            setOnCompletionListener { next() }
            // Yükləmə zamanı texniki xəta baş verərsə
            setOnErrorListener { _, _, _ ->
                handleError()
                true
            }

            try {
                setDataSource(song.url) // Mahnının internet linkini veririk
                prepareAsync() // Mahnını arxa fonda (UI dondurmadan) hazırlayırıq
            } catch (e: IOException) {
                handleError()
            }
        }
        refreshNotification()
    }

    /**
     * Xəta baş verdikdə vəziyyəti yeniləyən funksiya.
     */
    private fun handleError() {
        isPrepared = false
        playWhenPrepared = false
        stopTicker()
        _state.update { it.copy(isPlaying = false, isBuffering = false, hasError = true) }
        refreshNotification()
    }

    /**
     * Musiqini tam dayandırır, bildirişi silir və servisi bağlayır.
     */
    private fun stopPlayback() {
        pause()
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Mahnının gedişatını (position) hər 250ms-dən bir yeniləyən Coroutine.
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val p = player
                if (p != null && isPrepared) {
                    _state.update { it.copy(positionMs = p.currentPosition.toLong()) }
                }
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Səs fokusu dəyişəndə (zəng gələndə və s.) nə baş verməli olduğunu təyin edirik.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause() // Səsi kəs
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.2f, 0.2f) // Səsi azalt
            AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f, 1f) // Səsi bərpa et
        }
    }

    /**
     * Sistemdən səs fokusunu (icazəsini) tələb edirik.
     */
    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Android 8.0+ üçün bildiriş kanalını yaradırıq.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Çalan mahnı haqqında məlumat və idarəetmə" }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Bildiriş panelini (Notification) yeni məlumatlarla yeniləyir.
     */
    private fun refreshNotification() {
        if (isForeground) notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Bildiriş düymələri üçün klik komandaları (Action Intent) yaradır.
     */
    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Musiqi idarəetmə bildirişini (Layout) vizual olaraq qurur.
     */
    private fun buildNotification(): Notification {
        val s = _state.value

        // Bildirişin özünə basdıqda tətbiqi ön plana gətirir
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (s.isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note) // Bildiriş ikonu
            .setContentTitle(s.song.title) // Mahnı adı
            .setContentText(if (s.hasError) "Mahnı yüklənmədi" else s.song.artist) // İfaçı və ya xəta
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(s.isPlaying) // Mahnı çalınırsa bildirişi silmək olmaz
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_prev, "Previous", actionIntent(ACTION_PREV, 1)) // Geri düyməsi
            .addAction(playPauseIcon, playPauseTitle, actionIntent(ACTION_TOGGLE, 2)) // Play/Pause düyməsi
            .addAction(R.drawable.ic_next, "Next", actionIntent(ACTION_NEXT, 3)) // İrəli düyməsi
            .addAction(R.drawable.ic_close, "Close", actionIntent(ACTION_STOP, 4)) // Bağla düyməsi
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1, 2)) // Bildirişin yığcam formasında ilk 3 düyməni göstər
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1001

        // Komanda Action-ları
        const val ACTION_START = "com.example.musicplayer.START"
        const val ACTION_TOGGLE = "com.example.musicplayer.TOGGLE"
        const val ACTION_NEXT = "com.example.musicplayer.NEXT"
        const val ACTION_PREV = "com.example.musicplayer.PREV"
        const val ACTION_STOP = "com.example.musicplayer.STOP"
    }
}
