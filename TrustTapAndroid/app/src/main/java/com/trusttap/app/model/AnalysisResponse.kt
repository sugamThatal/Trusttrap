package com.trusttap.app.model

/**
 * Mirrors the JSON shape returned by POST /analyze-image and
 * POST /analyze-video on the FastAPI backend:
 *
 * {
 *   "trust_score": 80,
 *   "risk": "Low",
 *   "reason": ["Metadata missing"],
 *   "accessible_description": "Image shows: a group of people holding signs at a protest",
 *   "evidence": [ ... ],
 *   "frames_analyzed": 6   // only present on video results
 * }
 *
 * `evidence` and `frames_analyzed` are nullable with defaults so this
 * still deserializes fine against older backend responses that don't
 * include them.
 */
data class AnalysisResponse(
    val trust_score: Int,
    val risk: String,
    val reason: List<String>,
    val accessible_description: String,
    val evidence: List<Evidence>? = null,
    val frames_analyzed: Int? = null,
    val extracted_text: String? = null,
    val url_findings: List<UrlFinding>? = null,
    val next_actions: List<String>? = null,
    val follow_up_prompts: List<String>? = null,
    val limitations: List<String>? = null,
    val analysis_method: String? = null,
    val input_type: String? = null,
    val ocr_available: Boolean? = null,
    val text_model_available: Boolean? = null,
    val content_available: Boolean? = null,
    val content_message: String? = null
)

/**
 * One piece of corroborating/refuting evidence. The backend returns two
 * different shapes under the same list depending on `type`:
 *   - "fact_check": claim, rating, publisher, url
 *   - "reverse_image": url, page_title
 * All fields besides `type` are nullable so either shape deserializes
 * cleanly into this one class.
 */
data class Evidence(
    val type: String,
    val claim: String? = null,
    val rating: String? = null,
    val publisher: String? = null,
    val url: String? = null,
    val page_title: String? = null
)

data class UrlFinding(
    val url: String,
    val host: String? = null,
    val risk: String? = null,
    val signals: List<String>? = null,
    val recommended_action: String? = null
)
