package com.intraprenuers.noticepilot.extract

class ExtractionException(
    val kind: Kind,
    val detail: String,
    cause: Throwable? = null
) : Exception(detail, cause) {

    enum class Kind { MISSING_KEY, NO_INTERNET, TIMEOUT, RATE_LIMIT, BAD_KEY, SERVER, HTTP, BLOCKED, EMPTY, BAD_JSON }

    val userMessage: String
        get() = when (kind) {
            Kind.MISSING_KEY -> "Gemini API key is missing. Add GEMINI_API_KEY to local.properties and rebuild the app."
            Kind.NO_INTERNET -> "No internet connection. Connect and try again."
            Kind.TIMEOUT -> "Gemini took too long to answer. Please try again."
            Kind.RATE_LIMIT -> "Too many requests to Gemini right now. Wait a minute and try again."
            Kind.SERVER -> "Gemini is busy right now (all backup models too). Wait a moment and tap Try again."
            Kind.BAD_KEY -> "Gemini rejected the API key. Check GEMINI_API_KEY in local.properties."
            else -> "Couldn't read this notice. Please try again."
        }
}
