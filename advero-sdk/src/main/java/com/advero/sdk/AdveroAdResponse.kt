package com.advero.sdk

/**
 * Parsed response from GET /ads/serve/{placementCode}.
 * `impressionToken` and `viewUrl` are null for the house placeholder response.
 * `viewRatio` / `viewMs` are the viewability thresholds chosen by the server.
 */
internal data class AdveroAdResponse(
    val creativeUrl: String,
    val clickUrl: String,
    val impressionToken: String?,
    val placeholder: Boolean,
    val viewUrl: String?,
    val viewRatio: Double,
    val viewMs: Long
)
