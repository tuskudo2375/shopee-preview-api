package com.titus.aicompose

data class NormalizedRect(val x: Float, val y: Float, val width: Float, val height: Float)
data class NormalizedPoint(val x: Float, val y: Float)
data class Movement(val horizontal: Float, val vertical: Float, val rotation: Float)
data class CompositionResult(
    val composition: NormalizedRect,
    val subject: NormalizedPoint,
    val recommendedZoom: Float,
    val movement: Movement,
    val compositionType: String,
    val confidence: Float,
    val instruction: String,
    val reason: String
)
