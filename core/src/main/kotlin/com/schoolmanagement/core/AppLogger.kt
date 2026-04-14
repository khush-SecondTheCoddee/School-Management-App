package com.schoolmanagement.core

object AppLogger {
    fun info(tag: String, message: String) {
        println("INFO [$tag]: $message")
    }
}
