package com.arcanatable

import java.util.Locale

/**
 * The deck builder's deck. It is stored as ordinary deck-list text in the existing saved-deck list
 * ([SavedDeck.text]), the same text the importer writes and the game already loads, so:
 *  - existing saved decks need no migration and are never rewritten unless the player edits them;
 *  - a deck built here and a pasted import take the identical path into Argentum.
 *
 * Nothing here decides how a card plays. Validation only reports; it never edits the deck.
 */
data class DeckLine(val name: String, val count: Int)

data class DeckDraft(
    val name: String,
    val commanders: List<String> = emptyList(),
    val main: List<DeckLine> = emptyList(),
    val sideboard: List<DeckLine> = emptyList(),
    val companion: String? = null,
    /** Cards being considered: saved with the deck, never part of the 100 or the game. */
    val maybeboard: List<DeckLine> = emptyList(),
    /** Import lines the parser could not read; kept so nothing the player typed is lost. */
    val unreadLines: List<String> = emptyList(),
) {
    val mainCount: Int get() = main.sumOf { it.count }
    /** Cards in the 100: commanders plus main deck (sideboard and companion are outside it). */
    val deckSize: Int get() = mainCount + commanders.size

    fun countOf(card: String): Int = main.firstOrNull { it.name.equals(card, true) }?.count ?: 0

    /**
     * Deck text both parsers read the same way: `parseDeckList` (game load) takes Commander + Deck
     * and skips everything after `Sideboard`; `parseDeckListDetailed` (import/validation) also
     * keeps the Sideboard and Companion zones. Companion is written after a Sideboard heading so
     * the older parser can never put it into the library.
     */
    fun toText(): String = buildString {
        if (commanders.isNotEmpty()) {
            appendLine("Commander")
            commanders.forEach { appendLine("1 $it") }
            appendLine()
        }
        appendLine("Deck")
        main.forEach { appendLine("${it.count} ${it.name}") }
        if (sideboard.isNotEmpty() || companion != null || maybeboard.isNotEmpty() || unreadLines.isNotEmpty()) {
            appendLine()
            appendLine("Sideboard")
            sideboard.forEach { appendLine("${it.count} ${it.name}") }
            companion?.let { appendLine(); appendLine("Companion"); appendLine("1 $it") }
            // Both parsers keep Maybeboard out of the game deck (the importer reads it as excluded).
            if (maybeboard.isNotEmpty()) { appendLine(); appendLine("Maybeboard"); maybeboard.forEach { appendLine("${it.count} ${it.name}") } }
            if (unreadLines.isNotEmpty()) { appendLine(); unreadLines.forEach { appendLine("// unread: $it") } }
        }
    }.trimEnd() + "\n"

    // ---- Edits (all return a new draft; the editor keeps history for undo) ---------------------

    fun add(card: String, copies: Int = 1): DeckDraft {
        if (copies < 1) return this
        val existing = main.indexOfFirst { it.name.equals(card, true) }
        val next = if (existing >= 0) main.toMutableList().also { it[existing] = it[existing].copy(count = it[existing].count + copies) }
        else main + DeckLine(card, copies)
        return copy(main = next)
    }

    fun remove(card: String, copies: Int = 1): DeckDraft {
        val next = main.mapNotNull { if (it.name.equals(card, true)) it.copy(count = it.count - copies).takeIf { l -> l.count > 0 } else it }
        return copy(main = next)
    }

    fun setCount(card: String, count: Int): DeckDraft = when {
        count <= 0 -> copy(main = main.filterNot { it.name.equals(card, true) })
        main.any { it.name.equals(card, true) } -> copy(main = main.map { if (it.name.equals(card, true)) it.copy(count = count) else it })
        else -> copy(main = main + DeckLine(card, count))
    }

    /** Makes [card] a commander, taking one copy out of the main deck if it was there. */
    fun setCommander(card: String, asSecond: Boolean = false): DeckDraft {
        val withoutCard = remove(card, 1)
        val others = commanders.filterNot { it.equals(card, true) }
        val next = if (asSecond && others.size == 1) others + card else listOf(card)
        // A replaced commander goes back into the main deck rather than disappearing.
        val displaced = commanders.filterNot { c -> next.any { it.equals(c, true) } }
        return displaced.fold(withoutCard.copy(commanders = next)) { d, c -> d.add(c) }
    }

    fun removeCommander(card: String, toMain: Boolean = true): DeckDraft {
        val next = copy(commanders = commanders.filterNot { it.equals(card, true) })
        return if (toMain) next.add(card) else next
    }

    fun moveToSideboard(card: String): DeckDraft {
        val copies = countOf(card).takeIf { it > 0 } ?: return this
        val side = sideboard.indexOfFirst { it.name.equals(card, true) }
        val nextSide = if (side >= 0) sideboard.toMutableList().also { it[side] = it[side].copy(count = it[side].count + copies) } else sideboard + DeckLine(card, copies)
        return copy(main = main.filterNot { it.name.equals(card, true) }, sideboard = nextSide)
    }

    fun moveToMain(card: String): DeckDraft {
        val line = sideboard.firstOrNull { it.name.equals(card, true) } ?: return this
        return copy(sideboard = sideboard - line).add(line.name, line.count)
    }

    /** Moves every main-deck copy of [card] to the maybeboard. */
    fun moveToMaybeboard(card: String): DeckDraft {
        val copies = countOf(card).takeIf { it > 0 } ?: return this
        return copy(main = main.filterNot { it.name.equals(card, true) }, maybeboard = addLine(maybeboard, card, copies))
    }

    /** Adds a card straight to the maybeboard (from search), one copy. */
    fun addToMaybeboard(card: String): DeckDraft =
        if (maybeboard.any { it.name.equals(card, true) }) this else copy(maybeboard = addLine(maybeboard, card, 1))

    fun moveFromMaybeboard(card: String): DeckDraft {
        val line = maybeboard.firstOrNull { it.name.equals(card, true) } ?: return this
        return copy(maybeboard = maybeboard - line).add(line.name, line.count)
    }

    fun removeFromMaybeboard(card: String): DeckDraft = copy(maybeboard = maybeboard.filterNot { it.name.equals(card, true) })

    private fun addLine(lines: List<DeckLine>, card: String, copies: Int): List<DeckLine> {
        val i = lines.indexOfFirst { it.name.equals(card, true) }
        return if (i >= 0) lines.toMutableList().also { it[i] = it[i].copy(count = it[i].count + copies) } else lines + DeckLine(card, copies)
    }

    /** Swaps every main-deck copy of [old] for [new] (or the commander). Only ever called on a player's choice. */
    fun replace(old: String, new: String): DeckDraft = when {
        commanders.any { it.equals(old, true) } -> copy(commanders = commanders.map { if (it.equals(old, true)) new else it })
        else -> {
            val copies = countOf(old)
            if (copies == 0) this else remove(old, copies).add(new, copies)
        }
    }

    companion object {
        /** Reads saved or imported text with the zone-aware importer parser; never drops a zone. */
        fun fromText(name: String, text: String, catalog: SupportedCardCatalog?): DeckDraft {
            val parsed = parseDeckListDetailed(text, catalog)
            fun lines(entries: List<DeckEntry>) = entries.groupBy { it.name.lowercase(Locale.ROOT) }
                .map { (_, group) -> DeckLine(catalog?.find(group.first().name)?.name ?: group.first().name, group.sumOf { it.count }) }
            val commanders = parsed.commanders.flatMap { e -> List(e.count) { catalog?.find(e.name)?.name ?: e.name } }
            return DeckDraft(
                name = name,
                commanders = commanders,
                main = lines(parsed.main),
                sideboard = lines(parsed.sideboard),
                companion = parsed.companion.firstOrNull()?.let { catalog?.find(it.name)?.name ?: it.name },
                maybeboard = lines(parsed.excluded),
                // Lines kept from an earlier save come back as comments; carry them forward so a
                // second save can never drop something the player once typed.
                unreadLines = (text.lines().map { it.trim() }.filter { it.startsWith("// unread: ") }.map { it.removePrefix("// unread: ") } +
                    parsed.diagnostics.map { it.original.trim() }).distinct(),
            )
        }
    }
}

/**
 * The deck builder ships in developer builds first so it can be tried on the Fold without
 * changing release behavior. Flip this (owner decision) to release it.
 */
internal object DeckBuilderFlag {
    val enabled: Boolean get() = true // Owner-approved Deck Editor in next release-look test APK
}

// ---- Validation -----------------------------------------------------------------------------

enum class IssueLevel { ERROR, WARNING }

data class DeckIssue(val level: IssueLevel, val message: String, val cards: List<String> = emptyList())

/**
 * Commander deck checks. Reports problems; never changes the deck. Gameplay rules (commander tax,
 * commander damage, zone changes) stay in Argentum, not here.
 */
fun validateCommanderDeck(deck: DeckDraft, catalog: SupportedCardCatalog): List<DeckIssue> {
    val issues = mutableListOf<DeckIssue>()
    val commanders = deck.commanders.map { it to catalog.find(it) }

    // Commanders
    when {
        commanders.isEmpty() -> issues += DeckIssue(IssueLevel.ERROR, "Choose a commander.")
        commanders.size > 2 -> issues += DeckIssue(IssueLevel.ERROR, "A deck can have at most two commanders.", deck.commanders)
        else -> {
            commanders.filter { (_, c) -> c != null && !CardLibrary.isCommanderEligible(c) }.forEach { (n, _) ->
                issues += DeckIssue(IssueLevel.ERROR, "$n can't be a commander (it isn't a legendary creature).", listOf(n))
            }
            if (commanders.size == 2) {
                if (!hasLegalCommanderPair(deck.commanders, catalog)) issues += DeckIssue(IssueLevel.ERROR,
                    "These two can't share command: both need Partner, Friends forever, matching “Partner with”, or a Background pair.", deck.commanders)
                issues += DeckIssue(IssueLevel.WARNING, "Arcana games have one commander slot for now, so this deck can be built and saved but not played yet.", deck.commanders)
            }
        }
    }
    if (deck.companion != null) issues += DeckIssue(IssueLevel.WARNING, "Companions are saved with the deck but can't be used in Arcana games yet.", listOf(deck.companion))

    // Size
    if (deck.deckSize != 100) issues += DeckIssue(IssueLevel.ERROR,
        "Commander decks have exactly 100 cards including the commander; this one has ${deck.deckSize}.")

    // Singleton
    val all = deck.main.map { it.name to it.count } + deck.commanders.map { it to 1 }
    all.groupBy({ it.first.lowercase(Locale.ROOT) }, { it }).forEach { (_, group) ->
        val total = group.sumOf { it.second }
        val name = group.first().first
        if (total > 1 && !allowsMultiple(name, catalog, total)) {
            issues += DeckIssue(IssueLevel.ERROR, "Only one copy of $name is allowed (deck has $total).", listOf(name))
        }
    }

    // Unsupported, not Commander-legal, color identity
    val identity = commanders.flatMap { (_, c) -> c?.colors.orEmpty() }.toSet()
    val knownCommanders = commanders.all { it.second != null } && commanders.isNotEmpty()
    val unsupported = mutableListOf<String>()
    val offColor = mutableListOf<String>()
    val notLegal = mutableListOf<String>()
    (deck.commanders + deck.main.map { it.name }).distinctBy { it.lowercase(Locale.ROOT) }.forEach { name ->
        val card = catalog.find(name)
        if (card == null) { unsupported += name; return@forEach }
        if (card.commanderLegal == false) notLegal += card.name
        if (knownCommanders && name !in deck.commanders && !card.colors.all { it in identity }) offColor += card.name
    }
    if (unsupported.isNotEmpty()) issues += DeckIssue(IssueLevel.ERROR,
        "${unsupported.size} card${if (unsupported.size == 1) " isn't" else "s aren't"} in this build's card library, so Arcana can't play ${if (unsupported.size == 1) "it" else "them"}. Replace or remove before playing.", unsupported)
    if (offColor.isNotEmpty()) issues += DeckIssue(IssueLevel.ERROR,
        "${offColor.size} card${if (offColor.size == 1) " is" else "s are"} outside your commander's colors (${identityLabel(identity)}).", offColor)
    if (notLegal.isNotEmpty()) issues += DeckIssue(IssueLevel.WARNING, "Not legal in Commander (banned or not a Commander card): ${notLegal.joinToString()}.", notLegal)
    return issues
}

/** Basic lands and "any number of cards named …" / "up to seven …" cards may repeat. */
internal fun allowsMultiple(name: String, catalog: SupportedCardCatalog, copies: Int): Boolean {
    val card = catalog.find(name) ?: return name.trim().lowercase(Locale.ROOT) in BASIC_NAMES
    if (card.kind == "basic" || card.typeLine.startsWith("Basic ", ignoreCase = true)) return true
    val text = card.oracleText.orEmpty().lowercase(Locale.ROOT)
    if ("a deck can have any number of cards named" in text) return true
    val upTo = Regex("a deck can have up to (\\w+) cards named").find(text)?.groupValues?.get(1)
    val limit = mapOf("two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)[upTo]
    return limit != null && copies <= limit
}

private val BASIC_NAMES = setOf("plains", "island", "swamp", "mountain", "forest", "wastes",
    "snow-covered plains", "snow-covered island", "snow-covered swamp", "snow-covered mountain", "snow-covered forest")

fun identityLabel(colors: Set<Char>): String = if (colors.isEmpty()) "colorless" else "WUBRG".filter { it in colors }

// ---- Statistics -----------------------------------------------------------------------------

data class DeckStats(
    val total: Int,
    val creatures: Int,
    val lands: Int,
    val nonlandSpells: Int,
    /** Index = mana value 0..6, last bucket is 7+. Nonland cards only. */
    val curve: List<Int>,
    /** Colored mana symbols in costs, by color letter. */
    val pips: Map<Char, Int>,
    val averageManaValue: Double,
    val identity: Set<Char>,
    val unsupported: Int,
    val byType: Map<String, Int>,
    /** Lands that can make each color (W U B R G), to compare with [pips]. */
    val landSources: Map<Char, Int> = emptyMap(),
)

fun deckStats(deck: DeckDraft, catalog: SupportedCardCatalog): DeckStats {
    var creatures = 0; var lands = 0; var unsupported = 0
    val curve = IntArray(8)
    val pips = linkedMapOf('W' to 0, 'U' to 0, 'B' to 0, 'R' to 0, 'G' to 0)
    val byType = linkedMapOf<String, Int>()
    val sources = linkedMapOf('W' to 0, 'U' to 0, 'B' to 0, 'R' to 0, 'G' to 0)
    var mvSum = 0.0; var nonland = 0
    val lines = deck.commanders.map { DeckLine(it, 1) } + deck.main
    for (line in lines) {
        val card = catalog.find(line.name)
        if (card == null) { unsupported += line.count; continue }
        val types = card.cardTypes
        val group = deckGroupFor(card)
        byType[group] = (byType[group] ?: 0) + line.count
        if ("creature" in types) creatures += line.count
        if ("land" in types) {
            lands += line.count
            landColors(card, identityOf(deck, catalog)).forEach { c -> sources[c] = sources.getValue(c) + line.count }
            continue
        }
        nonland += line.count
        mvSum += card.manaValue * line.count
        curve[card.manaValue.toInt().coerceIn(0, 7)] += line.count
        Regex("\\{([^}]+)\\}").findAll(card.manaCost.substringBefore(" // ")).forEach { m ->
            m.groupValues[1].uppercase(Locale.ROOT).filter { it in "WUBRG" }.forEach { c -> pips[c] = pips.getValue(c) + line.count }
        }
    }
    val identity = deck.commanders.flatMap { catalog.find(it)?.colors.orEmpty() }.toSet()
    return DeckStats(deck.deckSize, creatures, lands, nonland, curve.toList(), pips, if (nonland == 0) 0.0 else mvSum / nonland, identity, unsupported, byType, sources)
}

private fun identityOf(deck: DeckDraft, catalog: SupportedCardCatalog): Set<Char> =
    deck.commanders.flatMap { catalog.find(it)?.colors.orEmpty() }.toSet()

/**
 * Colors a land can make, read from its basic land types and "Add {X}" text. "Any color" and
 * fetch-style lands count for every color the commander allows (all five with no commander).
 * A guide for the stats panel only; how the land really works is Argentum's job.
 */
internal fun landColors(card: SupportedCard, identity: Set<Char>): Set<Char> {
    val type = card.typeLine.substringBefore(" // ").lowercase(Locale.ROOT)
    val text = card.oracleText.orEmpty().lowercase(Locale.ROOT)
    val out = mutableSetOf<Char>()
    mapOf("plains" to 'W', "island" to 'U', "swamp" to 'B', "mountain" to 'R', "forest" to 'G').forEach { (t, c) -> if (t in type) out += c }
    Regex("add[^.]*").findAll(text).forEach { m ->
        Regex("\\{([wubrg])\\}").findAll(m.value).forEach { out += it.groupValues[1].uppercase(Locale.ROOT)[0] }
    }
    val wide = identity.ifEmpty { "WUBRG".toSet() }
    if ("mana of any color" in text || "mana of any one color" in text || "any color" in text && "add" in text) out += wide
    if ("search your library for a basic land" in text) out += wide
    // Name the types fetch lands and dual-fetches look for ("Forest or Plains card").
    if ("search your library for" in text) mapOf("plains" to 'W', "island" to 'U', "swamp" to 'B', "mountain" to 'R', "forest" to 'G')
        .forEach { (t, c) -> if (Regex("\\b$t\\b").containsMatchIn(text.substringAfter("search your library for"))) out += c }
    return out
}

/** One group per card for type grouping; the order follows how players usually read a list. */
fun deckGroupFor(card: SupportedCard): String {
    val t = card.cardTypes
    return when {
        "creature" in t -> "Creatures"
        "planeswalker" in t -> "Planeswalkers"
        "battle" in t -> "Battles"
        "land" in t -> "Lands"
        "instant" in t -> "Instants"
        "sorcery" in t -> "Sorceries"
        "artifact" in t -> "Artifacts"
        "enchantment" in t -> "Enchantments"
        else -> "Other"
    }
}

val DECK_GROUP_ORDER = listOf("Creatures", "Planeswalkers", "Instants", "Sorceries", "Artifacts", "Enchantments", "Battles", "Lands", "Other", "Not in library")

enum class DeckGrouping(val label: String) { TYPE("Type"), MANA_VALUE("Mana value"), COLOR("Color"), NAME("A–Z") }

/** Groups deck lines for display. Unknown cards always land in a visible "Not in library" group. */
fun groupDeck(deck: DeckDraft, catalog: SupportedCardCatalog, grouping: DeckGrouping): List<Pair<String, List<DeckLine>>> {
    fun key(line: DeckLine): String {
        val card = catalog.find(line.name) ?: return "Not in library"
        return when (grouping) {
            DeckGrouping.TYPE -> deckGroupFor(card)
            DeckGrouping.MANA_VALUE -> if ("land" in card.cardTypes) "Lands" else "Mana value ${card.manaValue.toInt().coerceAtMost(7)}${if (card.manaValue >= 7) "+" else ""}"
            DeckGrouping.COLOR -> colorsOf(card.manaCost).let { c -> when { "land" in card.cardTypes -> "Lands"; c.isEmpty() -> "Colorless"; c.size > 1 -> "Multicolor"; else -> colorName(c.single()) } }
            DeckGrouping.NAME -> card.name.first().uppercaseChar().toString()
        }
    }
    val groups = deck.main.groupBy(::key).mapValues { (_, v) -> v.sortedWith(compareBy({ catalog.find(it.name)?.manaValue ?: 99.0 }, { it.name.lowercase(Locale.ROOT) })) }
    val order: (String) -> Int = when (grouping) {
        DeckGrouping.TYPE -> { k -> DECK_GROUP_ORDER.indexOf(k).let { if (it < 0) 99 else it } }
        DeckGrouping.MANA_VALUE -> { k -> when (k) { "Lands" -> 50; "Not in library" -> 60; else -> k.filter(Char::isDigit).toIntOrNull() ?: 40 } }
        DeckGrouping.COLOR -> { k -> listOf("White", "Blue", "Black", "Red", "Green", "Multicolor", "Colorless", "Lands", "Not in library").indexOf(k) }
        DeckGrouping.NAME -> { k -> if (k == "Not in library") 999 else k.first().code }
    }
    return groups.entries.sortedBy { order(it.key) }.map { it.key to it.value }
}

private fun colorName(c: Char) = when (c) { 'W' -> "White"; 'U' -> "Blue"; 'B' -> "Black"; 'R' -> "Red"; 'G' -> "Green"; else -> "Colorless" }

// ---- Editing session with undo --------------------------------------------------------------

/** Holds the draft plus undo history. Pure, so fold/rotate state can be rebuilt from saved text. */
class DeckEditSession(initial: DeckDraft, private val maxHistory: Int = 100) {
    var draft: DeckDraft = initial
        private set
    private val undoStack = ArrayDeque<DeckDraft>()
    private val redoStack = ArrayDeque<DeckDraft>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun apply(change: (DeckDraft) -> DeckDraft): DeckDraft {
        val next = change(draft)
        if (next != draft) {
            undoStack.addLast(draft)
            if (undoStack.size > maxHistory) undoStack.removeFirst()
            redoStack.clear()
            draft = next
        }
        return draft
    }

    fun undo(): DeckDraft { undoStack.removeLastOrNull()?.let { redoStack.addLast(draft); draft = it }; return draft }
    fun redo(): DeckDraft { redoStack.removeLastOrNull()?.let { undoStack.addLast(draft); draft = it }; return draft }
}

/** Saved-deck list operations used by the deck manager; they never touch other decks. */
internal object SavedDeckOps {
    fun uniqueName(base: String, existing: List<SavedDeck>): String {
        val names = existing.map { it.name.lowercase(Locale.ROOT) }.toSet()
        if (base.lowercase(Locale.ROOT) !in names) return base
        var i = 2
        while ("$base ($i)".lowercase(Locale.ROOT) in names) i++
        return "$base ($i)"
    }

    /** Replaces the deck called [originalName] in place (keeping list order) or adds it at the top. */
    fun upsert(decks: List<SavedDeck>, originalName: String?, deck: SavedDeck): List<SavedDeck> {
        val index = originalName?.let { o -> decks.indexOfFirst { it.name == o } } ?: -1
        return if (index >= 0) decks.toMutableList().also { it[index] = deck } else listOf(deck) + decks
    }

    fun duplicate(decks: List<SavedDeck>, source: SavedDeck): List<SavedDeck> =
        listOf(source.copy(name = uniqueName("${source.name} copy", decks))) + decks

    fun delete(decks: List<SavedDeck>, target: SavedDeck): List<SavedDeck> = decks.filterNot { it.name == target.name && it.text == target.text }

    /** Puts a deleted deck back where it was (undo). A deck saved with that name since is kept. */
    fun restore(decks: List<SavedDeck>, deck: SavedDeck, index: Int): List<SavedDeck> {
        if (decks.any { it.name == deck.name }) return decks
        return decks.toMutableList().also { it.add(index.coerceIn(0, it.size), deck) }
    }

    fun setTags(decks: List<SavedDeck>, target: SavedDeck, tags: List<String>): List<SavedDeck> {
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }
        return decks.map { if (it.name == target.name && it.text == target.text) it.copy(tags = clean) else it }
    }

    /** Every tag in use, alphabetically, first spelling wins. */
    fun allTags(decks: List<SavedDeck>): List<String> =
        decks.flatMap { it.tags }.distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }

    /**
     * The deck library's visible list: [query] matches the deck name, commander or any card in it;
     * [tag] keeps decks carrying that tag. Decks with no edit time sort after dated ones.
     */
    fun visible(decks: List<SavedDeck>, query: String, tag: String?, sort: DeckLibrarySort, summaries: Map<String, DeckSummary>): List<SavedDeck> {
        val q = query.trim().lowercase(Locale.ROOT)
        val filtered = decks.filter { d ->
            (tag == null || d.tags.any { it.equals(tag, ignoreCase = true) }) &&
                (q.isEmpty() || d.name.lowercase(Locale.ROOT).contains(q) || d.commander.orEmpty().lowercase(Locale.ROOT).contains(q) ||
                    d.text.lowercase(Locale.ROOT).lineSequence().any { line -> !line.trimStart().startsWith("//") && line.contains(q) })
        }
        return when (sort) {
            DeckLibrarySort.RECENT -> filtered.sortedWith(compareByDescending<SavedDeck> { it.updatedAt }) // stable: keeps saved order on ties
            DeckLibrarySort.NAME -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            DeckLibrarySort.COLOR -> filtered.sortedWith(compareBy<SavedDeck>(
                { summaries[it.name]?.identity?.size ?: 9 },
                { summaries[it.name]?.identity?.let { c -> "WUBRG".filter { it in c } } ?: "" },
                { it.name.lowercase(Locale.ROOT) }))
        }
    }

    fun rename(decks: List<SavedDeck>, target: SavedDeck, newName: String): List<SavedDeck> {
        val clean = newName.trim().ifBlank { return decks }
        val name = if (clean.equals(target.name, true)) clean else uniqueName(clean, decks.filterNot { it.name == target.name })
        return decks.map { if (it.name == target.name && it.text == target.text) it.copy(name = name) else it }
    }
}

enum class DeckLibrarySort(val label: String) { RECENT("Recently edited"), NAME("Name"), COLOR("Colors") }

/** What a deck library tile shows. [problems] is null when the card library isn't loaded yet. */
data class DeckSummary(
    val commanders: List<String>,
    val cards: Int,
    val identity: Set<Char>?,
    val problems: Int?,
    val commanderImageUrl: String?,
)

internal fun summarizeDeck(deck: SavedDeck, catalog: SupportedCardCatalog?): DeckSummary {
    val draft = DeckDraft.fromText(deck.name, deck.text, catalog)
    if (catalog == null) return DeckSummary(draft.commanders, draft.deckSize, null, null, null)
    val identity = if (draft.commanders.isEmpty() || draft.commanders.any { catalog.find(it) == null }) null
        else draft.commanders.flatMap { catalog.find(it)?.colors.orEmpty() }.toSet()
    val problems = validateCommanderDeck(draft, catalog).count { it.level == IssueLevel.ERROR }
    return DeckSummary(draft.commanders, draft.deckSize, identity, problems, draft.commanders.firstOrNull()?.let { catalog.find(it)?.imageUrl })
}

/** "edited 5 min ago" style label for a deck tile; null when the edit time was never recorded. */
internal fun editedAgo(updatedAt: Long, now: Long): String? {
    if (updatedAt <= 0L) return null
    val minutes = ((now - updatedAt).coerceAtLeast(0L)) / 60_000L
    return when {
        minutes < 1 -> "edited just now"
        minutes < 60 -> "edited $minutes min ago"
        minutes < 24 * 60 -> "edited ${minutes / 60} h ago"
        minutes < 48 * 60 -> "edited yesterday"
        minutes < 30L * 24 * 60 -> "edited ${minutes / (24 * 60)} days ago"
        else -> "edited " + java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(updatedAt))
    }
}
