package com.cadence.fit.domain.core

import java.util.UUID

/** Injected everywhere so tests can control time. */
fun interface Clock {
    fun nowMs(): Long
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

/** A clock frozen at a fixed instant, advanced manually by tests. */
class FixedClock(private var currentMs: Long) : Clock {
    override fun nowMs(): Long = currentMs

    fun advanceBy(millis: Long) {
        currentMs += millis
    }

    fun setTo(millis: Long) {
        currentMs = millis
    }
}

fun interface IdGenerator {
    fun next(): String
}

object UuidGenerator : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}

/** Deterministic generator for tests. */
class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0

    override fun next(): String {
        counter += 1
        return "$prefix-$counter"
    }
}
