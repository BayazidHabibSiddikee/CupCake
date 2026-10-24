package com.cupcake.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Global event bus for asynchronous chat messages (e.g. from STT or BLE). */
object MessagingBus {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val messages = _messages.asSharedFlow()

    suspend fun sendUserMessage(text: String) {
        _messages.emit(text)
    }
}
