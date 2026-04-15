package com.termux.alpa_termuxkit.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.shared.jni.TerminalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TerminalViewModel — Versión con TerminalEmulator.kt
 *
 * - Rust SOLO maneja el PTY (abre, fork/exec shell, lee/escribe bytes)
 * - Los bytes del PTY van a TerminalEmulator.kt via onPtyData()
 * - TerminalEmulator parsea TODOS los escape sequences VTE/ANSI
 * - Compose lee emulator.screen y renderiza
 */
class TerminalViewModel : ViewModel() {

    companion object {
        private const val TAG = "TerminalViewModel"
        const val DEFAULT_COLS = 80
        const val DEFAULT_ROWS = 24
    }

    // Contador de cambios — fuerza redibujado en Compose
    private val _gridState = MutableStateFlow(0)
    val gridState: StateFlow<Int> = _gridState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Inicia sesion PTY + TerminalEmulator.kt
     */
    fun startSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.e("TERMINAL_DEBUG", "ViewModel.startSession() llamando a TerminalManager.startSession()")

                val success = TerminalManager.startSession(null, DEFAULT_COLS, DEFAULT_ROWS)
                _isRunning.value = success

                if (success) {
                    Log.i(TAG, "✅ Sesion activa — TerminalEmulator.kt parsea VTE")
                    Log.e("TERMINAL_DEBUG", "ViewModel: startSession exitoso, iniciando polling de ptyDataVersion")
                    withContext(Dispatchers.Main) { triggerUpdate() }
                    // Polling del contador ptyDataVersion para forzar recomposicion
                    pollPtyDataVersion()
                } else {
                    Log.e(TAG, "❌ Error iniciando sesion")
                    Log.e("TERMINAL_DEBUG", "ViewModel: startSession fallo")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error iniciando sesion", e)
                Log.e("TERMINAL_DEBUG", "ViewModel: startSession EXCEPTION: ${e.message}")
                _isRunning.value = false
            }
        }
    }

    /**
     * Pollea el contador ptyDataVersion de TerminalManager.
     * Cada vez que cambia, fuerza un triggerUpdate() para que Compose redraw.
     */
    private suspend fun pollPtyDataVersion() {
        var lastVersion = TerminalManager.ptyDataVersion
        var pollCount = 0
        while (_isRunning.value) {
            withContext(Dispatchers.Default) {
                kotlinx.coroutines.delay(50) // Más rápido: 50ms
            }
            val currentVersion = TerminalManager.ptyDataVersion
            pollCount++
            if (currentVersion != lastVersion) {
                lastVersion = currentVersion
                Log.e("TERMINAL_DEBUG", "ViewModel: ptyDataVersion cambio a $currentVersion (poll #$pollCount), triggerUpdate")
                withContext(Dispatchers.Main) { triggerUpdate() }
            }
        }
        Log.e("TERMINAL_DEBUG", "ViewModel: pollPtyDataVersion terminado después de $pollCount polls")
    }

    /**
     * Detiene la sesion
     */
    fun stopSession() {
        Log.e("TERMINAL_DEBUG", "ViewModel: stopSession llamado")
        TerminalManager.stopSession()
        _isRunning.value = false
    }

    /**
     * Escribe texto al PTY → shell → respuesta → TerminalEmulator → screen
     */
    fun writeInput(text: String) {
        TerminalManager.write(text)
        triggerUpdate()
    }

    /**
     * Escribe bytes al PTY
     */
    fun writeBytes(bytes: ByteArray) {
        TerminalManager.writeBytes(bytes)
        triggerUpdate()
    }

    /**
     * Limpia la terminal
     */
    fun clear() {
        val emulator = TerminalManager.getEmulator()
        emulator?.reset()
        triggerUpdate()
    }

    /**
     * Notifica al UI que el contenido cambio
     */
    fun triggerUpdate() {
        _gridState.value = (_gridState.value + 1) and 0x7FFFFFFF
    }

    override fun onCleared() {
        super.onCleared()
        TerminalManager.stopSession()
    }
}
