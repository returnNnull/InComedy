package com.bam.incomedy

import com.bam.chats.ChatsViewModel

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return ChatsViewModel().getContent()
    }
}