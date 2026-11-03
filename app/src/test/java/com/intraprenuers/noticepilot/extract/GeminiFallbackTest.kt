package com.intraprenuers.noticepilot.extract

import com.intraprenuers.noticepilot.verify.SourceLine
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.UnknownHostException

/** Fakes Gemini with an OkHttp interceptor: no network needed. */
class GeminiFallbackTest {

    private val lines = listOf(SourceLine("L1", "Sports Day on 26 October 2026"))
    private val models = listOf("model-a", "model-b", "model-c")

    private val okBody = """
        {"candidates":[{"content":{"parts":[{"text":
        "{\"items\":[{\"type\":\"event\",\"title\":\"Sports Day\",\"date\":\"2026-10-26\",\"conditions\":[],\"line_ids\":[\"L1\"],\"quote\":\"Sports Day on 26 October 2026\"}]}"
        }]}}]}
    """.trimIndent()

    /** Replies per model with (code, body); records which models were called, in order. */
    private class Fake(val replies: Map<String, Pair<Int, String>>) : Interceptor {
        val called = mutableListOf<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val model = chain.request().url.encodedPath.substringAfterLast('/').substringBefore(':')
            called += model
            val (code, body) = replies[model] ?: (500 to "{}")
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun extractor(fake: Interceptor) =
        GeminiExtractor("test-key", OkHttpClient.Builder().addInterceptor(fake).build(), models)

    private val busy = 503 to """{"error":{"code":503,"message":"The model is overloaded. Please try again later."}}"""

    @Test fun overloadedPrimaryFallsBackToNextModel() {
        val fake = Fake(mapOf("model-a" to busy, "model-b" to (200 to okBody)))
        val items = runBlocking { extractor(fake).extract(lines) }
        assertEquals(listOf("model-a", "model-b"), fake.called)
        assertEquals("Sports Day", items.single().title)
    }

    @Test fun primaryWorksNoFallback() {
        val fake = Fake(mapOf("model-a" to (200 to okBody)))
        runBlocking { extractor(fake).extract(lines) }
        assertEquals(listOf("model-a"), fake.called)
    }

    @Test fun rateLimitAndMissingModelAlsoFallBack() {
        val fake = Fake(
            mapOf(
                "model-a" to (429 to """{"error":{"message":"Resource exhausted"}}"""),
                "model-b" to (404 to """{"error":{"message":"models/model-b is not found"}}"""),
                "model-c" to (200 to okBody)
            )
        )
        val items = runBlocking { extractor(fake).extract(lines) }
        assertEquals(models, fake.called)
        assertEquals(1, items.size)
    }

    @Test fun allModelsBusyThrowsServerWithBusyMessage() {
        val fake = Fake(models.associateWith { busy })
        try {
            runBlocking { extractor(fake).extract(lines) }
            fail("expected exception")
        } catch (e: ExtractionException) {
            assertEquals(ExtractionException.Kind.SERVER, e.kind)
            assertEquals(models, fake.called)
            assertEquals(true, e.userMessage.startsWith("Gemini is busy"))
            assertEquals(false, e.detail.contains("test-key"))
        }
    }

    @Test fun badKeyDoesNotFallBack() {
        val fake = Fake(mapOf("model-a" to (400 to """{"error":{"message":"API key not valid. Please pass a valid API key."}}""")))
        try {
            runBlocking { extractor(fake).extract(lines) }
            fail("expected exception")
        } catch (e: ExtractionException) {
            assertEquals(ExtractionException.Kind.BAD_KEY, e.kind)
            assertEquals(listOf("model-a"), fake.called)
        }
    }

    @Test fun noInternetDoesNotFallBack() {
        val calls = mutableListOf<String>()
        val offline = Interceptor { chain ->
            calls += chain.request().url.toString()
            throw UnknownHostException("generativelanguage.googleapis.com")
        }
        try {
            runBlocking { extractor(offline).extract(lines) }
            fail("expected exception")
        } catch (e: ExtractionException) {
            assertEquals(ExtractionException.Kind.NO_INTERNET, e.kind)
            assertEquals(1, calls.size)
        }
    }

    @Test fun malformedOutputFallsBack() {
        val bad = """{"candidates":[{"content":{"parts":[{"text":"not json at all"}]}}]}"""
        val fake = Fake(mapOf("model-a" to (200 to bad), "model-b" to (200 to okBody)))
        val items = runBlocking { extractor(fake).extract(lines) }
        assertEquals(listOf("model-a", "model-b"), fake.called)
        assertEquals(1, items.size)
    }
}
