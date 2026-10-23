package com.arcanatable

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
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

    /** Loads a card picture: memory, then the phone's cache, then the network (with fallbacks). */
    suspend fun load(url: String, fallbackName: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        CardBitmapCache.get(url)?.let { return@withContext it }
        cacheFile(url)?.takeIf { it.exists() && it.length() > 0 }?.let { file ->
            BitmapFactory.decodeFile(file.path)?.let { CardBitmapCache.put(url, it); return@withContext it }
        }
        val version = versionOf(url)
        val candidates = buildList {
            if (isScryfallApi(url)) resolve(jsonLookupFor(url)!!, version)?.let(::add) else add(url)
            val name = fallbackName?.takeIf { it.isNotBlank() && it != "Hidden card" } ?: nameIn(url)
            if (name != null) resolve(nameLookupFor(name), version)?.let { if (it !in this) add(it) }
        }
        for (candidate in candidates) {
            val bytes = fetchBytes(candidate) ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            runCatching { cacheFile(url)?.writeBytes(bytes) }
            CardBitmapCache.put(url, bitmap)
            return@withContext bitmap
        }
        null
    }

    /** A Scryfall JSON lookup → real picture link, remembered on the phone. Misses are not remembered. */
    private suspend fun resolve(lookup: String, version: String): String? {
        val key = "$version|$lookup"
        prefs?.getString(key, null)?.let { return it }
        val json = apiGet(lookup) ?: return null
        val image = runCatching { imageFromJson(JSONObject(json), version) }.getOrNull() ?: return null
        prefs?.edit()?.putString(key, image)?.apply()
        return image
    }

    /** One Scryfall API call at a time, ~120 ms apart; one retry after a "slow down" (429). */
    private suspend fun apiGet(url: String): String? = apiGate.withLock {
        repeat(2) { attempt ->
            val wait = 120 - (System.currentTimeMillis() - lastApiCall)
            if (wait > 0) delay(wait)
            lastApiCall = System.currentTimeMillis()
            val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return@withLock null
            try {
                connection.connectTimeout = 15_000; connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                val code = runCatching { connection.responseCode }.getOrNull() ?: return@withLock null
                if (code == 429 && attempt == 0) { delay(1_000); return@repeat }
                if (code !in 200..299) return@withLock null
                return@withLock connection.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                return@withLock null
            } finally { connection.disconnect() }
        }
        null
    }

    private fun fetchBytes(url: String): ByteArray? {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode !in 200..299) null else connection.inputStream.use { it.readBytes() }
        } catch (_: Exception) { null } finally { connection.disconnect() }
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
