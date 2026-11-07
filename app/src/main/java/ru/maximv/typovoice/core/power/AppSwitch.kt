package ru.maximv.typovoice.core.power

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Master on/off switch. Off means: no dictation can start, any running one is cancelled,
 * the bubble disappears, the tile is unavailable and the accessibility service stops
 * receiving events. The service itself stays enabled in system settings (only the user can
 * change that), but it is completely idle.
 */
class AppSwitch {
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val isEnabled get() = _enabled.value

    fun apply(enabled: Boolean) {
        _enabled.value = enabled
    }
}
