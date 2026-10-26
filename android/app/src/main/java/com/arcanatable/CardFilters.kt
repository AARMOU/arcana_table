package com.arcanatable

import java.util.Locale

/**
 * Structured card filters for the deck builder's FILTERS panel. They sit on top of the typed
 * search box ([CardQuery]) and run over the same offline [CardLibrary], so every result is a card
 * this build of Arcana can play. Pure Kotlin: no Android types, so it is unit-testable.
 *
 * Price, artist and finish filters are deliberately absent: the offline library ships one printing
 * per card and no market data, so those filters could only ever be guesses.
 */
enum class ColorMatch(val label: String) {
    INCLUDING("Including these"),
    EXACTLY("Exactly these"),
    AT_MOST("At most these"),
}

enum class StatField(val label: String) { MANA_VALUE("Mana value"), POWER("Power"), TOUGHNESS("Toughness") }

enum class StatOp(val symbol: String) { EQ("="), LT("<"), LTE("≤"), GT(">"), GTE("≥"), BETWEEN("from") }

/** One stat condition, e.g. mana value ≤ 3, or power from 2 to 4 (inclusive). */
data class StatRange(val stat: StatField, val op: StatOp, val value: Double, val upTo: Double? = null) {
    fun test(actual: Double): Boolean = when (op) {
        StatOp.EQ -> actual == value
        StatOp.LT -> actual < value
        StatOp.LTE -> actual <= value
        StatOp.GT -> actual > value
        StatOp.GTE -> actual >= value
        StatOp.BETWEEN -> actual >= value && actual <= (upTo ?: Double.MAX_VALUE)
    }

    val label: String
        get() = when (op) {
            StatOp.BETWEEN -> "${stat.label} ${fmtNumber(value)}–${upTo?.let(::fmtNumber) ?: "∞"}"
            else -> "${stat.label} ${op.symbol} ${fmtNumber(value)}"
        }
}

enum class CardSort(val label: String) {
    NAME("Name"), MANA_VALUE("Mana value"), COLOR("Color"), TYPE("Type"), RARITY("Rarity"), SET("Set"), POWER("Power"),
}

data class CardFilters(
    /** Every term must appear in the rules text. */
    val oracleTerms: List<String> = emptyList(),
    /** Every term must appear in the type line ("Legendary", "Creature", "Angel"). */
    val typeTerms: List<String> = emptyList(),
    /** Each cost must be contained in the card's mana cost: "2{G}{W}" or "2GW". */
    val manaCosts: List<String> = emptyList(),
    val colors: Set<Char> = emptySet(),
    /** Colorless only; ignores [colors]. */
    val colorless: Boolean = false,
    val colorMatch: ColorMatch = ColorMatch.INCLUDING,
    /** Compare against color identity (Commander) instead of the colors in the mana cost. */
    val useIdentity: Boolean = false,
    /** Number of colors, inclusive. 0..5 means any. */
    val minColors: Int = 0,
    val maxColors: Int = 5,
    /** Set codes of the shipped printing. */
    val sets: List<String> = emptyList(),
    /** True: exclude [sets] instead of requiring one of them. */
    val excludeSets: Boolean = false,
    /** COMMON / UNCOMMON / RARE / MYTHIC; empty means any. */
    val rarities: Set<String> = emptySet(),
    val keywords: List<String> = emptyList(),
    val stats: List<StatRange> = emptyList(),
    /** Hide cards marked not legal in Commander. Cards with no legality data (custom cards) stay. */
    val commanderLegalOnly: Boolean = true,
    val sort: CardSort = CardSort.NAME,
    val descending: Boolean = false,
) {
    /** How many filter groups are on, for the FILTERS badge. Sort and the legality default don't count. */
    val activeCount: Int get() = listOf(
        oracleTerms.isNotEmpty(), typeTerms.isNotEmpty(), manaCosts.isNotEmpty(),
        colorless || colors.isNotEmpty(), minColors > 0 || maxColors < 5, sets.isNotEmpty(),
        rarities.isNotEmpty(), keywords.isNotEmpty(), stats.isNotEmpty(), !commanderLegalOnly,
    ).count { it }

    /** Same filters, default sort, legality on: what "Clear all" resets to. */
    fun cleared(): CardFilters = CardFilters(sort = sort, descending = descending)

    private val manaNeeds: List<ManaCost> by lazy(LazyThreadSafetyMode.NONE) { manaCosts.mapNotNull(ManaCost::parse) }
    private val lowOracle by lazy(LazyThreadSafetyMode.NONE) { oracleTerms.map { it.lowercase(Locale.ROOT) } }
    private val lowTypes by lazy(LazyThreadSafetyMode.NONE) { typeTerms.map { it.lowercase(Locale.ROOT) } }
    private val lowKeywords by lazy(LazyThreadSafetyMode.NONE) { keywords.map { it.lowercase(Locale.ROOT) } }
    private val upperSets by lazy(LazyThreadSafetyMode.NONE) { sets.map { it.uppercase(Locale.ROOT) }.toSet() }

    fun matches(c: CardLibrary.Indexed): Boolean {
        if (commanderLegalOnly && c.card.commanderLegal == false) return false
        if (lowOracle.any { it !in c.oracle }) return false
        if (lowTypes.any { it !in c.type }) return false
        if (lowKeywords.any { k -> c.keywords.none { it == k } && c.oracle.lineSequence().none { it.trim().startsWith(k) } }) return false
        val have = if (useIdentity) c.identity else c.cardColors
        if (colorless) { if (have.isNotEmpty()) return false }
        else if (colors.isNotEmpty()) {
            val ok = when (colorMatch) {
                ColorMatch.EXACTLY -> have == colors
                ColorMatch.INCLUDING -> have.containsAll(colors)
                ColorMatch.AT_MOST -> colors.containsAll(have)
            }
            if (!ok) return false
        }
        if (have.size < minColors || have.size > maxColors) return false
        if (upperSets.isNotEmpty()) {
            val inSets = c.card.setCode?.uppercase(Locale.ROOT) in upperSets
            if (inSets == excludeSets) return false
        }
        if (rarities.isNotEmpty() && (c.card.rarity ?: "COMMON") !in rarities) return false
        for (s in stats) {
            val actual = when (s.stat) {
                StatField.MANA_VALUE -> c.mv
                StatField.POWER -> c.card.power?.toDoubleOrNull() ?: return false
                StatField.TOUGHNESS -> c.card.toughness?.toDoubleOrNull() ?: return false
            }
            if (!s.test(actual)) return false
        }
        if (manaNeeds.isNotEmpty()) {
            val cost = ManaCost.parse(c.card.manaCost.substringBefore(" // ")) ?: return false
            if (manaNeeds.any { !cost.contains(it) }) return false
        }
        return true
    }

    fun comparator(): Comparator<CardLibrary.Indexed> {
        val byName = compareBy<CardLibrary.Indexed> { it.name }
        val key: Comparator<CardLibrary.Indexed> = when (sort) {
            CardSort.NAME -> byName
            CardSort.MANA_VALUE -> compareBy<CardLibrary.Indexed> { it.mv }.then(byName)
            CardSort.COLOR -> compareBy<CardLibrary.Indexed> { colorRank(it.cardColors) }.then(byName)
            CardSort.TYPE -> compareBy<CardLibrary.Indexed> { DECK_GROUP_ORDER.indexOf(deckGroupFor(it.card)) }.thenBy { it.mv }.then(byName)
            CardSort.RARITY -> compareBy<CardLibrary.Indexed> { rarityRank(it.card.rarity) }.then(byName)
            CardSort.SET -> compareBy<CardLibrary.Indexed> { it.card.setCode.orEmpty() }.then(byName)
            // Cards with no numeric power sort last whichever way the list runs.
            CardSort.POWER -> compareBy<CardLibrary.Indexed> { it.card.power?.toDoubleOrNull() == null }
                .thenBy { it.card.power?.toDoubleOrNull() ?: 0.0 }.then(byName)
        }
        return if (descending && sort == CardSort.POWER) compareBy<CardLibrary.Indexed> { it.card.power?.toDoubleOrNull() == null }
            .thenByDescending { it.card.power?.toDoubleOrNull() ?: 0.0 }.then(byName)
        else if (descending) key.reversed() else key
    }

    /** Short removable chips describing what's on, each with the filters minus that part. */
    fun summaryChips(): List<Pair<String, CardFilters>> = buildList {
        oracleTerms.forEach { t -> add("Text: $t" to copy(oracleTerms = oracleTerms - t)) }
        typeTerms.forEach { t -> add("Type: $t" to copy(typeTerms = typeTerms - t)) }
        manaCosts.forEach { m -> add("Cost: $m" to copy(manaCosts = manaCosts - m)) }
        if (colorless) add("Colorless${if (useIdentity) " identity" else ""}" to copy(colorless = false))
        else if (colors.isNotEmpty()) add("${colorMatch.label.substringBefore(" ")} ${"WUBRG".filter { it in colors }}${if (useIdentity) " identity" else ""}" to copy(colors = emptySet()))
        if (minColors > 0 || maxColors < 5) add("${if (minColors == maxColors) "$minColors" else "$minColors–$maxColors"} colors" to copy(minColors = 0, maxColors = 5))
        if (sets.isNotEmpty()) add("${if (excludeSets) "Not set" else "Set"} ${sets.joinToString(", ") { it.uppercase(Locale.ROOT) }}" to copy(sets = emptyList(), excludeSets = false))
        if (rarities.isNotEmpty()) add(RARITY_ORDER.filter { it in rarities }.joinToString(", ") { it.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar) } to copy(rarities = emptySet()))
        keywords.forEach { k -> add("Keyword: $k" to copy(keywords = keywords - k)) }
        stats.forEach { s -> add(s.label to copy(stats = stats - s)) }
        if (!commanderLegalOnly) add("Including not Commander-legal" to copy(commanderLegalOnly = true))
    }

    // ---- Saved filter sets --------------------------------------------------------------------

    /** A line-based text form for saving presets; no JSON so it round-trips on the plain JVM too. */
    fun encode(): String = buildList {
        fun put(k: String, v: String) = add("$k\t${v.clean()}")
        fun list(k: String, v: List<String>) { if (v.isNotEmpty()) put(k, v.joinToString(SEP) { it.clean() }) }
        list("oracle", oracleTerms); list("type", typeTerms); list("mana", manaCosts)
        if (colors.isNotEmpty()) put("colors", "WUBRG".filter { it in colors })
        if (colorless) put("colorless", "1")
        if (colorMatch != ColorMatch.INCLUDING) put("match", colorMatch.name)
        if (useIdentity) put("identity", "1")
        if (minColors > 0 || maxColors < 5) put("count", "$minColors-$maxColors")
        list("sets", sets); if (excludeSets) put("excludeSets", "1")
        if (rarities.isNotEmpty()) put("rarity", rarities.sorted().joinToString(SEP))
        list("keywords", keywords)
        stats.forEach { s -> put("stat", "${s.stat.name}$SEP${s.op.name}$SEP${s.value}$SEP${s.upTo ?: ""}") }
        if (!commanderLegalOnly) put("legal", "0")
        if (sort != CardSort.NAME) put("sort", sort.name)
        if (descending) put("desc", "1")
    }.joinToString("\n")

    companion object {
        private const val SEP = "\u001F"
        private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

        /** Reads [encode] output. Unknown or broken lines are skipped, never fatal. */
        fun decode(text: String): CardFilters {
            var f = CardFilters()
            text.lineSequence().forEach { line ->
                val key = line.substringBefore('\t', "")
                val v = line.substringAfter('\t', "")
                if (key.isEmpty()) return@forEach
                val items = v.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }
                runCatching {
                    f = when (key) {
                        "oracle" -> f.copy(oracleTerms = items)
                        "type" -> f.copy(typeTerms = items)
                        "mana" -> f.copy(manaCosts = items)
                        "colors" -> f.copy(colors = v.uppercase(Locale.ROOT).filter { it in "WUBRG" }.toSet())
                        "colorless" -> f.copy(colorless = v == "1")
                        "match" -> f.copy(colorMatch = ColorMatch.valueOf(v))
                        "identity" -> f.copy(useIdentity = v == "1")
                        "count" -> v.split('-').let { (a, b) -> f.copy(minColors = a.toInt().coerceIn(0, 5), maxColors = b.toInt().coerceIn(0, 5)) }
                        "sets" -> f.copy(sets = items)
                        "excludeSets" -> f.copy(excludeSets = v == "1")
                        "rarity" -> f.copy(rarities = items.filter { it in RARITY_ORDER }.toSet())
                        "keywords" -> f.copy(keywords = items)
                        "stat" -> v.split(SEP).let { p ->
                            f.copy(stats = f.stats + StatRange(StatField.valueOf(p[0]), StatOp.valueOf(p[1]), p[2].toDouble(), p.getOrNull(3)?.toDoubleOrNull()))
                        }
                        "legal" -> f.copy(commanderLegalOnly = v != "0")
                        "sort" -> f.copy(sort = CardSort.valueOf(v))
                        "desc" -> f.copy(descending = v == "1")
                        else -> f
                    }
                }
            }
            return f
        }

        val RARITY_ORDER = listOf("COMMON", "UNCOMMON", "RARE", "MYTHIC")
        private fun rarityRank(r: String?) = RARITY_ORDER.indexOf(r ?: "COMMON").let { if (it < 0) 0 else it }

        /** White, blue, black, red, green, then multicolor, then colorless. */
        internal fun colorRank(colors: Set<Char>): Int = when {
            colors.isEmpty() -> 7
            colors.size > 1 -> 6
            else -> "WUBRG".indexOf(colors.single())
        }
    }
}

/** A named, saved set of filters ("Load" in the FILTERS panel). */
data class FilterPreset(val name: String, val filters: CardFilters)

object FilterPresets {
    private const val RS = "\u001E"

    fun encode(presets: List<FilterPreset>): String =
        presets.joinToString(RS) { p -> "name\t${p.name.replace('\t', ' ').replace('\n', ' ').trim()}\n${p.filters.encode()}" }

    fun decode(text: String): List<FilterPreset> = text.split(RS).mapNotNull { block ->
        val lines = block.lines()
        val name = lines.firstOrNull()?.takeIf { it.startsWith("name\t") }?.removePrefix("name\t")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        FilterPreset(name, CardFilters.decode(lines.drop(1).joinToString("\n")))
    }

    /** Replaces a preset with the same name (case-insensitive) or adds it first. */
    fun upsert(presets: List<FilterPreset>, preset: FilterPreset): List<FilterPreset> =
        listOf(preset) + presets.filterNot { it.name.equals(preset.name, ignoreCase = true) }
}

/**
 * A mana cost as symbol counts: generic amount plus one entry per other symbol ("G", "W", "G/W",
 * "X", "C"). Accepts Scryfall-style braces "{2}{G}{W}" or shorthand "2GW".
 */
data class ManaCost(val generic: Int, val symbols: Map<String, Int>) {
    /** True when this cost has at least every symbol of [need] (and at least its generic amount). */
    fun contains(need: ManaCost): Boolean =
        generic >= need.generic && need.symbols.all { (s, n) -> (symbols[s] ?: 0) >= n }

    companion object {
        fun parse(raw: String): ManaCost? {
            val text = raw.trim().uppercase(Locale.ROOT)
            if (text.isEmpty()) return ManaCost(0, emptyMap())
            var generic = 0
            val symbols = linkedMapOf<String, Int>()
            fun addSymbol(s: String) {
                val n = s.toIntOrNull()
                if (n != null) generic += n else symbols[s] = (symbols[s] ?: 0) + 1
            }
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                when {
                    ch == '{' -> {
                        val end = text.indexOf('}', i)
                        if (end < 0) return null
                        val inner = text.substring(i + 1, end).trim()
                        if (inner.isEmpty()) return null
                        addSymbol(inner)
                        i = end + 1
                    }
                    ch.isDigit() -> {
                        var j = i
                        while (j < text.length && text[j].isDigit()) j++
                        generic += text.substring(i, j).toInt()
                        i = j
                    }
                    ch in "WUBRGCXSYZ" -> { addSymbol(ch.toString()); i++ }
                    ch == ' ' || ch == ',' -> i++
                    else -> return null
                }
            }
            return ManaCost(generic, symbols)
        }
    }
}

internal fun fmtNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
