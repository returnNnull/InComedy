package com.bam.incomedy.domain.auth

/**
 * Поддерживаемые типы способов входа во внутреннюю InComedy-сессию.
 *
 * Enum живет в domain-слое, потому что им пользуются и presentation, и data,
 * и он описывает бизнес-термины auth surface, а не транспортную реализацию.
 */
enum class AuthProviderType {
    PASSWORD,
    PHONE,
    VK,
    TELEGRAM,
    GOOGLE,
}
