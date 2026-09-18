package com.omni.image

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.omni.image.ui.MainScreen
import com.omni.image.ui.theme.OmniImageStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniImageStudioTheme {
                MainScreen()
            }
        }
    }
}
