package com.bam.incomedy.shared.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

open class BaseFeatureBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    protected fun <T, R> observeState(
        stateFlow: StateFlow<T>,
        mapper: (T) -> R,
        onState: (R) -> Unit,
    ): BridgeHandle {
        onState(mapper(stateFlow.value))
        val job = scope.launch {
            stateFlow.collect { state ->
                onState(mapper(state))
            }
        }
        return JobBridgeHandle(job)
    }

    protected fun <T> observeEffect(
        effectFlow: Flow<T>,
        onEffect: (T) -> Unit,
    ): BridgeHandle {
        val job = scope.launch {
            effectFlow.collect { effect ->
                onEffect(effect)
            }
        }
        return JobBridgeHandle(job)
    }

    open fun dispose() {
        scope.cancel()
    }
}
