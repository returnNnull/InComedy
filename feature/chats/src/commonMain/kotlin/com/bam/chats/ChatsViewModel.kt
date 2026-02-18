package com.bam.chats

import com.bam.incomedy.data.chats.ChatsRepository

class ChatsViewModel {
    private val repository = ChatsRepository()

    fun getContent(): String = repository.getStatus()
}
