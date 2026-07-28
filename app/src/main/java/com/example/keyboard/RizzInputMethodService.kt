package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsRepository
import com.example.repository.RizzRepository

class RizzInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    private var composeView: ComposeView? = null
    private lateinit var repository: RizzRepository
    private lateinit var settingsRepository: SettingsRepository

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate()

        try {
            val database = AppDatabase.getDatabase(this)
            settingsRepository = SettingsRepository(this)
            repository = RizzRepository(database.messageDao(), settingsRepository)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateInputView(): View {
        val windowView = window?.window?.decorView
        if (windowView != null) {
            windowView.setViewTreeLifecycleOwner(this)
            windowView.setViewTreeViewModelStoreOwner(this)
            windowView.setViewTreeSavedStateRegistryOwner(this)
        }

        val view = ComposeView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setViewTreeLifecycleOwner(this@RizzInputMethodService)
            setViewTreeViewModelStoreOwner(this@RizzInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@RizzInputMethodService)

            setContent {
                if (::repository.isInitialized && ::settingsRepository.isInitialized) {
                    KeyboardComposeView(
                        repository = repository,
                        settingsRepository = settingsRepository,
                        onKeyPress = { text ->
                            val ic = currentInputConnection ?: return@KeyboardComposeView
                            ic.commitText(text, 1)
                        },
                        onBackspace = {
                            val ic = currentInputConnection ?: return@KeyboardComposeView
                            val selectedText = ic.getSelectedText(0)
                            if (selectedText.isNullOrEmpty()) {
                                ic.deleteSurroundingText(1, 0)
                            } else {
                                ic.commitText("", 1)
                            }
                        },
                        onInsertText = { text ->
                            val ic = currentInputConnection ?: return@KeyboardComposeView
                            ic.commitText(text, 1)
                        },
                        onSwitchKeyboard = {
                            try {
                                switchInputMethod("com.android.inputmethod.latin/.LatinIME")
                            } catch (e: Exception) {
                                try {
                                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                    imm.showInputMethodPicker()
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                    )
                }
            }
        }
        composeView = view
        return view
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        composeView = null
        super.onDestroy()
    }
}

