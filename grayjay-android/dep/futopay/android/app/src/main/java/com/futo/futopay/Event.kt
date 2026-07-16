package com.futo.futopay

class Event1<T> {
    private val listeners = mutableMapOf<Any, (T) -> Unit>()

    fun subscribe(tag: Any, listener: (T) -> Unit) {
        synchronized(listeners) {
            listeners[tag] = listener
        }
    }

    fun remove(tag: Any) {
        synchronized(listeners) {
            listeners.remove(tag)
        }
    }

    fun emit(eventData: T) {
        synchronized(listeners) {
            for (pair in listeners) {
                pair.value(eventData)
            }
        }
    }
}