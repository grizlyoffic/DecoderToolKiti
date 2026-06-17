package com.nexbytes.h7skertool.model

import java.util.UUID

enum class LogLevel { INFO, WARNING, ERROR, DEBUG }

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String
)
