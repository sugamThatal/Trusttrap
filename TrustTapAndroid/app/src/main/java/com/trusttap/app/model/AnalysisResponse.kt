package com.trusttap.app.model

/**
 * Mirrors the JSON shape returned by POST /analyze-image on the FastAPI backend:
 *
 * {
 *   "trust_score": 80,
 *   "risk": "Low",
 *   "reason": ["Metadata missing"],
 *   "accessible_description": "Image shows: a group of people holding signs at a protest"
 * }
 */
data class AnalysisResponse(
    val trust_score: Int,
    val risk: String,
    val reason: List<String>,
    val accessible_description: String
)
