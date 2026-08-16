package com.trusttap.app.model

data class CapabilitiesResponse(
    val ocr: Capability = Capability(),
    val text_model: Capability = Capability(),
    val tee: Capability = Capability(),
    val url_inspection: Capability = Capability(),
    val privacy: List<String> = emptyList()
)

data class Capability(
    val available: Boolean = false,
    val message: String = ""
)
