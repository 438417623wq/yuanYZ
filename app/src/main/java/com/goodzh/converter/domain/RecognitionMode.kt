package com.goodzh.converter.domain

enum class RecognitionMode(val label: String) {
    MandarinAccurate("普通话精准"),
    DialectEnhanced("方言增强"),
    FastFallback("极速备用")
}
