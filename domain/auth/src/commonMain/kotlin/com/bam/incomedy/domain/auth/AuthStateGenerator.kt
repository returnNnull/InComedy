package com.bam.incomedy.domain.auth

import kotlin.random.Random

/**
 * Контракт генерации `state` для auth-потоков.
 *
 * Интерфейс позволяет тестам подменять недетерминированную генерацию и
 * удерживает детали random/state creation вне orchestration-логики.
 */
interface AuthStateGenerator {
    /** Возвращает новое значение `state` для очередной auth-попытки. */
    fun next(): String
}

/**
 * Базовая random-реализация генератора auth-state.
 */
class RandomAuthStateGenerator : AuthStateGenerator {
    /** Возвращает псевдослучайный state для защиты auth flow. */
    override fun next(): String = Random.nextLong().toString()
}
