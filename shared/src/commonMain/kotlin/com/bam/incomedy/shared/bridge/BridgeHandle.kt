package com.bam.incomedy.shared.bridge

import kotlinx.coroutines.Job

interface BridgeHandle {
    fun dispose()
}

class CompositeBridgeHandle(
    private val handles: List<BridgeHandle>,
) : BridgeHandle {
    override fun dispose() {
        handles.forEach { it.dispose() }
    }
}

internal class JobBridgeHandle(
    private val job: Job,
) : BridgeHandle {
    override fun dispose() {
        job.cancel()
    }
}
