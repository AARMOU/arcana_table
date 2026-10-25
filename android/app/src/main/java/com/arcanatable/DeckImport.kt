package com.arcanatable

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.net.URLEncoder

data class DeckEntry(val name: String, val count: Int, val setCode: String? = null, val collectorNumber: String? = null, val commander: Boolean = false)
data class RepeatedDeckEntry(val name: String, val quantity: Int)

/** Summarizes repeated main-deck names for diagnostics; it does not imply a format violation. */
fun repeatedDeckEntries(entries: List<DeckEntry>): List<RepeatedDeckEntry> = entries
    .asSequence()
    .filterNot { it.commander }
    .groupBy { it.name.trim().lowercase(Locale.ROOT) }
    .mapNotNull { (_, matching) ->
        val quantity = matching.sumOf { it.count }
        if (quantity > 1) RepeatedDeckEntry(matching.first().name.trim(), quantity) else null
    }
    .sortedBy { it.name.lowercase(Locale.ROOT) }

data class ScryfallCard(
    val name: String,
    val type: String,
    val imageUrl: String?,
    val oracleText: String? = null,
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val manaValue: Double? = null,
    val artCropUrl: String? = null,
    val manaCost: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val keywords: List<String> = emptyList()
)

private val deckSectionHeadings = setOf("creatures", "instants", "sorceries", "artifacts", "enchantments", "lands", "planeswalkers", "battles", "other", "others", "other spells", "tokens", "noncreatures")

fun isDeckSectionHeading(name: String): Boolean = name.trim().trim('[', ']', ':', ' ').lowercase(Locale.ROOT) in deckSectionHeadings

/** Accepts common MTG deck exports (Moxfield, Archidekt, ManaBox, Arena-style text and CSV). */
fun parseDeckList(raw: String): List<DeckEntry> = parseDeckListDetailed(raw).playableEntries

private fun splitCsv(line: String): List<String> {
    val columns = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        if (char == '"') {
            if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                cell.append('"'); index++
            } else quoted = !quoted
        } else if (char == ',' && !quoted) {
            columns += cell.toString().trim(); cell.clear()
        } else cell.append(char)
        index++
    }
    columns += cell.toString().trim()
    return columns
}

object Scryfall {
    suspend fun lookup(entries: List<DeckEntry>): Map<DeckEntry, ScryfallCard> = withContext(Dispatchers.IO) {
        val exact = linkedMapOf<String, ScryfallCard>()
        val byName = linkedMapOf<String, ScryfallCard>()
        suspend fun collect(identifiers: JSONArray) {
            val body = JSONObject().put("identifiers", identifiers).toString()
            val connection = (URL("https://api.scryfall.com/cards/collection").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ArcanaTable/0.7.0 (Android)")
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val json = stream.bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) error("Scryfall returned ${connection.responseCode}: ${JSONObject(json).optString("details", "Card lookup failed")}")
                val data = JSONObject(json).optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val card = data.getJSONObject(i)
                    val name = card.optString("name")
                    val type = card.optString("type_line", "CARD")
                    val imageUrl = card.optJSONObject("image_uris")?.optString("large")?.takeIf { !it.isNullOrBlank() }
                        ?: card.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris")?.optString("large")?.takeIf { !it.isNullOrBlank() }
                    val artCropUrl = card.optJSONObject("image_uris")?.optString("art_crop")?.takeIf { it.isNotBlank() }
                        ?: card.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris")?.optString("art_crop")?.takeIf { it.isNotBlank() }
                    val firstFace = card.optJSONArray("card_faces")?.optJSONObject(0)
                    val found = ScryfallCard(
                        name, type, imageUrl,
                        card.optString("oracle_text").takeIf { it.isNotBlank() },
                        card.optString("set").takeIf { it.isNotBlank() },
                        card.optString("collector_number").takeIf { it.isNotBlank() },
                        card.optDouble("cmc").takeIf { card.has("cmc") },
                        artCropUrl,
                        card.optString("mana_cost").takeIf { it.isNotBlank() },
                        card.optString("power").takeIf { it.isNotBlank() } ?: firstFace?.optString("power")?.takeIf { it.isNotBlank() },
                        card.optString("toughness").takeIf { it.isNotBlank() } ?: firstFace?.optString("toughness")?.takeIf { it.isNotBlank() },
                        card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                    )
                    byName[name.normalized()] = found
                    val set = card.optString("set").lowercase(Locale.ROOT)
                    val collector = card.optString("collector_number")
                    if (set.isNotBlank() && collector.isNotBlank()) exact["$set/$collector"] = found
                }
            } finally {
                connection.disconnect()
            }
        }
        entries.filter { !it.setCode.isNullOrBlank() && !it.collectorNumber.isNullOrBlank() }.chunked(75).forEach { batch ->
            try { collect(JSONArray().apply { batch.forEach { put(JSONObject().put("set", it.setCode!!.lowercase(Locale.ROOT)).put("collector_number", it.collectorNumber)) } }) } catch (_: Exception) { /* preserve other batches and offline fallback */ }
        }
        entries.map { it.name }.distinct().chunked(75).forEach { names ->
            try { collect(JSONArray().apply { names.forEach { put(JSONObject().put("name", it)) } }) }
            catch (_: Exception) { /* one failed API request cannot abort the entire import */ }
        }
        entries.mapNotNull { entry ->
            val hasExactPrinting = !entry.setCode.isNullOrBlank() && !entry.collectorNumber.isNullOrBlank()
            val card = if (hasExactPrinting) {
                exact["${entry.setCode!!.lowercase(Locale.ROOT)}/${entry.collectorNumber}"]
                    ?.takeIf { it.name.normalized() == entry.name.normalized() }
            } else {
                byName[entry.name.normalized()]
            }
            card?.let { entry to it }
        }.toMap()
    }

    suspend fun searchCards(query: String): List<ScryfallCard> = withContext(Dispatchers.IO) {
        val expression = "name:\"${query.trim().replace("\"", "") }\""
        val encoded = URLEncoder.encode(expression, "UTF-8")
        val connection = (URL("https://api.scryfall.com/cards/search?q=$encoded&unique=cards&order=name").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/json"); setRequestProperty("User-Agent", "ArcanaTable/0.7.0 (Android)")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { index ->
                val card = data.optJSONObject(index) ?: return@mapNotNull null
                val imageUrl = card.optJSONObject("image_uris")?.optString("large")?.takeIf { it.isNotBlank() }
                    ?: card.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris")?.optString("large")?.takeIf { it.isNotBlank() }
                val artCropUrl = card.optJSONObject("image_uris")?.optString("art_crop")?.takeIf { it.isNotBlank() }
                    ?: card.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris")?.optString("art_crop")?.takeIf { it.isNotBlank() }
                val firstFace = card.optJSONArray("card_faces")?.optJSONObject(0)
                ScryfallCard(
                    card.optString("name"), card.optString("type_line", "CARD"), imageUrl,
                    card.optString("oracle_text").takeIf { it.isNotBlank() }, card.optString("set"), card.optString("collector_number"),
                    card.optDouble("cmc").takeIf { card.has("cmc") }, artCropUrl, card.optString("mana_cost").takeIf { it.isNotBlank() },
                    card.optString("power").takeIf { it.isNotBlank() } ?: firstFace?.optString("power")?.takeIf { it.isNotBlank() },
                    card.optString("toughness").takeIf { it.isNotBlank() } ?: firstFace?.optString("toughness")?.takeIf { it.isNotBlank() },
                    card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                )
            }
        } finally { connection.disconnect() }
    }

    /** Ask Scryfall's fuzzy name endpoint for the closest card when an import has a typo. */
    suspend fun fuzzyNamedCard(query: String): ScryfallCard? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = (URL("https://api.scryfall.com/cards/named?fuzzy=$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/json"); setRequestProperty("User-Agent", "ArcanaTable/0.7.0 (Android)")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val card = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val firstFace = card.optJSONArray("card_faces")?.optJSONObject(0)
            val imageUrl = card.optJSONObject("image_uris")?.optString("large")?.takeIf { it.isNotBlank() }
                ?: firstFace?.optJSONObject("image_uris")?.optString("large")?.takeIf { it.isNotBlank() }
            val artCropUrl = card.optJSONObject("image_uris")?.optString("art_crop")?.takeIf { it.isNotBlank() }
                ?: firstFace?.optJSONObject("image_uris")?.optString("art_crop")?.takeIf { it.isNotBlank() }
            ScryfallCard(
                card.optString("name"), card.optString("type_line", "CARD"), imageUrl,
                card.optString("oracle_text").takeIf { it.isNotBlank() },
                card.optString("set").takeIf { it.isNotBlank() },
                card.optString("collector_number").takeIf { it.isNotBlank() },
                card.optDouble("cmc").takeIf { card.has("cmc") }, artCropUrl,
                card.optString("mana_cost").takeIf { it.isNotBlank() },
                card.optString("power").takeIf { it.isNotBlank() } ?: firstFace?.optString("power")?.takeIf { it.isNotBlank() },
                card.optString("toughness").takeIf { it.isNotBlank() } ?: firstFace?.optString("toughness")?.takeIf { it.isNotBlank() },
                card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            )
        } finally { connection.disconnect() }
    }

    suspend fun searchTokens(query: String): List<ScryfallCard> = withContext(Dispatchers.IO) {
        val expression = "is:token name:\"${query.trim().replace("\"", "") }\""
        val encoded = URLEncoder.encode(expression, "UTF-8")
        val connection = (URL("https://api.scryfall.com/cards/search?q=$encoded&unique=cards&order=name").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ArcanaTable/0.7.0 (Android)")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val data = json.optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { index ->
                val card = data.optJSONObject(index) ?: return@mapNotNull null
                val imageUrl = card.optJSONObject("image_uris")?.optString("large")?.takeIf { it.isNotBlank() }
                    ?: card.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris")?.optString("large")?.takeIf { it.isNotBlank() }
                val firstFace = card.optJSONArray("card_faces")?.optJSONObject(0)
                ScryfallCard(
                    card.optString("name"), card.optString("type_line", "TOKEN"), imageUrl,
                    card.optString("oracle_text").takeIf { it.isNotBlank() },
                    power = card.optString("power").takeIf { it.isNotBlank() } ?: firstFace?.optString("power")?.takeIf { it.isNotBlank() },
                    toughness = card.optString("toughness").takeIf { it.isNotBlank() } ?: firstFace?.optString("toughness")?.takeIf { it.isNotBlank() },
                    keywords = card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun String.normalized() = lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
}

object CardBitmapCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    fun get(url: String) = cache.get(url)
    fun put(url: String, bitmap: Bitmap) = cache.put(url, bitmap)
}

/** Card picture by link; see [CardImages] (phone cache, Scryfall link resolving, name fallback). */
suspend fun downloadCardBitmap(url: String, fallbackName: String? = null): Bitmap? = CardImages.load(url, fallbackName)
