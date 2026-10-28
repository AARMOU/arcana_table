package com.arcanatable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Opt-in, name-only requests to the owner's separate Cloudflare Worker.
 * Never sends decklists, account IDs, devices, room IDs or credentials.
 * Server-held GitHub token is NEVER part of this Android build.
 */
data class CardRequestReceipt(val accepted: Int, val pending: Boolean, val trackerUrl: String)

object CardRequestApi {
    /** Stable public client identity; Cloudflare blocks some generic HTTP clients (Error 1010). */
    internal const val USER_AGENT = "ArcanaTable-CardRequests/1.0"

    val isConfigured: Boolean
        get() = BuildConfig.CARD_REQUEST_ENDPOINT.startsWith("https://", ignoreCase = true)

    /** The importer owns the source-of-truth support report, not the request API. */
    fun normalizeNames(names: List<String>): List<String> = names
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.length in 2..120 }
        .distinctBy { it.substringBefore(" // ").lowercase(Locale.ROOT) }
        .take(10)

    suspend fun submit(names: List<String>, requestId: String): CardRequestReceipt = withContext(Dispatchers.IO) {
        if (!isConfigured) throw IOException("Card requests aren't enabled in this build yet.")
        val normalized = normalizeNames(names)
        if (normalized.isEmpty()) throw IOException("Select at least one unsupported card.")
        val body = JSONObject()
            .put("requestId", requestId)
            .put("cardNames", JSONArray(normalized))
            .toString()
        val connection = (URL(BuildConfig.CARD_REQUEST_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText().take(2048) }.orEmpty()
            val reply = runCatching { JSONObject(raw) }.getOrNull()
            if (status !in 200..299 || reply?.optBoolean("ok") != true) {
                val error = reply?.optString("error")?.takeIf { it.isNotBlank() }
                throw IOException(error ?: "Request service returned HTTP $status. Please retry.")
            }
            CardRequestReceipt(
                accepted = reply.optInt("accepted", 0),
                pending = reply.optBoolean("pending", false),
                trackerUrl = reply.optString("tracker", "https://github.com/AARMOU/arcana_table/issues/321"),
            )
        } finally {
            connection.disconnect()
        }
    }
}
