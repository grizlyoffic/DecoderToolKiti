package com.nexbytes.h7skertool.model

import java.util.UUID

data class CapturedRequest(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val url: String,
    val endpoint: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val bodyText: String?,
    val bodyHex: String?
) {
    fun headersAsString(): String =
        headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return id == (other as CapturedRequest).id
    }
    override fun hashCode(): Int = id.hashCode()
}
