package com.cleantube.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.cleantube.app.ui.AppRoot
import com.cleantube.app.ui.VyroTheme
import com.cleantube.app.ui.MainVM
import com.cleantube.app.player.PlaybackService

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VyroTheme { AppRoot(vm) } }
    }

    /** Back/exit ile Activity gercekten kapatilirsa medya servisini de kapat. */
    override fun onDestroy() {
        if (isFinishing) {
            runCatching { stopService(Intent(this, PlaybackService::class.java)) }
        }
        super.onDestroy()
    }
}
