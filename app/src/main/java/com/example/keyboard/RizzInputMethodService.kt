package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsRepository
import com.example.repository.RizzRepository

class RizzInputMethodService : InputMethodService() {

    private lateinit var composeView: ComposeKeyboardView
    private lateinit var repository: RizzRepository
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        repository = RizzRepository(database.messageDao(), settingsRepository)
    }

    override fun onCreateInputView(): View {
        composeView = ComposeKeyboardView(this)
        
        composeView.content = {
            KeyboardScreen(
                repository = repository,
                settingsRepository = settingsRepository,
                onKeyPress = { text ->
                    val ic = currentInputConnection
                    ic?.commitText(text, 1)
                },
                onBackspace = {
                    val ic = currentInputConnection
                    ic?.deleteSurroundingText(1, 0)
                },
                onInsertText = { text ->
                    val ic = currentInputConnection
                    ic?.commitText(text, 1)
                }
            )
        }
        
        return composeView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Reset keyboard state if necessary
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::composeView.isInitialized) {
            composeView.dispose()
        }
    }
}
