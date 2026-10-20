package com.arcanatable.engine

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.tooling.CardLoader
import org.json.JSONArray
import java.io.InputStream
import kotlin.random.Random

/**
 * Argentum's card library shipped as data (assets/argentum/<base>.jsonl plus <base>.index.tsv:
 * "name<TAB>kind" per line, same order). Only the cards a game needs are decoded; tokens and basic
 * lands always come along because cards create and fetch them by name.
 *
 * Arcana's own custom cards (custom-cards.jsonl, written in Argentum's card language for cards
 * Argentum doesn't have yet) sit beside it. Argentum's version of a card always wins: a custom card is
 * only used when the library has no card of that name.
 */
class OfflineCardLibrary(private val open: (String) -> InputStream?, forcedBase: String? = null) {
    fun portalOnly(): OfflineCardLibrary = OfflineCardLibrary(open, "por-cards")
    val base: String = forcedBase ?: if (open("argentum/all-cards.index.tsv")?.also { it.close() } != null) "all-cards" else "por-cards"
    val isFullLibrary: Boolean get() = base == "all-cards"

    private fun readIndex(file: String): List<Pair<String, String>>? = open("argentum/$file.index.tsv")?.bufferedReader()?.useLines { lines ->
        lines.filter(String::isNotBlank).map { val tab = it.indexOf('\t'); if (tab < 0) it to "card" else it.substring(0, tab) to it.substring(tab + 1) }.toList()
    }

    private val index: List<Pair<String, String>> by lazy {
        readIndex(base) ?: open("argentum/$base.jsonl")!!.bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).map { CardLoader.fromJsonPreservingIds(it).let { c -> c.name to (if (c.typeLine.isBasicLand) "basic" else "card") } }.toList()
        }
    }
    private val byLower: Map<String, String> by lazy { index.associate { it.first.lowercase() to it.first } }

    /** Custom cards (only with the full library; Portal duels stay Portal). */
    private val customIndex: List<Pair<String, String>> by lazy { if (isFullLibrary) readIndex(CUSTOM).orEmpty() else emptyList() }
    private val customByLower: Map<String, String> by lazy {
        customIndex.map { it.first }.filter { it.lowercase() !in byLower }.associateBy { it.lowercase() }
    }
    private val cardOverrides: Map<String, CardDefinition> by lazy {
        open("argentum/card-overrides.jsonl")?.bufferedReader()?.useLines { lines ->
            lines.filter(String::isNotBlank).map(CardLoader::fromJsonPreservingIds)
                .associateBy { it.name.lowercase() }
        }.orEmpty()
    }

    private fun loadDefinition(line: String): CardDefinition {
        val card = CardLoader.fromJsonPreservingIds(line)
        return cardOverrides[card.name.lowercase()] ?: card
    }

    val size: Int get() = index.size

    /** How many custom cards this library can add. */
    val customCount: Int get() = customByLower.size

    /** The library's spelling of [name] (or a custom card's), or null when neither has it. */
    fun canonical(name: String): String? {
        val key = name.trim().lowercase()
        byLower[key]?.let { return it }
        customByLower[key]?.let { return it }
        // A double-faced card listed by both faces ("Front // Back") is known by its front face.
        val front = key.substringBefore(" // ").trim()
        return if (front != key) byLower[front] ?: customByLower[front] else null
    }

    /** True when [name] is played with Arcana's custom version rather than Argentum's. */
    fun isCustom(name: String): Boolean = canonical(name)?.lowercase()?.let { it in customByLower && it !in byLower } == true

    /** Every card in the library (only sensible for the small Portal library). */
    fun loadAll(): List<CardDefinition> = open("argentum/$base.jsonl")!!.bufferedReader().useLines { lines ->
        lines.filter(String::isNotBlank).map(::loadDefinition).toList()
    }

    /** Decodes [names] plus every token and basic land, in one streaming pass (and custom cards). */
    fun load(names: Set<String>): List<CardDefinition> {
        val wanted = names.mapNotNull(::canonical).toSet()
        val out = ArrayList<CardDefinition>(wanted.size + 64)
        open("argentum/$base.jsonl")!!.bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).forEachIndexed { i, line ->
                val (name, kind) = index.getOrNull(i) ?: return@forEachIndexed
                if (kind != "card" || name in wanted) out += loadDefinition(line)
            }
        }
        val customWanted = wanted.filter { it.lowercase() in customByLower }.toSet()
        if (customWanted.isNotEmpty()) open("argentum/$CUSTOM.jsonl")?.bufferedReader()?.useLines { lines ->
            lines.filter(String::isNotBlank).forEachIndexed { i, line ->
                val name = customIndex.getOrNull(i)?.first ?: return@forEachIndexed
                if (name in customWanted) out += CardLoader.fromJsonPreservingIds(line)
            }
        }
        return out
    }

    companion object {
        const val CUSTOM = "custom-cards"
    }
}

/** Who plays what in an offline game, and anything the player should be told about it. */
data class OfflineSetup(val you: LocalSeat, val ai: LocalSeat, val notes: List<String>, val aiDeckName: String, val extraAis: List<LocalSeat> = emptyList())

object OfflineGameSetup {
    private val BASICS = mapOf(Color.WHITE to "Plains", Color.BLUE to "Island", Color.BLACK to "Swamp", Color.RED to "Mountain", Color.GREEN to "Forest")

    /**
     * Your imported Commander deck against one of Argentum's premade Commander decks, when the full
     * library is on the phone. Cards Argentum lacks are left out and replaced with basics of your
     * commander's colours. Without the full library (or a commander), a Portal duel instead.
     */
    fun build(
        library: OfflineCardLibrary,
        playerName: String,
        deckNames: List<String>,
        commander: String?,
        aiDecksJson: String?,
        random: Random = Random.Default,
        aiDeckId: String? = null,
        aiCount: Int = 1,
    ): Pair<OfflineSetup, CardRegistry> {
        val count = aiCount.coerceIn(1, 3)
        val notes = mutableListOf<String>()
        val yourCommander = commander?.let(library::canonical)
        val premades = aiDecksJson?.let(::parsePremades).orEmpty()
            .filter { deck -> library.canonical(deck.commander) != null }
        if (!library.isFullLibrary || yourCommander == null || premades.isEmpty()) {
            if (commander != null && !library.isFullLibrary) notes += "This build only has the Portal cards offline, so you get a Portal duel instead of your Commander deck."
            else if (commander != null && yourCommander == null) notes += "Argentum doesn't have your commander ($commander) offline, so you get a Portal-style duel instead."
            val portalCards = library.portalOnly().loadAll()
            val registry = CardRegistry().apply { register(portalCards) }
            val you = EmbeddedEngineTrial.monoDeck(portalCards, Color.GREEN, random)
            val ai = EmbeddedEngineTrial.monoDeck(portalCards, Color.RED, random)
            val extraColors = listOf(Color.BLUE to "Blue", Color.BLACK to "Black").take(count - 1)
            val extras = extraColors.map { (color, name) -> LocalSeat("AI · $name", EmbeddedEngineTrial.monoDeck(portalCards, color, random).cards, null) }
            val firstName = if (count > 1) "AI · Red" else "Offline AI"
            return OfflineSetup(LocalSeat(playerName, you.cards, null), LocalSeat(firstName, ai.cards, null), notes, "Red (Portal)", extras) to registry
        }
        // Your deck: the 99 without the commander, unsupported cards left out.
        val yourList = argentumLibraryList(deckNames, yourCommander)
        val supported = yourList.mapNotNull(library::canonical)
        val missing = yourList.filter { library.canonical(it) == null }.distinct()
        // The deck you picked for the AI, or a random complete one.
        val premade = premades.firstOrNull { it.id == aiDeckId } ?: premades.sortedByDescending { d -> d.cards.count { library.canonical(it) != null } }.let { ranked ->
            val complete = ranked.filter { d -> d.cards.all { library.canonical(it) != null } }
            (complete.ifEmpty { ranked.take(1) }).random(random)
        }
        val aiList = argentumLibraryList(premade.cards, premade.commander).mapNotNull(library::canonical)
        // Seats 3 and 4: other decks, complete ones first, never the same deck twice.
        val others = premades.filter { it.id != premade.id }.shuffled(random)
            .sortedByDescending { d -> d.cards.all { library.canonical(it) != null } }
            .take(count - 1)
        val otherLists = others.map { d -> d to argentumLibraryList(d.cards, d.commander).mapNotNull(library::canonical) }
        val cards = library.load((supported + aiList + yourCommander + premade.commander + otherLists.flatMap { (d, l) -> l + d.commander }).toSet())
        val registry = CardRegistry().apply { register(cards) }
        fun filled(list: List<String>, commanderName: String): List<String> {
            if (list.size >= 99) return list.take(99)
            val identity = registry.getCard(commanderName)?.colorIdentity.orEmpty()
            val basics = identity.mapNotNull(BASICS::get).ifEmpty { listOf("Wastes").filter { registry.getCard(it) != null }.ifEmpty { listOf("Plains") } }
            return list + List(99 - list.size) { basics[it % basics.size] }
        }
        val custom = (supported + yourCommander).filter(library::isCustom).distinct()
        if (custom.isNotEmpty()) notes += "Using Arcana's own versions of ${custom.size} card(s) Argentum doesn't have yet (${custom.take(4).joinToString()}${if (custom.size > 4) ", …" else ""})."
        if (missing.isNotEmpty()) notes += "Left out ${missing.size} card(s) Argentum can't run offline (${missing.take(4).joinToString()}${if (missing.size > 4) ", …" else ""}) and filled with basic lands. Your saved deck is unchanged."
        val you = LocalSeat(playerName, filled(supported, yourCommander), yourCommander)
        fun aiName(commanderName: String) = if (count > 1) "AI · ${commanderName.substringBefore(',').trim()}" else "Offline AI"
        val ai = LocalSeat(aiName(premade.commander), filled(aiList, library.canonical(premade.commander)!!), library.canonical(premade.commander))
        val extras = otherLists.mapNotNull { (d, list) -> library.canonical(d.commander)?.let { cmd -> LocalSeat(aiName(cmd), filled(list, cmd), cmd) } }
        if (extras.size < count - 1) notes += "Only ${extras.size + 1} AI deck(s) could be made, so this game has ${extras.size + 2} players."
        val name = (listOf(premade.name) + others.map { it.name }).joinToString(", ")
        return OfflineSetup(you, ai, notes, name, extras) to registry
    }

    /**
     * Wi-Fi: the guest's Commander deck as a seat in the host's game. Its cards are added to the
     * host's [registry]; unsupported cards are left out and filled with basics, as offline. Null with
     * a reason when it can't be played (no commander, or this phone lacks the full library).
     */
    fun lanSeat(library: OfflineCardLibrary, registry: CardRegistry, playerName: String, deckNames: List<String>, commander: String?): Pair<LocalSeat?, List<String>> {
        if (!library.isFullLibrary) return null to listOf("This build only has the Portal cards, so Wi-Fi games can't run.")
        val cmd = commander?.let(library::canonical) ?: return null to listOf(if (commander == null) "$playerName needs a Commander deck for Wi-Fi play." else "Argentum doesn't have $playerName's commander ($commander).")
        val list = argentumLibraryList(deckNames, cmd)
        val supported = list.mapNotNull(library::canonical)
        val missing = list.filter { library.canonical(it) == null }.distinct()
        val toLoad = (supported + cmd).toSet().filter { registry.getCard(it) == null }.toSet()
        if (toLoad.isNotEmpty()) registry.register(library.load(toLoad))
        val identity = registry.getCard(cmd)?.colorIdentity.orEmpty()
        val basics = identity.mapNotNull(BASICS::get).ifEmpty { listOf("Wastes").filter { registry.getCard(it) != null }.ifEmpty { listOf("Plains") } }
        basics.filter { registry.getCard(it) == null }.toSet().takeIf { it.isNotEmpty() }?.let { registry.register(library.load(it)) }
        val filled = if (supported.size >= 99) supported.take(99) else supported + List(99 - supported.size) { basics[it % basics.size] }
        val notes = if (missing.isEmpty()) emptyList() else listOf("Left out ${missing.size} of $playerName's cards Argentum can't run (${missing.take(4).joinToString()}${if (missing.size > 4) ", …" else ""}) and filled with basic lands.")
        return LocalSeat(playerName, filled, cmd) to notes
    }

    data class Premade(val name: String, val commander: String, val cards: List<String>, val id: String = name)

    fun parsePremades(json: String): List<Premade> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { deck ->
            val cards = deck.getJSONObject("cards")
            Premade(deck.optString("name"), deck.optString("commander"), cards.keys().asSequence().flatMap { name -> List(cards.optInt(name)) { name } }.toList(), deck.optString("id").ifBlank { deck.optString("name") })
        }
    }

    /** The deck minus one copy of its commander (the commander starts in the command zone). */
    fun argentumLibraryList(names: List<String>, commander: String): List<String> {
        val list = names.map(String::trim).filter(String::isNotEmpty).toMutableList()
        val at = list.indexOfFirst { it.equals(commander, ignoreCase = true) }
        if (at >= 0) list.removeAt(at)
        return list
    }
}
