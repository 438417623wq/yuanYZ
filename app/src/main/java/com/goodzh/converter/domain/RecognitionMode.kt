package com.goodzh.converter.domain

enum class SpeechLanguage(val label: String, val modelCode: String) {
    Mandarin("普通话", "zh"),
    English("英文", "en"),
    Japanese("日语", "ja"),
    Korean("韩语", "ko"),
    Cantonese("粤语", "yue"),
    OtherDialect("其它方言", "auto")
}

enum class PerformanceMode(val label: String, val description: String) {
    LowMemory("省内存", "低内存手机优先，速度慢一点"),
    Balanced("均衡", "默认推荐，稳定和速度折中"),
    HighPerformance("高性能", "新手机优先，速度更快但更占内存")
}

data class PerformanceProfile(
    val speechThreads: Int,
    val speechChunkSeconds: Int,
    val ocrMaxImageSide: Int,
    val ocrTileHeight: Int
)

val PerformanceMode.profile: PerformanceProfile
    get() = when (this) {
        PerformanceMode.LowMemory -> PerformanceProfile(
            speechThreads = 1,
            speechChunkSeconds = 15,
            ocrMaxImageSide = 1600,
            ocrTileHeight = 1200
        )
        PerformanceMode.Balanced -> PerformanceProfile(
            speechThreads = 2,
            speechChunkSeconds = 20,
            ocrMaxImageSide = 2048,
            ocrTileHeight = 1600
        )
        PerformanceMode.HighPerformance -> PerformanceProfile(
            speechThreads = 4,
            speechChunkSeconds = 25,
            ocrMaxImageSide = 2560,
            ocrTileHeight = 2200
        )
    }
