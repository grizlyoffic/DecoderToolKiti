package com.nexbytes.h7skertool.model

data class CapturedResponse(
    val requestId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val statusCode: Int,
    val statusMessage: String,
    val endpoint: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val bodyText: String?,
    val bodyHex: String?,
    val durationMs: Long
) {
    fun headersAsString(): String =
        headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return requestId == (other as CapturedResponse).requestId
    }
    override fun hashCode(): Int = requestId.hashCode()
}
