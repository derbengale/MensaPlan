package com.watchyourself.mensaplan.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.watchyourself.mensaplan.presentation.theme.MensaPlanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MensaPlanTheme {
                MensaApp()
            }
        }
    }
}
