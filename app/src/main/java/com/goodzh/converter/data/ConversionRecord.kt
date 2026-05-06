package com.goodzh.converter.data

enum class ConversionType {
    Video,
    Audio,
    Image
}

enum class ConversionStatus {
    Success,
    Failed,
    Pending
}

data class ConversionRecord(
    val id: Long = System.currentTimeMillis(),
    val type: ConversionType,
    val title: String,
    val sourceUri: String,
    val resultText: String,
    val segmentsJson: String = "",
    val status: ConversionStatus,
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
