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
