package com.bam.incomedy.data.chats

import com.bam.incomedy.core.common.buildPlatformName

class ChatsRepository {
    fun getStatus(): String = "Work on ${buildPlatformName()}"
}
