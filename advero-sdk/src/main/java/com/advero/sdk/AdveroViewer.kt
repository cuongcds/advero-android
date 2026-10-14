package com.advero.sdk

/**
 * Optional signal about the person using the app, signed by the publisher's
 * SERVER — never sign it in the app, the property's signal secret must not
 * ship inside it.
 *
 * token = HMAC_SHA256_hex("<placementCode>|<age>|<gender>|<expires>", signalSecret),
 * with age/gender as empty strings when omitted. [expires] is a unix timestamp
 * (seconds) at most one hour ahead. An invalid or expired signal is ignored by
 * the server.
 */
data class AdveroViewer @JvmOverloads constructor(
    val expires: Long,
    val token: String,
    val age: Int? = null,
    val gender: String? = null
)
