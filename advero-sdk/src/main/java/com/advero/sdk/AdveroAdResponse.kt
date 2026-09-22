package com.advero.sdk

/**
 * Parsed response from GET /ads/serve/{placementCode}.
 * `impressionToken` is null for the house placeholder response.
 */
internal data class AdveroAdResponse(
    val creativeUrl: String,
    val clickUrl: String,
    val impressionToken: String?,
    val placeholder: Boolean
)
