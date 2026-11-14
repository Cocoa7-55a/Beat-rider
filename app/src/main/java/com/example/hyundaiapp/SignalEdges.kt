package com.example.hyundaiapp

/** A subscription's first value is a baseline, never a button press. */
class SignalEdges<T : Any> {
    var previous: T? = null
        private set
    fun accept(value: T?): Boolean {
        val old = previous
        previous = value
        return value != null && old != null && old != value
    }
    fun reset() { previous = null }
}

/** Position callbacks arrive throughout a movement. Emit the first change after a quiet interval. */
internal class WindowMovement {
    private val positions = SignalEdges<Int>()
    private var lastChangeAt: Long? = null

    fun accept(position: Int?, at: Long): Boolean {
        if (position == null) {
            reset()
            return false
        }
        if (!positions.accept(position)) return false
        val previous = lastChangeAt
        lastChangeAt = at
        return previous == null || at - previous >= 220L
    }

    fun reset() {
        positions.reset()
        lastChangeAt = null
    }
}
