package com.cupcake.network

/**
 * Builds the payload broadcast to the ESP board on a face change.
 *
 * The board can either react to the legacy [expression] (its own sprite vocabulary)
 * or fetch [imagePath] to display the exact same art the phone is showing. Keeping
 * this a pure function makes the wire contract unit-testable without a device.
 */
fun buildFacePayload(
    expression: String,
    duration: Int = 2000,
    emotion: String? = null,
    imagePath: String? = null
): Map<String, Any?> {
    val payload = mutableMapOf<String, Any?>(
        "expression" to expression,
        "duration" to duration
    )
    if (!emotion.isNullOrBlank()) payload["emotion"] = emotion
    if (!imagePath.isNullOrBlank()) payload["image"] = imagePath
    return payload
}
