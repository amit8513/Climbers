package com.example.climb.analysis

enum class AnalysisStatus {
    QUEUED,
    PREPARING,
    EXTRACTING_FRAMES,
    ESTIMATING_POSE,
    CALCULATING_METRICS,
    GENERATING_TIPS,
    SAVING,
    COMPLETE,
    FAILED,
    CANCELLED,
}
