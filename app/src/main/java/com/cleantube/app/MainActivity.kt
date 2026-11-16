package com.cleantube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.cleantube.app.ui.AppRoot
import com.cleantube.app.ui.VyroTheme
import com.cleantube.app.ui.MainVM

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CleanTubeTheme { AppRoot(vm) } }
    }
}
