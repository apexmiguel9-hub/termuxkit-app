package com.termux.alpa_termuxkit.ui

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.termux.alpa_termuxkit.presentation.viewmodel.TerminalViewModel
import com.termux.alpa_termuxkit.ui.component.TerminalScreen
import com.termux.shared.jni.TerminalManager

private const val TAG = "MainActivity"

/**
 * MainActivity - Activity principal de Alpa TermuxKit
 *
 * SOFT_INPUT_STATE_ALWAYS_VISIBLE - Android sabe que esta app vive de escribir
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TerminalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("TERMINAL_DEBUG", "MainActivity onCreate - iniciando sesión")
        Log.i(TAG, "=========================================")
        Log.i(TAG, "🚀 MainActivity.onCreate() llamado")
        Log.i(TAG, "=========================================")
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Registrar contexto para bootstrap
        TerminalManager.setContext(this)

        // TOQUE FINAL - Forzar teclado siempre visible
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        Log.i(TAG, "⌨️ Teclado forzado visible")

        setContent {
            Log.i(TAG, "🎨 setContent() llamado - renderizando TerminalScreen")
            TerminalScreen(
                viewModel = viewModel,
                onKeyboardRequest = { 
                    Log.i(TAG, "🎹 onKeyboardRequest llamado")
                }
            )
        }

        // Iniciar sesión PTY después de un delay
        Log.i(TAG, "⏰ Programando inicio de PTY en 500ms...")
        window.decorView.postDelayed({
            Log.i(TAG, "🔥 Iniciando sesión PTY AHORA...")
            try {
                viewModel.startSession()
                Log.i(TAG, "✅ PTY iniciado exitosamente")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error iniciando PTY", e)
            }
        }, 500)

        Log.i(TAG, "✅ MainActivity.onCreate() completado")
    }

    override fun onDestroy() {
        Log.i(TAG, "🛑 MainActivity.onDestroy() llamado")
        super.onDestroy()
        viewModel.stopSession()
    }
}
