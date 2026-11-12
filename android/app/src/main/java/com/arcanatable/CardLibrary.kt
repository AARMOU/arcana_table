package com.arcanatable

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Offline search over the cards this build can actually play: the same [SupportedCardCatalog]
 * the importer and the offline engine read (Argentum's library + Arcana custom cards). Nothing
 * here asks Scryfall; Scryfall only adds pictures elsewhere. Pure Kotlin so it is unit-testable.
 *
 * Query language (a practical subset of Scryfall's, so players can reuse habits):
 * ```
 * bolt                 name contains "bolt" (all bare words must match; typo-tolerant fallback)
 * "sol ring"           quoted phrase in the name
 * t:creature  type:    type line contains
 * o:draw  oracle:      rules text contains (quote phrases: o:"draw a card")
 * c:ub  color:         card colors include U and B;  c=ub exact;  c<=ub at most;  c:c colorless
 * id:ub  identity:     color identity fits inside UB (Commander deck building); id:c colorless
 * mv<=3  cmc>=5  mv=2  mana value comparisons (also pow / tou / power / toughness)
 * k:flying  keyword:   keyword ability
 * r:mythic  rarity:    c / u / r / m or the full word
 * s:lea  set: e:       set code of the shipped printing
 * is:legendary  is:commander  is:basic  is:custom  is:dfc  is:partner
 * legal:commander  f:commander
 * -t:land              a leading minus negates any term
 * ```
 */
class CardLibrary(val catalog: SupportedCardCatalog) {

    /** Pre-lowered fields, so a search is one cheap pass over ~17k entries with no allocation per card. */
    class Indexed internal constructor(val card: SupportedCard) {
        val name = card.name.lowercase(Locale.ROOT)
        val allNames = (listOf(card.name) + card.faceNames).map { it.lowercase(Locale.ROOT) }
        val type = card.typeLine.lowercase(Locale.ROOT)
        val oracle = card.oracleText.orEmpty().lowercase(Locale.ROOT)
        val keywords = card.keywords.map { it.lowercase(Locale.ROOT) }
        val mv = card.manaValue
        val cardColors: Set<Char> = colorsOf(card.manaCost.substringBefore(" // "))
        val identity: Set<Char> = card.colors
        val words: List<String> = name.split(' ', ',', '-', '\'').filter { it.isNotBlank() }
        val legendary = type.substringBefore(" // ").contains("legendary")
        val canBeCommander = isCommanderEligible(card)
        val basic = card.kind == "basic" || type.startsWith("basic ")
    }

    val cards: List<Indexed> = catalog.playableCards.map(::Indexed)
    private val byName: Map<String, Indexed> = cards.associateBy { it.name }

    val size: Int get() = cards.size

    fun indexed(name: String): Indexed? = catalog.find(name)?.let { byName[it.name.lowercase(Locale.ROOT)] }

    data class Result(val cards: List<SupportedCard>, val total: Int, val fuzzy: Boolean, val errors: List<String>)

    /** Runs [query]; a blank query lists everything (useful with filters from the UI). */
    fun search(query: String, limit: Int = 300): Result {
        val parsed = CardQuery.parse(query)
        val matches = cards.filter { parsed.matches(it) }
        if (matches.isNotEmpty() || parsed.nameWords.isEmpty()) {
            return Result(rank(matches, parsed).take(limit).map { it.card }, matches.size, false, parsed.errors)
        }
        // Nothing contained those name words: tolerate typos, keeping every non-name filter.
        val fuzzy = cards.asSequence().filter { parsed.matchesIgnoringName(it) }
            .mapNotNull { c -> fuzzyNameScore(parsed.nameWords, c)?.let { c to it } }
            .sortedWith(compareBy({ it.second }, { it.first.name })).map { it.first }.toList()
        return Result(fuzzy.take(limit).map { it.card }, fuzzy.size, fuzzy.isNotEmpty(), parsed.errors)
    }

    /**
     * [search] plus the FILTERS panel. Name relevance wins while sorting by name with words typed;
     * any other sort uses [CardFilters.comparator]. Typos still fall back to close names.
     */
    fun search(query: String, filters: CardFilters, limit: Int = 300): Result {
        val parsed = CardQuery.parse(query)
        val matches = cards.filter { parsed.matches(it) && filters.matches(it) }
        fun order(list: List<Indexed>) =
            if (filters.sort == CardSort.NAME && !filters.descending && parsed.nameWords.isNotEmpty()) rank(list, parsed)
            else list.sortedWith(filters.comparator())
        if (matches.isNotEmpty() || parsed.nameWords.isEmpty()) {
            return Result(order(matches).take(limit).map { it.card }, matches.size, false, parsed.errors)
        }
        val fuzzy = cards.asSequence().filter { parsed.matchesIgnoringName(it) && filters.matches(it) }
            .mapNotNull { c -> fuzzyNameScore(parsed.nameWords, c)?.let { c to it } }
            .sortedWith(compareBy({ it.second }, { it.first.name })).map { it.first }.toList()
        return Result(fuzzy.take(limit).map { it.card }, fuzzy.size, fuzzy.isNotEmpty(), parsed.errors)
    }

    /** Set codes present in this library, for the Set picker. */
    val setCodes: List<String> by lazy { cards.mapNotNull { it.card.setCode?.uppercase(Locale.ROOT) }.distinct().sorted() }

    /** Type-ahead name suggestions: prefix matches first, then word starts. */
    fun suggestNames(prefix: String, limit: Int = 8): List<String> {
        val q = prefix.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        val starts = cards.filter { it.name.startsWith(q) }
        val words = cards.filter { c -> !c.name.startsWith(q) && c.words.any { it.startsWith(q) } }
        return (starts.sortedBy { it.name } + words.sortedBy { it.name }).take(limit).map { it.card.name }
    }

    private fun rank(list: List<Indexed>, q: CardQuery): List<Indexed> {
        if (q.nameWords.isEmpty()) return list.sortedBy { it.name }
        val phrase = q.nameWords.joinToString(" ")
        fun score(c: Indexed) = when {
            c.allNames.any { it == phrase } -> 0
            c.name.startsWith(phrase) -> 1
            c.words.any { it.startsWith(q.nameWords.first()) } -> 2
            else -> 3
        }
        return list.sortedWith(compareBy({ score(it) }, { it.name }))
    }

    /** Lower is better; null means too far from every name word. */
    private fun fuzzyNameScore(words: List<String>, c: Indexed): Int? {
        fun distanceIn(name: String, w: String): Int? = name.split(' ', ',', '-', '\'').filter { it.isNotBlank() }
            .minOfOrNull { word -> if (word.startsWith(w)) 0 else editDistance(w, word) }
        fun scoreFor(name: String): Int? {
            var total = 0
            for (w in words) {
                val allowed = when { w.length <= 3 -> 0; w.length <= 6 -> 1; else -> 2 }
                val best = distanceIn(name, w) ?: return null
                if (best > allowed) return null
                total += best
            }
            return total
        }
        // The card's own name beats a match on its back face or other half.
        val primary = scoreFor(c.name)
        val faces = c.allNames.drop(1).mapNotNull(::scoreFor).minOrNull()?.plus(1)
        return listOfNotNull(primary, faces).minOrNull()
    }

    // ---- Replacement suggestions -------------------------------------------------------------

    /** What we know about a card the library lacks (from Scryfall when online, or from the list). */
    data class Original(
        val name: String,
        val typeLine: String,
        val manaValue: Double?,
        val colors: Set<Char>,
        val oracleText: String? = null,
        val keywords: List<String> = emptyList(),
    )

    data class Suggestion(val card: SupportedCard, val reasons: List<String>, val score: Double)

    /**
     * Explainable, conservative swap candidates from the playable library only. Hard rules:
     * same card type, inside [allowedIdentity], not already in the deck, a legendary creature when
     * replacing a commander. Then a simple additive score; every point is shown to the player as a
     * reason. Nothing is ever substituted without the player choosing it.
     */
    fun suggestReplacements(
        original: Original,
        allowedIdentity: Set<Char>?,
        exclude: Set<String> = emptySet(),
        forCommander: Boolean = false,
        limit: Int = 6,
    ): List<Suggestion> {
        val types = cardTypesOf(original.typeLine)
        if (types.isEmpty()) return emptyList()
        val subtypes = subtypesOf(original.typeLine)
        val excluded = exclude.map { it.lowercase(Locale.ROOT) }.toSet() + original.name.lowercase(Locale.ROOT)
        val identity = allowedIdentity ?: original.colors
        val origWords = significantWords(original.oracleText.orEmpty())
        val origKeywords = original.keywords.map { it.lowercase(Locale.ROOT) }.toSet()
        val wantsBasic = original.typeLine.contains("Basic", ignoreCase = true)
        return cards.asSequence()
            .filter { it.name !in excluded }
            .filter { it.card.cardTypes.intersect(types).isNotEmpty() }
            .filter { it.identity.all { color -> color in identity } }
            .filter { wantsBasic || !it.basic }
            .filter { !forCommander || it.canBeCommander }
            .map { c ->
                val reasons = mutableListOf<String>()
                var score = 0.0
                val sharedTypes = c.card.cardTypes.intersect(types)
                if (c.card.cardTypes == types) { score += 3.0; reasons += "Same type (${types.joinToString(" ")})" }
                else { score += 1.5; reasons += "Shares type ${sharedTypes.joinToString(" ")}" }
                original.manaValue?.let { mv ->
                    val d = abs(c.mv - mv)
                    score += max(0.0, 3.0 - d * 1.5)
                    if (d == 0.0) reasons += "Same mana value (${fmtMv(mv)})" else if (d <= 1.0) reasons += "Mana value ${fmtMv(c.mv)} vs ${fmtMv(mv)}"
                }
                if (subtypes.isNotEmpty()) c.card.subtypes.intersect(subtypes).takeIf { it.isNotEmpty() }?.let {
                    score += 1.0; reasons += "Shares ${it.joinToString(", ") { s -> s.replaceFirstChar(Char::uppercaseChar) }}"
                }
                val sharedKw = c.keywords.filter { it in origKeywords }
                if (sharedKw.isNotEmpty()) { score += sharedKw.size.coerceAtMost(3) * 0.8; reasons += "Keywords: ${sharedKw.joinToString(", ")}" }
                if (origWords.isNotEmpty()) {
                    val theirs = significantWords(c.oracle)
                    val overlap = origWords.intersect(theirs)
                    val jaccard = overlap.size.toDouble() / (origWords.size + theirs.size - overlap.size).coerceAtLeast(1)
                    if (jaccard >= 0.12) { score += jaccard * 6.0; reasons += "Similar rules text (${overlap.sorted().take(4).joinToString(", ")})" }
                }
                if (c.identity == original.colors && original.colors.isNotEmpty()) { score += 0.5; reasons += "Same colors" }
                if (c.card.custom) score -= 0.25
                Suggestion(c.card, reasons, score)
            }
            .sortedWith(compareBy<Suggestion>({ -it.score }, { it.card.name }))
            .take(limit).toList()
    }

    companion object {
        fun isCommanderEligible(card: SupportedCard): Boolean {
            val front = card.typeLine.substringBefore(" // ").lowercase(Locale.ROOT)
            return ("legendary" in front && "creature" in front) ||
                card.oracleText.orEmpty().contains("can be your commander", ignoreCase = true)
        }

        private val STOP = setOf("the", "a", "an", "of", "to", "and", "or", "you", "your", "it", "its", "that", "this", "is", "are",
            "on", "in", "for", "with", "as", "at", "by", "from", "may", "card", "cards", "each", "until", "end", "turn", "if", "then", "target", "one")

        internal fun significantWords(text: String): Set<String> = text.lowercase(Locale.ROOT)
            .replace(Regex("\\{[^}]*\\}"), " ").split(Regex("[^a-z]+")).filter { it.length > 2 && it !in STOP }.toSet()

        private fun fmtMv(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

        /** Damerau–Levenshtein (optimal string alignment) distance. */
        internal fun editDistance(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
            return d[a.length][b.length]
        }
    }
}

/** A parsed search: name words plus filters. Unknown operators are reported, never silently ignored. */
class CardQuery private constructor(
    val nameWords: List<String>,
    private val filters: List<(CardLibrary.Indexed) -> Boolean>,
    val errors: List<String>,
) {
    fun matches(c: CardLibrary.Indexed): Boolean = matchesIgnoringName(c) && nameWords.all { w -> c.allNames.any { it.contains(w) } }
    fun matchesIgnoringName(c: CardLibrary.Indexed): Boolean = filters.all { it(c) }

    companion object {
        private val TERM = Regex("""(-?)([a-zA-Z]+)(:|<=|>=|!=|=|<|>)("[^"]*"|\S+)|(-?)"([^"]*)"|(\S+)""")

        fun parse(raw: String): CardQuery {
            val names = mutableListOf<String>()
            val filters = mutableListOf<(CardLibrary.Indexed) -> Boolean>()
            val errors = mutableListOf<String>()
            TERM.findAll(raw.trim()).forEach { m ->
                when {
                    m.groupValues[2].isNotEmpty() -> {
                        val negate = m.groupValues[1] == "-"
                        val key = m.groupValues[2].lowercase(Locale.ROOT)
                        val op = m.groupValues[3]
                        val value = m.groupValues[4].trim('"').lowercase(Locale.ROOT)
                        val f = filterFor(key, op, value)
                        if (f == null) {
                            errors += "Didn't understand “${m.value}”"
                            // Treat an unknown key:value as plain name text rather than dropping it.
                            names += m.value.lowercase(Locale.ROOT).trim('"')
                        } else filters += if (negate) { c -> !f(c) } else f
                    }
                    m.groupValues[6].isNotEmpty() || m.value.startsWith("\"") || m.value.startsWith("-\"") -> {
                        val phrase = m.groupValues[6].lowercase(Locale.ROOT)
                        if (m.groupValues[5] == "-") filters += { c -> c.allNames.none { it.contains(phrase) } }
                        else if (phrase.isNotBlank()) names += phrase
                    }
                    else -> {
                        val word = m.groupValues[7].lowercase(Locale.ROOT)
                        if (word.startsWith("-") && word.length > 1) { val w = word.drop(1); filters += { c -> c.allNames.none { it.contains(w) } } }
                        else if (word.isNotBlank()) names += word
                    }
                }
            }
            return CardQuery(names, filters, errors)
        }

        private fun colorSet(value: String): Set<Char>? {
            val named = mapOf("white" to "w", "blue" to "u", "black" to "b", "red" to "r", "green" to "g", "colorless" to "c",
                "azorius" to "wu", "dimir" to "ub", "rakdos" to "br", "gruul" to "rg", "selesnya" to "gw",
                "orzhov" to "wb", "izzet" to "ur", "golgari" to "bg", "boros" to "rw", "simic" to "gu",
                "esper" to "wub", "grixis" to "ubr", "jund" to "brg", "naya" to "rgw", "bant" to "gwu",
                "abzan" to "wbg", "jeskai" to "urw", "sultai" to "bgu", "mardu" to "rwb", "temur" to "gur")
            val v = named[value] ?: value
            if (v.isEmpty() || v.any { it !in "wubrgcm" }) return null
            return v.uppercase(Locale.ROOT).filter { it in "WUBRG" }.toSet()
        }

        private fun compare(op: String, actual: Double, target: Double): Boolean = when (op) {
            ":", "=" -> actual == target; "!=" -> actual != target
            "<" -> actual < target; "<=" -> actual <= target; ">" -> actual > target; ">=" -> actual >= target
            else -> false
        }

        private fun filterFor(key: String, op: String, v: String): ((CardLibrary.Indexed) -> Boolean)? = when (key) {
            "t", "type" -> if (op == ":" || op == "=") { c -> c.type.contains(v) } else null
            "o", "oracle", "text" -> if (op == ":" || op == "=") { c -> c.oracle.contains(v) } else null
            "n", "name" -> if (op == ":" || op == "=") { c -> c.allNames.any { it.contains(v) } } else null
            "k", "kw", "keyword" -> { c -> c.keywords.any { it == v || it.replace(' ', '_') == v } || c.oracle.lineSequence().any { it.trim().startsWith(v) } }
            "s", "set", "e", "edition" -> { c -> c.card.setCode.equals(v, ignoreCase = true) }
            "r", "rarity" -> {
                val want = when (v) { "c", "common" -> "COMMON"; "u", "uncommon" -> "UNCOMMON"; "r", "rare" -> "RARE"; "m", "mythic" -> "MYTHIC"; else -> null }
                want?.let { w -> { c: CardLibrary.Indexed -> c.card.rarity == w } }
            }
            "mv", "cmc", "manavalue" -> v.toDoubleOrNull()?.let { t -> { c: CardLibrary.Indexed -> compare(op, c.mv, t) } }
            "pow", "power" -> v.toDoubleOrNull()?.let { t -> { c: CardLibrary.Indexed -> c.card.power?.toDoubleOrNull()?.let { compare(op, it, t) } == true } }
            "tou", "toughness" -> v.toDoubleOrNull()?.let { t -> { c: CardLibrary.Indexed -> c.card.toughness?.toDoubleOrNull()?.let { compare(op, it, t) } == true } }
            "c", "color", "colour" -> colorSet(v)?.let { want ->
                val colorless = v == "c" || v == "colorless"
                val multi = v == "m"
                val f: (CardLibrary.Indexed) -> Boolean = { c ->
                    val have = c.cardColors
                    when {
                        colorless -> have.isEmpty()
                        multi -> have.size >= 2
                        op == "=" -> have == want
                        op == "<=" || op == "<" -> have.all { it in want } && (op == "<=" || have != want)
                        op == "!=" -> have != want
                        else -> have.containsAll(want)
                    }
                }
                f
            }
            "id", "identity", "ci" -> colorSet(v)?.let { want ->
                val colorless = v == "c" || v == "colorless"
                val f: (CardLibrary.Indexed) -> Boolean = { c ->
                    when {
                        colorless -> c.identity.isEmpty()
                        op == "=" -> c.identity == want
                        op == ">=" || op == ">" -> c.identity.containsAll(want)
                        else -> c.identity.all { it in want } // Scryfall's id: is "fits inside"
                    }
                }
                f
            }
            "is" -> when (v) {
                "legendary" -> { c -> c.legendary }
                "commander" -> { c -> c.canBeCommander }
                "basic" -> { c -> c.basic }
                "custom" -> { c -> c.card.custom }
                "dfc", "mdfc", "transform", "split" -> { c -> c.card.faceNames.isNotEmpty() }
                "partner" -> { c -> Regex("(?m)^partner").containsMatchIn(c.oracle) }
                "colorless" -> { c -> c.cardColors.isEmpty() }
                "multicolor", "multicolored", "gold" -> { c -> c.cardColors.size >= 2 }
                else -> null
            }
            "legal", "f", "format" -> if (v == "commander" || v == "edh") { c -> c.card.commanderLegal == true } else null
            else -> null
        }
    }
}
