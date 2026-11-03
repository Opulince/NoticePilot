package com.intraprenuers.noticepilot.extract

import com.intraprenuers.noticepilot.extract.ExtractionException.Kind
import com.intraprenuers.noticepilot.verify.SourceLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GeminiExtractor(
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val models: List<String> = GeminiConfig.MODELS
) {

    /**
     * Tries each model in [models] in order and returns the first usable result. Errors that another
     * model can't fix (no internet, bad/missing key) are thrown straight away.
     */
    suspend fun extract(lines: List<SourceLine>): List<ExtractedItem> {
        if (apiKey.isBlank()) throw ExtractionException(Kind.MISSING_KEY, "GEMINI_API_KEY is blank")
        val json = ExtractionPrompt.buildRequestBody(lines).toString()
        var last: ExtractionException? = null
        for (model in models) {
            try {
                val responseText = withContext(Dispatchers.IO) { call(request(model, json)) }
                return ExtractionParser.parse(responseText)
            } catch (e: ExtractionException) {
                if (e.kind !in FALLBACK_KINDS) throw e
                last = ExtractionException(e.kind, "$model: ${e.detail}", e)
            }
        }
        throw last ?: ExtractionException(Kind.EMPTY, "No models configured")
    }

    private fun request(model: String, json: String): Request = Request.Builder()
        .url("${GeminiConfig.BASE_URL}$model:generateContent")
        .header("x-goog-api-key", apiKey)
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()

    private fun call(request: Request): String {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, text)
                return text
            }
        } catch (e: ExtractionException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw ExtractionException(Kind.TIMEOUT, "Request timed out", e)
        } catch (e: InterruptedIOException) {
            throw ExtractionException(Kind.TIMEOUT, "Request timed out", e)
        } catch (e: UnknownHostException) {
            throw ExtractionException(Kind.NO_INTERNET, "Host not reachable", e)
        } catch (e: ConnectException) {
            throw ExtractionException(Kind.NO_INTERNET, "Connection failed", e)
        } catch (e: NoRouteToHostException) {
            throw ExtractionException(Kind.NO_INTERNET, "No route to host", e)
        } catch (e: IOException) {
            throw ExtractionException(Kind.NO_INTERNET, "Network error: ${e.javaClass.simpleName}", e)
        }
    }

    private fun httpError(code: Int, body: String): ExtractionException {
        val message = try {
            JSONObject(body).optJSONObject("error")?.optString("message", "")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: body
        val detail = "HTTP $code: " + message.take(300)
        val kind = when {
            code == 429 -> Kind.RATE_LIMIT
            code == 401 || code == 403 -> Kind.BAD_KEY
            code == 400 && message.contains("API key", ignoreCase = true) -> Kind.BAD_KEY
            code in 500..599 -> Kind.SERVER
            else -> Kind.HTTP
        }
        return ExtractionException(kind, detail)
    }

    companion object {
        /** Worth retrying on the next model: overload, quota, timeout, model missing, unusable output. */
        private val FALLBACK_KINDS = setOf(Kind.SERVER, Kind.RATE_LIMIT, Kind.TIMEOUT, Kind.HTTP, Kind.EMPTY, Kind.BAD_JSON, Kind.BLOCKED)

        fun defaultClient(): OkHttpClient {
            val s = GeminiConfig.TIMEOUT_SECONDS
            return OkHttpClient.Builder()
                .callTimeout(s, TimeUnit.SECONDS)
                .connectTimeout(s, TimeUnit.SECONDS)
                .readTimeout(s, TimeUnit.SECONDS)
                .writeTimeout(s, TimeUnit.SECONDS)
                .build()
        }
    }
}
