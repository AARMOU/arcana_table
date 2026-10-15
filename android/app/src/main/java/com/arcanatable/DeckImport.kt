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
import kotlin.random.Random

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
    val keywords: List<String> = emptyList(),
    val colors: List<String> = emptyList()
)

private val deckSectionHeadings = setOf("creatures", "instants", "sorceries", "artifacts", "enchantments", "lands", "planeswalkers", "battles", "other", "others", "tokens", "noncreatures")

fun isDeckSectionHeading(name: String): Boolean = name.trim().trim('[', ']', ':', ' ').lowercase(Locale.ROOT) in deckSectionHeadings

/** Accepts common MTG deck exports (Moxfield, Archidekt, ManaBox, Arena-style text and CSV). */
fun parseDeckList(raw: String): List<DeckEntry> {
    data class Key(val name: String, val set: String?, val collector: String?, val commander: Boolean)
    val quantities = linkedMapOf<Key, Int>()
    var sideboard = false
    var commanderSection = false
    var csvNameIndex: Int? = null
    var csvCountIndex: Int? = null
    var csvSetIndex: Int? = null
    var csvCollectorIndex: Int? = null
    var csvZoneIndex: Int? = null

    fun add(nameRaw: String, countRaw: Int?, setRaw: String? = null, collectorRaw: String? = null, isCommander: Boolean = commanderSection) {
        val name = nameRaw.trim().trim('"', '\'')
        val count = countRaw?.coerceAtMost(999) ?: 0
        if (name.isBlank() || count < 1) return
        val set = setRaw?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
        val collector = collectorRaw?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() && !it.equals("null", true) }
        val key = Key(name, set?.uppercase(Locale.ROOT), collector, isCommander)
        quantities[key] = (quantities[key] ?: 0) + count
    }

    fun stripPrinting(rawName: String): Triple<String, String?, String?> {
        var name = rawName.trim().trim('"', '\'')
        val paren = Regex("^(.*?)\\s+\\(([A-Za-z0-9]{2,6})\\)\\s+#?(\\d+[a-zA-Z]?)$").find(name)
        if (paren != null) return Triple(paren.groupValues[1].trim(), paren.groupValues[2], paren.groupValues[3])
        val bracket = Regex("^(.*?)\\s+\\[([A-Za-z0-9]{2,6})]\\s+#?(\\d+[a-zA-Z]?)$").find(name)
        if (bracket != null) return Triple(bracket.groupValues[1].trim(), bracket.groupValues[2], bracket.groupValues[3])
        val setOnly = Regex("^(.*?)\\s+\\(([A-Za-z0-9]{2,6})\\)$").find(name)
        if (setOnly != null) return Triple(setOnly.groupValues[1].trim(), setOnly.groupValues[2], null)
        return Triple(name, null, null)
    }

    raw.lineSequence().forEach { rawLine ->
        var line = rawLine.trim().removePrefix("\uFEFF")
        if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEach
        // Strip a quantity prefix before classifying headings, since exports may
        // render a category as "1 [INSTANTS]" as well as just "[INSTANTS]".
        val headingText = line.replace(Regex("^(?:[xX]\\s*)?\\d+\\s*[xX]?\\s+"), "")
        val normalizedLine = headingText.trim('[', ']', ':', ' ').lowercase(Locale.ROOT)
        if (normalizedLine in setOf("sideboard", "sideboard cards", "maybeboard", "considering")) {
            sideboard = true
            return@forEach
        }
        // Some exports include bracketed/category headings. They are structure,
        // not cards (e.g. the literal "[INSTANTS]" should never become a card).
        if (normalizedLine in deckSectionHeadings) return@forEach
        if (normalizedLine in setOf("mainboard", "main", "deck")) {
            sideboard = false
            commanderSection = false
            return@forEach
        }
        if (normalizedLine in setOf("commander", "commanders")) { sideboard = false; commanderSection = true; return@forEach }
        if (normalizedLine in setOf("companion")) { sideboard = false; commanderSection = false; return@forEach }
        if (sideboard) return@forEach
        if (line.startsWith("SB:", ignoreCase = true)) return@forEach
        var explicitCommander = false
        if (line.startsWith("Commander:", ignoreCase = true)) {
            line = line.substringAfter(':').trim()
            explicitCommander = true
        }

        val csv = splitCsv(line)
        val normalizedHeaders = csv.map { it.trim().trim('"').lowercase(Locale.ROOT) }
        if (normalizedHeaders.any { it in setOf("name", "card name") } && normalizedHeaders.any { it in setOf("count", "quantity", "amount") }) {
            csvNameIndex = normalizedHeaders.indexOfFirst { it in setOf("name", "card name") }
            csvCountIndex = normalizedHeaders.indexOfFirst { it in setOf("count", "quantity", "amount") }
            csvSetIndex = normalizedHeaders.indexOfFirst { it in setOf("set", "set code", "edition") }.takeIf { it >= 0 }
            csvCollectorIndex = normalizedHeaders.indexOfFirst { it in setOf("collector number", "collector_number", "number", "card number") }.takeIf { it >= 0 }
            csvZoneIndex = normalizedHeaders.indexOfFirst { it in setOf("zone", "board", "section", "type") }.takeIf { it >= 0 }
            return@forEach
        }
        if (csvNameIndex != null && csvCountIndex != null) {
            val name = csv.getOrNull(csvNameIndex!!)?.trim().orEmpty()
            val count = csv.getOrNull(csvCountIndex!!)?.trim()?.toIntOrNull()
            val zone = csvZoneIndex?.let { csv.getOrNull(it)?.lowercase(Locale.ROOT).orEmpty() }.orEmpty()
            val isCommander = explicitCommander || commanderSection || zone.contains("commander")
            add(name, count, csvSetIndex?.let { csv.getOrNull(it) }, csvCollectorIndex?.let { csv.getOrNull(it) }, isCommander)
            return@forEach
        }
        var count = 1
        var cardPart = line
        val prefixed = Regex("^(?:[xX]\\s*)?(\\d+)\\s*[xX]?\\s+(.+)$").find(line)
        if (prefixed != null) { count = prefixed.groupValues[1].toIntOrNull() ?: 0; cardPart = prefixed.groupValues[2].trim() }
        else if (csv.size >= 2 && csv.first().trim().toIntOrNull() != null) { count = csv.first().trim().toIntOrNull() ?: 0; cardPart = csv.getOrNull(1).orEmpty() }
        val (name, setCode, collectorNumber) = stripPrinting(cardPart)
        add(name, count, setCode, collectorNumber, explicitCommander || commanderSection)
    }
    return quantities.map { (key, count) -> DeckEntry(key.name, count, key.set, key.collector, key.commander) }
}

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
                        card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                        card.optJSONArray("colors")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
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
            collect(JSONArray().apply { batch.forEach { put(JSONObject().put("set", it.setCode!!.lowercase(Locale.ROOT)).put("collector_number", it.collectorNumber)) } })
        }
        entries.map { it.name }.distinct().chunked(75).forEach { names -> collect(JSONArray().apply { names.forEach { put(JSONObject().put("name", it)) } }) }
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
                    card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                    card.optJSONArray("colors")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
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
                card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                card.optJSONArray("colors")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
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
                    keywords = card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                    colors = card.optJSONArray("colors")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
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

suspend fun downloadCardBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    CardBitmapCache.get(url)?.let { return@withContext it }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "ArcanaTable/0.7.0 (Android)")
    }
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
        if (bitmap != null) CardBitmapCache.put(url, bitmap)
        bitmap
    } finally {
        connection.disconnect()
    }
}

/** Fetches a random popular public Commander list from Archidekt and converts it
 * to the text format accepted by the existing deck importer. */
suspend fun fetchRandomCommanderDecklist(): String = withContext(Dispatchers.IO) {
    fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ArcanaTable/0.15")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Deck source returned HTTP $code")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    val page = Random.nextInt(1, 6)
    val search = getJson("https://archidekt.com/api/decks/v3/?deckFormat=3&orderBy=-viewCount&pageSize=100&page=$page")
    val results = search.optJSONArray("results") ?: JSONArray()
    val candidates = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        .filter { it.optLong("id", 0L) > 0L }.shuffled()
    if (candidates.isEmpty()) error("No public Commander decks were returned. Try again later.")

    var lastFailure: Exception? = null
    for (candidate in candidates.take(8)) {
        try {
            val deck = getJson("https://archidekt.com/api/decks/${candidate.optLong("id")}/")
            val cards = deck.optJSONArray("cards") ?: continue
            val commanders = mutableListOf<String>()
            val mainboard = mutableListOf<String>()
            for (index in 0 until cards.length()) {
                val row = cards.optJSONObject(index) ?: continue
                val card = row.optJSONObject("card") ?: continue
                val name = (card.optJSONObject("oracleCard") ?: card).optString("name").trim()
                if (name.isBlank()) continue
                val categories = row.optJSONArray("categories")
                val categoryNames = (0 until (categories?.length() ?: 0))
                    .mapNotNull { categories?.optString(it)?.lowercase(Locale.ROOT) }
                if (categoryNames.any { it.contains("sideboard") || it.contains("maybeboard") }) continue
                val line = "${row.optInt("quantity", 1).coerceAtLeast(1)} $name"
                if (categoryNames.any { it.contains("commander") }) commanders += line else mainboard += line
            }
            if (commanders.isNotEmpty() && mainboard.isNotEmpty()) {
                return@withContext buildString {
                    appendLine("[Commander]")
                    commanders.forEach(::appendLine)
                    appendLine("[Mainboard]")
                    mainboard.forEach(::appendLine)
                }
            }
        } catch (exception: Exception) {
            lastFailure = exception
        }
    }
    throw IllegalStateException("Could not load a complete public Commander deck. Try again later.", lastFailure)
}
