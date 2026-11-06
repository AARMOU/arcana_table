package com.arcanatable

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Card pictures that load reliably and stay on the phone.
 *
 * - Pictures are kept in the app's cache folder, so art seen once shows instantly next time
 *   (home-screen commander art, custom cards).
 * - Scryfall API links (`api.scryfall.com/cards/named?exact=…&format=image`, `/cards/set/num?format=image`)
 *   are turned into the real picture link (cards.scryfall.io) with the JSON API, sending the
 *   User-Agent and Accept headers Scryfall asks for and spacing requests out (it limits bursts).
 *   The resolved link is remembered, so each card is looked up once.
 * - If a picture link fails (bad or unverified URL), the card's exact name is looked up instead.
 */
internal sealed interface CardArtLoadResult<out T> {
    data class Loaded<T>(val value: T) : CardArtLoadResult<T>
    object RetryableFailure : CardArtLoadResult<Nothing>
    object PermanentFailure : CardArtLoadResult<Nothing>
}

/** 429 is handled once inside the serialized Scryfall API gate; it is never retried by the outer image loop. */
internal fun isRetryableCardArtStatusCode(statusCode: Int): Boolean =
    statusCode == 408 || statusCode in 500..599

object CardImages {
    private const val USER_AGENT = "ArcanaTable/0.33 (Android)"
    private var dir: File? = null
    private var prefs: SharedPreferences? = null
    private val apiGate = Mutex()
    private var lastApiCall = 0L

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.cacheDir, "card-art").apply { mkdirs() }
        prefs = context.getSharedPreferences("card_art_links", Context.MODE_PRIVATE)
    }

    private var bundled: Map<String, String>? = null
    private val bundledGate = Mutex()

    /**
     * The picture link the app's own card data has for [name] (engine cards and custom cards), or
     * null. Read once from the bundled card files, then kept in memory.
     */
    suspend fun bundledImage(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        val map = bundledGate.withLock {
            bundled ?: buildMap {
                val nameRx = Regex("^\\{\"name\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                // The card's own picture is in its last "metadata" block (back faces come earlier).
                val imageRx = Regex("\"metadata\":\\{[^{}]*?\"imageUri\":\"(https://[^\"]+)\"")
                for (file in listOf("argentum/custom-cards.jsonl", "argentum/all-cards.jsonl")) {
                    runCatching {
                        context.assets.open(file).bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val n = nameRx.find(line)?.groupValues?.get(1) ?: return@forEach
                                val img = imageRx.findAll(line).lastOrNull()?.groupValues?.get(1) ?: return@forEach
                                putIfAbsent(n.lowercase(), img)
                            }
                        }
                    }
                }
            }.also { bundled = it }
        }
        map[name.lowercase()]
    }

    /** A cards.scryfall.io picture link at another size (`art_crop` for banners). */
    internal fun atSize(url: String, version: String): String =
        if (url.startsWith("https://cards.scryfall.io/")) url.replace(Regex("scryfall\\.io/(small|normal|large|png|art_crop|border_crop)/"), "scryfall.io/$version/") else url

    /** Which picture size a link asks for (Scryfall `version=`), so the resolved link matches. */
    internal fun versionOf(url: String): String =
        Regex("[?&]version=([a-z_]+)").find(url)?.groupValues?.get(1) ?: "normal"

    /** True for Scryfall API links that redirect to a picture (these need resolving). */
    internal fun isScryfallApi(url: String): Boolean = url.startsWith("https://api.scryfall.com/cards/")

    /** The JSON lookup for a Scryfall API picture link: same card, `format=json`, no version. */
    internal fun jsonLookupFor(url: String): String? {
        if (!isScryfallApi(url)) return null
        val base = url.substringBefore('?')
        val params = url.substringAfter('?', "").split('&').filter { it.isNotBlank() }
            .filterNot { it.startsWith("format=") || it.startsWith("version=") }
        return if (params.isEmpty()) base else base + "?" + params.joinToString("&")
    }

    /** The exact-name lookup for a card. */
    internal fun nameLookupFor(name: String): String =
        "https://api.scryfall.com/cards/named?exact=" + URLEncoder.encode(name.substringBefore(" // ").trim(), "UTF-8")

    /** The card name inside a `named?exact=` link, if any. */
    internal fun nameIn(url: String): String? =
        Regex("[?&]exact=([^&]+)").find(url)?.groupValues?.get(1)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    /** Picks the picture link out of a Scryfall card JSON (front face for two-faced cards). */
    internal fun imageFromJson(json: JSONObject, version: String): String? {
        fun from(uris: JSONObject?) = uris?.optString(version)?.takeIf { it.isNotBlank() }
            ?: uris?.optString("normal")?.takeIf { it.isNotBlank() }
        return from(json.optJSONObject("image_uris"))
            ?: from(json.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris"))
    }

    private fun cacheFile(url: String): File? {
        val d = dir ?: return null
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(d, "$hash.img")
    }

    /** Loads a card picture, preserving the existing nullable API for other callers. */
    suspend fun load(url: String, fallbackName: String? = null): Bitmap? =
        when (val result = loadForCardArt(url, fallbackName)) {
            is CardArtLoadResult.Loaded -> result.value
            CardArtLoadResult.RetryableFailure, CardArtLoadResult.PermanentFailure -> null
        }

    /**
     * One bounded retry attempt with an explicit failure class for the visible CardArt caller.
     * Permanent HTTP outcomes (including 404 and 429) do not trigger outer retries.
     */
    internal suspend fun loadForCardArt(url: String, fallbackName: String? = null): CardArtLoadResult<Bitmap> =
        withContext(Dispatchers.IO) {
            CardBitmapCache.get(url)?.let { return@withContext CardArtLoadResult.Loaded(it) }
            cacheFile(url)?.takeIf { it.exists() && it.length() > 0 }?.let { file ->
                BitmapFactory.decodeFile(file.path)?.let {
                    CardBitmapCache.put(url, it)
                    return@withContext CardArtLoadResult.Loaded(it)
                }
            }
            val version = versionOf(url)
            var transientFailure = false
            val candidates = mutableListOf<String>()
            if (isScryfallApi(url)) {
                when (val result = resolve(jsonLookupFor(url)!!, version)) {
                    is CardArtLoadResult.Loaded -> candidates.add(result.value)
                    CardArtLoadResult.RetryableFailure -> transientFailure = true
                    CardArtLoadResult.PermanentFailure -> Unit
                }
            } else {
                candidates.add(url)
            }
            val name = fallbackName?.takeIf { it.isNotBlank() && it != "Hidden card" } ?: nameIn(url)
            if (name != null) {
                when (val result = resolve(nameLookupFor(name), version)) {
                    is CardArtLoadResult.Loaded -> if (result.value !in candidates) candidates.add(result.value)
                    CardArtLoadResult.RetryableFailure -> transientFailure = true
                    CardArtLoadResult.PermanentFailure -> Unit
                }
            }
            for (candidate in candidates) {
                when (val response = fetchBytes(candidate)) {
                    is CardArtLoadResult.Loaded -> {
                        val bytes = response.value
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        runCatching { cacheFile(url)?.writeBytes(bytes) }
                        CardBitmapCache.put(url, bitmap)
                        return@withContext CardArtLoadResult.Loaded(bitmap)
                    }
                    CardArtLoadResult.RetryableFailure -> transientFailure = true
                    CardArtLoadResult.PermanentFailure -> Unit
                }
            }
            if (transientFailure) CardArtLoadResult.RetryableFailure else CardArtLoadResult.PermanentFailure
        }

    /** A Scryfall JSON lookup → real picture link, remembered on the phone. Misses are not remembered. */
    private suspend fun resolve(lookup: String, version: String): CardArtLoadResult<String> {
        val key = "$version|$lookup"
        prefs?.getString(key, null)?.let { return CardArtLoadResult.Loaded(it) }
        val json = when (val response = apiGet(lookup)) {
            is CardArtLoadResult.Loaded -> response.value
            CardArtLoadResult.RetryableFailure -> return CardArtLoadResult.RetryableFailure
            CardArtLoadResult.PermanentFailure -> return CardArtLoadResult.PermanentFailure
        }
        val image = runCatching { imageFromJson(JSONObject(json), version) }.getOrNull()
            ?: return CardArtLoadResult.PermanentFailure
        prefs?.edit()?.putString(key, image)?.apply()
        return CardArtLoadResult.Loaded(image)
    }

    /** One Scryfall API call at a time, ~120 ms apart; one retry after a 429 slow-down. */
    private suspend fun apiGet(url: String): CardArtLoadResult<String> = apiGate.withLock {
        for (attempt in 0..1) {
            val wait = 120 - (System.currentTimeMillis() - lastApiCall)
            if (wait > 0) delay(wait)
            lastApiCall = System.currentTimeMillis()
            val connection = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: MalformedURLException) {
                return@withLock CardArtLoadResult.PermanentFailure
            } catch (_: IOException) {
                return@withLock CardArtLoadResult.RetryableFailure
            } catch (_: Exception) {
                return@withLock CardArtLoadResult.PermanentFailure
            }
            try {
                connection.connectTimeout = 15_000; connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                if (code == 429 && attempt == 0) {
                    delay(1_000)
                    continue
                }
                if (code !in 200..299) {
                    return@withLock if (isRetryableCardArtStatusCode(code)) {
                        CardArtLoadResult.RetryableFailure
                    } else CardArtLoadResult.PermanentFailure
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                return@withLock CardArtLoadResult.Loaded(body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: MalformedURLException) {
                return@withLock CardArtLoadResult.PermanentFailure
            } catch (_: IOException) {
                return@withLock CardArtLoadResult.RetryableFailure
            } catch (_: Exception) {
                return@withLock CardArtLoadResult.PermanentFailure
            } finally {
                connection.disconnect()
            }
        }
        // A second 429 is terminal for this visible attempt: do not multiply rate-limited requests.
        CardArtLoadResult.PermanentFailure
    }

    private fun fetchBytes(url: String): CardArtLoadResult<ByteArray> {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: MalformedURLException) {
            return CardArtLoadResult.PermanentFailure
        } catch (_: IOException) {
            return CardArtLoadResult.RetryableFailure
        } catch (_: Exception) {
            return CardArtLoadResult.PermanentFailure
        }
        return try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "image/*")
            val code = connection.responseCode
            if (code !in 200..299) {
                if (isRetryableCardArtStatusCode(code)) CardArtLoadResult.RetryableFailure
                else CardArtLoadResult.PermanentFailure
            } else {
                CardArtLoadResult.Loaded(connection.inputStream.use { it.readBytes() })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            CardArtLoadResult.RetryableFailure
        } catch (_: Exception) {
            CardArtLoadResult.PermanentFailure
        } finally {
            connection.disconnect()
        }
    }
}

/** Artwork-only commander art for home and deck-selection surfaces. */
internal fun homeArtUrl(tableArt: String?, bundled: String?, name: String): String {
    val nameUrl = "https://api.scryfall.com/cards/named?exact=${URLEncoder.encode(name, "UTF-8")}&format=image&version=art_crop"
    for (candidate in listOfNotNull(tableArt, bundled)) {
        val artworkOnly = CardImages.atSize(candidate, "art_crop")
        if (artworkOnly.startsWith("https://cards.scryfall.io/")) return artworkOnly
        if (artworkOnly.startsWith("https://api.scryfall.com/")) {
            val base = artworkOnly.substringBefore('?')
            val query = artworkOnly.substringAfter('?', "").split('&').filter { it.isNotBlank() && !it.startsWith("version=") }
            return base + "?" + (query + "version=art_crop").joinToString("&")
        }
    }
    return nameUrl
}
