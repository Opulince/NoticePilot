package com.intraprenuers.noticepilot.extract

object GeminiConfig {
    /** Primary model. */
    const val MODEL_NAME = "gemini-3.6-flash"

    /**
     * Tried in order. If a model is overloaded (503), rate-limited (429), times out, is unavailable (404)
     * or returns unusable output, the next one is tried. All are current Flash models with structured output.
     */
    val MODELS: List<String> = listOf(MODEL_NAME, "gemini-3.8-flash", "gemini-3.5-flash-lite")

    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    const val TIMEOUT_SECONDS = 60L
}
