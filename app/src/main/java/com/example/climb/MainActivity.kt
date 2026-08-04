package com.example.climb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.climb.navigation.ClimbNavHost
import com.example.climb.ui.theme.ClimbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ClimbApplication).container

        setContent {
            ClimbTheme {
                ClimbNavHost(container = container)
            }
        }
    }
}
