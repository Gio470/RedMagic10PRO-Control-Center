package com.elitedarkkaiser.redmagic.gametrigger

import java.util.concurrent.Executor

/** Coalesces window events and lets an in-flight vendor apply notice that its game has left. */
class LatestMappingQueue<T>(private val executor: Executor,
    private val apply: (T, () -> Boolean) -> Unit, private val clear: () -> Unit) {
    private val lock = Any()
    private var desired: T? = null
    private var revision = 0L
    private var scheduled = false

    fun requested(): Boolean = synchronized(lock) { desired != null }

    fun submit(value: T?, force: Boolean = false) {
        val start = synchronized(lock) {
            if (!force && desired == value) return
            desired = value
            revision++
            if (scheduled) false else { scheduled = true; true }
        }
        if (start) executor.execute { drain() }
    }

    private fun drain() {
        while (true) {
            val (value, token) = synchronized(lock) { desired to revision }
            if (value == null) clear() else apply(value) { synchronized(lock) { revision == token } }
            synchronized(lock) {
                if (revision == token) { scheduled = false; return }
            }
        }
    }
}
