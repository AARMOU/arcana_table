package com.arcanatable

import java.io.InputStream
import java.util.Locale

/**
 * Import-time Argentum support check for the existing deck importer.
 *
 * "Supported" here only means the card is in the card library shipped with the app:
 * Argentum's own definitions (`argentum/all-cards`) or Arcana's custom definitions
 * (`argentum/custom-cards`). Neither is proof the card has been verified in gameplay; the
 * app has no verified-card list yet, so nothing is reported as verified.
 *
 * It reads the same asset files with the same rule as the offline engine's
 * `OfflineCardLibrary`: Argentum's definition wins, and a custom card counts only when
 * Argentum has no card of that name.
 */
enum class CardSupport {
    /** Defined in Argentum's card library. Indexed, not verified by Arcana. */
    ARGENTUM,
    /** Defined only by Arcana's custom cards. Indexed, not verified. */
    CUSTOM_UNVERIFIED,
    /** Not in the shipped library, so Argentum games leave it out. */
    UNSUPPORTED;

    val isIndexed: Boolean get() = this != UNSUPPORTED

    /** Short badge for the deck editor and import dialog. */
    val label: String get() = when (this) {
        ARGENTUM -> "Argentum library · not verified"
        CUSTOM_UNVERIFIED -> "Arcana custom · not verified"
        UNSUPPORTED -> "Not in Argentum"
    }
}

/** One card the shipped library can run, with just enough data to search and suggest. */
data class SupportedCard(
    val name: String,
    val typeLine: String,
    val manaCost: String,
    val imageUrl: String?,
    val custom: Boolean,
    /** Color identity letters (W U B R G); null when the asset gave none. */
    val identity: Set<Char>? = null,
    val kind: String = "card",
) {
    val manaValue: Double get() = manaValueOf(manaCost)
    val colors: Set<Char> get() = identity ?: colorsOf(manaCost)
    val cardTypes: Set<String> get() = cardTypesOf(typeLine)
    val subtypes: Set<String> get() = subtypesOf(typeLine)
    val support: CardSupport get() = if (custom) CardSupport.CUSTOM_UNVERIFIED else CardSupport.ARGENTUM

    /** The existing import/editor card shape, built offline from the library asset. */
    fun toScryfallCard(): ScryfallCard = ScryfallCard(
        name = name, type = typeLine, imageUrl = imageUrl, manaValue = manaValue,
        manaCost = manaCost.takeIf { it.isNotBlank() }
    )
}

data class EntrySupport(val entry: DeckEntry, val support: CardSupport, val libraryName: String?)

data class DeckSupportReport(val entries: List<EntrySupport>) {
    private fun copies(support: CardSupport) = entries.filter { it.support == support }.sumOf { it.entry.count }
    val argentumCopies: Int get() = copies(CardSupport.ARGENTUM)
    val customCopies: Int get() = copies(CardSupport.CUSTOM_UNVERIFIED)
    val unsupportedCopies: Int get() = copies(CardSupport.UNSUPPORTED)
    val totalCopies: Int get() = entries.sumOf { it.entry.count }
    /** Distinct names not in the library, in deck order. */
    val unsupportedNames: List<String> get() = entries.filter { it.support == CardSupport.UNSUPPORTED }.map { it.entry.name }.distinctBy { it.lowercase(Locale.ROOT) }
    val commanderUnsupported: Boolean get() = entries.any { it.entry.commander && it.support == CardSupport.UNSUPPORTED }

    /** One line for the import dialog and diagnostics. Never claims cards are verified. */
    fun summary(): String = buildString {
        append("Argentum check: $argentumCopies in Argentum")
        if (customCopies > 0) append(" · $customCopies Arcana custom")
        append(" · $unsupportedCopies not in Argentum")
        append(" (indexed, not gameplay-verified)")
    }
}

class SupportedCardCatalog(cards: List<SupportedCard>) {
    private val byLower: Map<String, SupportedCard>
    private val playable: List<SupportedCard>

    init {
        val map = LinkedHashMap<String, SupportedCard>()
        // Argentum first, so its definition wins over a custom card of the same name.
        cards.sortedBy { it.custom }.forEach { card -> map.putIfAbsent(card.name.lowercase(Locale.ROOT), card) }
        byLower = map
        playable = map.values.filter { it.kind != "token" }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    val size: Int get() = playable.size
    val customCount: Int get() = playable.count { it.custom }

    /** The library's card for [name], also matching a double-faced card listed by both faces. */
    fun find(name: String): SupportedCard? {
        val key = name.trim().lowercase(Locale.ROOT)
        byLower[key]?.takeIf { it.kind != "token" }?.let { return it }
        val front = key.substringBefore(" // ").trim()
        return if (front != key) byLower[front]?.takeIf { it.kind != "token" } else null
    }

    fun support(name: String): CardSupport = find(name)?.support ?: CardSupport.UNSUPPORTED

    fun check(entries: List<DeckEntry>): DeckSupportReport = DeckSupportReport(entries.map { entry ->
        val card = find(entry.name)
        EntrySupport(entry, card?.support ?: CardSupport.UNSUPPORTED, card?.name)
    })

    /** Offline name search over supported cards: exact, then prefix, then word-start, then contains. */
    fun search(query: String, limit: Int = 40): List<SupportedCard> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        fun rank(card: SupportedCard): Int {
            val name = card.name.lowercase(Locale.ROOT)
            return when {
                name == q -> 0
                name.startsWith(q) -> 1
                name.split(' ', ',', '-').any { it.startsWith(q) } -> 2
                else -> 3
            }
        }
        return playable.asSequence().filter { it.name.lowercase(Locale.ROOT).contains(q) }
            .sortedWith(compareBy<SupportedCard>({ rank(it) }, { it.name.lowercase(Locale.ROOT) }))
            .take(limit).toList()
    }

    /**
     * Conservative swap candidates for a card the library lacks. A candidate must share a card
     * type with the original and stay inside [allowedIdentity] (the commander's colors, when known).
     * Suggestions are shown for the player to confirm; nothing is replaced automatically.
     */
    fun suggestReplacements(
        typeLine: String,
        manaValue: Double?,
        targetColors: Set<Char>,
        allowedIdentity: Set<Char>?,
        exclude: Set<String> = emptySet(),
        limit: Int = 5,
        /** For a commander swap: only legendary creatures (or "can be your commander" cards Argentum types as such). */
        requireLegendary: Boolean = false,
    ): List<SupportedCard> {
        val types = cardTypesOf(typeLine)
        if (types.isEmpty()) return emptyList()
        val subtypes = subtypesOf(typeLine)
        val excluded = exclude.map { it.lowercase(Locale.ROOT) }.toSet()
        val identity = allowedIdentity ?: targetColors
        val wantsBasic = typeLine.contains("Basic", ignoreCase = true)
        return playable.asSequence()
            .filter { it.name.lowercase(Locale.ROOT) !in excluded }
            .filter { it.cardTypes.intersect(types).isNotEmpty() }
            .filter { it.colors.all { color -> color in identity } }
            .filter { wantsBasic || it.kind != "basic" }
            .filter { !requireLegendary || it.typeLine.substringBefore(" // ").contains("Legendary", ignoreCase = true) }
            .map { card ->
                var score = kotlin.math.abs(card.manaValue - (manaValue ?: card.manaValue)) * 2.0
                if (card.cardTypes != types) score += 1.5
                if (subtypes.isNotEmpty() && card.subtypes.intersect(subtypes).isNotEmpty()) score -= 1.0
                if (card.colors == targetColors) score -= 0.5
                if (card.custom) score += 0.25
                card to score
            }
            .sortedWith(compareBy<Pair<SupportedCard, Double>>({ it.second }, { it.first.name.lowercase(Locale.ROOT) }))
            .take(limit).map { it.first }.toList()
    }

    companion object {
        /** Reads the shipped library index and data, decoding only names, costs, types and images. */
        fun load(open: (String) -> InputStream?): SupportedCardCatalog {
            val cards = ArrayList<SupportedCard>(17_000)
            for ((base, custom) in listOf("all-cards" to false, "custom-cards" to true)) {
                val kinds = open("argentum/$base.index.tsv")?.bufferedReader()?.useLines { lines ->
                    lines.filter(String::isNotBlank).map { it.substringAfter('\t', "card") }.toList()
                }
                open("argentum/$base.jsonl")?.bufferedReader()?.useLines { lines ->
                    lines.filter(String::isNotBlank).forEachIndexed { index, line ->
                        parseLibraryLine(line, custom, kinds?.getOrNull(index) ?: "card")?.let(cards::add)
                    }
                } ?: if (!custom) error("Argentum card library is missing from this build.") else Unit
            }
            return SupportedCardCatalog(cards)
        }

        internal fun parseLibraryLine(line: String, custom: Boolean, kind: String): SupportedCard? {
            val name = jsonString(line, "name")?.takeIf { it.isNotBlank() } ?: return null
            val identity = Regex("\"colorIdentityOverride\":\\[([^\\]]*)]").find(line)?.groupValues?.get(1)?.let { raw ->
                raw.split(',').mapNotNull { colorLetter(it.trim().trim('"')) }.toSet()
            }
            return SupportedCard(
                name = name,
                typeLine = jsonString(line, "typeLine").orEmpty(),
                manaCost = jsonString(line, "manaCost").orEmpty(),
                imageUrl = jsonString(line, "imageUri")?.takeIf { it.isNotBlank() },
                custom = custom,
                identity = identity,
                kind = kind,
            )
        }

        /** First string value for [key] in a JSON line, with standard escapes decoded. */
        internal fun jsonString(line: String, key: String): String? {
            val marker = "\"$key\":\""
            var i = line.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
            val out = StringBuilder()
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' && i + 1 < line.length -> {
                        when (val e = line[i + 1]) {
                            'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
                            'u' -> if (i + 5 < line.length) { out.append(line.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                            else -> out.append(e)
                        }
                        i += 2; continue
                    }
                    else -> out.append(c)
                }
                i++
            }
            return null
        }

        private fun colorLetter(raw: String): Char? = when (raw.uppercase(Locale.ROOT)) {
            "WHITE", "W" -> 'W'; "BLUE", "U" -> 'U'; "BLACK", "B" -> 'B'; "RED", "R" -> 'R'; "GREEN", "G" -> 'G'
            else -> null
        }
    }
}

private val CARD_TYPES = setOf("artifact", "battle", "creature", "enchantment", "instant", "land", "planeswalker", "sorcery", "kindred", "tribal")

/** Card types of the front face ("Legendary Creature — Elf" → {creature}). */
fun cardTypesOf(typeLine: String): Set<String> = typeLine.substringBefore(" // ").substringBefore("—").substringBefore(" - ")
    .lowercase(Locale.ROOT).split(' ').filter { it in CARD_TYPES }.toSet()

fun subtypesOf(typeLine: String): Set<String> = typeLine.substringBefore(" // ").substringAfter("—", "")
    .lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }.toSet()

/** Mana value of a cost such as "{2}{W/U}{G/P}" (X counts as 0; {2/W} counts as 2). */
fun manaValueOf(manaCost: String): Double = Regex("\\{([^}]+)}").findAll(manaCost).sumOf { match ->
    val symbol = match.groupValues[1].uppercase(Locale.ROOT)
    when {
        symbol.toIntOrNull() != null -> symbol.toInt().toDouble()
        symbol in setOf("X", "Y", "Z") -> 0.0
        symbol == "HW" || symbol == "HR" -> 0.5
        symbol.substringBefore('/').toIntOrNull() != null -> symbol.substringBefore('/').toDouble()
        else -> 1.0
    }
}

/** Colors appearing in a mana cost (W U B R G). */
fun colorsOf(manaCost: String): Set<Char> = Regex("\\{([^}]+)}").findAll(manaCost)
    .flatMap { m -> m.groupValues[1].uppercase(Locale.ROOT).asSequence().filter { it in "WUBRG" } }.toSet()

/**
 * Rewrites a saved deck list after the player swaps [copies] of [oldName] for [newName], keeping
 * every other line (headings, set codes, CSV) untouched. Returns null when no line names [oldName],
 * so callers never save a list that silently disagrees with the deck.
 */
fun replaceCardInDeckText(text: String, oldName: String, newName: String, copies: Int = 1): String? {
    if (copies < 1 || oldName.isBlank() || newName.isBlank()) return null
    // CSV exports are left alone: rewriting one row as plain text would corrupt the file.
    val csvHeader = text.lines().any { line ->
        val cells = line.lowercase(Locale.ROOT).split(',').map { it.trim().trim('"') }
        cells.size > 1 && cells.any { it == "name" || it == "card name" } && cells.any { it in setOf("count", "quantity", "amount") }
    }
    if (csvHeader) return null
    var remaining = copies
    var changed = false
    val added = mutableListOf<String>()
    val lines = text.lines().map { line ->
        if (remaining == 0) return@map line
        val parsed = parseDeckList(line).singleOrNull() ?: return@map line
        if (!parsed.name.equals(oldName.trim(), ignoreCase = true)) return@map line
        val take = minOf(remaining, parsed.count)
        remaining -= take
        changed = true
        val keep = parsed.count - take
        val replacement = "$take $newName"
        if (keep == 0) {
            if (parsed.commander && line.trim().startsWith("Commander:", ignoreCase = true)) "Commander: 1 $newName" else replacement
        } else {
            added += replacement
            val leading = Regex("^(\\s*(?:[xX]\\s*)?)(\\d+)").find(line)
            if (leading != null) line.replaceRange(leading.groups[2]!!.range, keep.toString()) else line
        }
    }.toMutableList()
    if (!changed) return null
    if (added.isNotEmpty()) {
        // Insert split-off copies right after the line they came from, inside the same section.
        val anchor = lines.indexOfFirst { l -> parseDeckList(l).singleOrNull()?.name?.equals(oldName.trim(), true) == true }
        if (anchor >= 0) lines.addAll(anchor + 1, added) else lines.addAll(added)
    }
    return lines.joinToString("\n")
}
