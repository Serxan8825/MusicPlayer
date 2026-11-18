package com.example.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.musicplayer.ui.MusicPlayerTheme
import com.example.musicplayer.ui.PlayerScreen

/**
 * MainActivity - Tətbiqin başladığı əsas aktivlik (Activity).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ekranın tam ölçüdə (status bar və naviqasiya paneli daxil) görünməsini aktiv edir
        enableEdgeToEdge()
        
        // Tətbiqin vizual məzmununu təyin edirik
        setContent {
            // Tətbiqin əsas mövzusunu tətbiq edirik
            MusicPlayerTheme {
                // Ekranı tam örtən əsas konteyner
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pleyer ekranını (UI) çağırırıq
                    PlayerScreen()
                }
            }
        }
    }
}
