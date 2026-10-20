package ai.opencode.mobile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle of the engine inside the app process. */
sealed interface EngineStatus {
    data object Idle : EngineStatus
    data object Starting : EngineStatus
    data class Ready(val port: Int) : EngineStatus
    data class Failed(val message: String) : EngineStatus
}

/** Tiny bus between EngineService and MainActivity (scaffold-quality by design). */
object EngineStateBus {
    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    fun set(value: EngineStatus) {
        _status.value = value
    }
}
