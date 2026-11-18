package com.example.musicplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.R
import com.example.musicplayer.viewmodel.PlayerUiState
import com.example.musicplayer.viewmodel.PlayerViewModel

/**
 * PlayerScreen - Tətbiqin əsas ekranı. ViewModel-dən vəziyyəti alır və UI-a ötürür.
 */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel = viewModel()) {
    // ViewModel-dəki UI vəziyyətini (state) izləyirik. Hər hansı dəyişiklik olanda ekran yenidən çəkilir.
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Əsas vizual məzmunu çağıran funksiya
    PlayerContent(
        state = state,
        onPlayPause = viewModel::onPlayPauseClick, // Play/Pause düyməsinə klik məntiqi
        onNext = viewModel::onNextClick,           // Növbəti düyməsi
        onPrevious = viewModel::onPreviousClick,   // Əvvəlki düyməsi
        onSeekChange = viewModel::onSeekChange,     // Seekbar sürüşdürüləndə
        onSeekFinished = viewModel::onSeekFinished  // Sürüşdürmə bitəndə
    )
}

/**
 * PlayerContent - Ekranın strukturunu (Layout) təyin edir.
 */
@Composable
private fun PlayerContent(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    // Mahnının örtük rənglərini alırıq, yoxdursa standart rənglər istifadə edirik
    val coverColors = state.song?.coverColors?.map { Color(it) }
        ?: listOf(Color(0xFF1E3C72), Color(0xFFFF7E5F))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2A41), Color(0xFF324A5F)))) // Arxa fon rəngi (Gradient)
            .statusBarsPadding()    // Status barın (saat olan hissə) altında qalmamaq üçün
            .navigationBarsPadding() // Aşağı naviqasiya panelinin altında qalmamaq üçün
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "NOW PLAYING",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.weight(1f)) // Elementlər arasında boşluq qoyur (elastik)
        AlbumArt(colors = coverColors, isPlaying = state.isPlaying) // Mahnı ikonu və animasiyası
        Spacer(Modifier.weight(1f))

        // Mahnı adı və ifaçı məlumatı kartı
        SongInfoCard(
            title = state.song?.title.orEmpty(),
            artist = if (state.hasError) "Mahnı yüklənmədi. İnterneti yoxlayın." else state.song?.artist.orEmpty()
        )

        Spacer(Modifier.height(24.dp))
        // Vaxt çubuğu (Seekbar)
        SeekBar(
            state = state,
            onSeekChange = onSeekChange,
            onSeekFinished = onSeekFinished
        )

        Spacer(Modifier.height(24.dp))
        // İdarəetmə düymələri (Play, Pause, Next, Prev)
        Controls(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            enabled = state.isConnected,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * AlbumArt - Mahnı oxuyanda fırlanan dairəvi vizual hissə.
 */
@Composable
private fun AlbumArt(colors: List<Color>, isPlaying: Boolean) {
    val rotation = remember { Animatable(0f) }
    
    // Musiqi oxuyanda fırlanma animasiyasını başladırıq
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(tween(15_000, easing = LinearEasing))
            )
        }
    }

    Box(
        modifier = Modifier
            .size(260.dp)
            .graphicsLayer { rotationZ = rotation.value } // Fırlanma effektini tətbiq edir
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(96.dp)
        )
    }
}

/**
 * SongInfoCard - Mahnı və ifaçı adını göstərən kart.
 */
@Composable
private fun SongInfoCard(title: String, artist: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

/**
 * SeekBar - Mahnının gedişatını göstərən və dəyişdirən çubuq.
 */
@Composable
private fun SeekBar(
    state: PlayerUiState,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    Column {
        Slider(
            value = state.progress, // 0.0 ilə 1.0 arası dəyər
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            enabled = state.isConnected && !state.isBuffering,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF9FA8DA),
                activeTrackColor = Color(0xFF5C6BC0),
                inactiveTrackColor = Color.White
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Hazırkı dəqiqə və ümumi müddət
            Text(state.positionText, color = Color.White, fontSize = 13.sp)
            Text(state.durationText, color = Color.White, fontSize = 13.sp)
        }
    }
}

/**
 * Controls - Əsas idarəetmə düymələri.
 */
@Composable
private fun Controls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Əvvəlki mahnı düyməsi
        IconButton(onClick = onPrevious, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_prev),
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Play/Pause düyməsi dairəvi fonda
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
                .clickable(enabled = enabled, onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) {
                // Mahnı yüklənirsə loading göstəririk
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                // Oxuyursa Pause, dayanıbsa Play ikonunu göstəririk
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Növbəti mahnı düyməsi
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
