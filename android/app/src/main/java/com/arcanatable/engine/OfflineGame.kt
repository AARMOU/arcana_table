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
 */
class OfflineCardLibrary(private val open: (String) -> InputStream?, forcedBase: String? = null) {
    fun portalOnly(): OfflineCardLibrary = OfflineCardLibrary(open, "por-cards")
    val base: String = forcedBase ?: if (open("argentum/all-cards.index.tsv")?.also { it.close() } != null) "all-cards" else "por-cards"
    val isFullLibrary: Boolean get() = base == "all-cards"

    private val index: List<Pair<String, String>> by lazy {
        open("argentum/$base.index.tsv")?.bufferedReader()?.useLines { lines ->
            lines.filter(String::isNotBlank).map { val tab = it.indexOf('\t'); if (tab < 0) it to "card" else it.substring(0, tab) to it.substring(tab + 1) }.toList()
        } ?: open("argentum/$base.jsonl")!!.bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).map { CardLoader.fromJsonPreservingIds(it).let { c -> c.name to (if (c.typeLine.isBasicLand) "basic" else "card") } }.toList()
        }
    }
    private val byLower: Map<String, String> by lazy { index.associate { it.first.lowercase() to it.first } }

    val size: Int get() = index.size

    /** The library's spelling of [name], or null when Argentum has no such card. */
    fun canonical(name: String): String? = byLower[name.trim().lowercase()]

    /** Every card in the library (only sensible for the small Portal library). */
    fun loadAll(): List<CardDefinition> = open("argentum/$base.jsonl")!!.bufferedReader().useLines { lines ->
        lines.filter(String::isNotBlank).map(CardLoader::fromJsonPreservingIds).toList()
    }

    /** Decodes [names] plus every token and basic land, in one streaming pass. */
    fun load(names: Set<String>): List<CardDefinition> {
        val wanted = names.mapNotNull(::canonical).toSet()
        val out = ArrayList<CardDefinition>(wanted.size + 64)
        open("argentum/$base.jsonl")!!.bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).forEachIndexed { i, line ->
                val (name, kind) = index.getOrNull(i) ?: return@forEachIndexed
                if (kind != "card" || name in wanted) out += CardLoader.fromJsonPreservingIds(line)
            }
        }
        return out
    }
}

/** Who plays what in an offline game, and anything the player should be told about it. */
data class OfflineSetup(val you: LocalSeat, val ai: LocalSeat, val notes: List<String>, val aiDeckName: String)

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
    ): Pair<OfflineSetup, CardRegistry> {
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
            return OfflineSetup(LocalSeat(playerName, you.cards, null), LocalSeat("Offline AI", ai.cards, null), notes, "Red (Portal)") to registry
        }
        // Your deck: the 99 without the commander, unsupported cards left out.
        val yourList = argentumLibraryList(deckNames, yourCommander)
        val supported = yourList.mapNotNull(library::canonical)
        val missing = yourList.filter { library.canonical(it) == null }.distinct()
        val premade = premades.sortedByDescending { d -> d.cards.count { library.canonical(it) != null } }.let { ranked ->
            val complete = ranked.filter { d -> d.cards.all { library.canonical(it) != null } }
            (complete.ifEmpty { ranked.take(1) }).random(random)
        }
        val aiList = argentumLibraryList(premade.cards, premade.commander).mapNotNull(library::canonical)
        val cards = library.load((supported + aiList + yourCommander + premade.commander).toSet())
        val registry = CardRegistry().apply { register(cards) }
        fun filled(list: List<String>, commanderName: String): List<String> {
            if (list.size >= 99) return list.take(99)
            val identity = registry.getCard(commanderName)?.colorIdentity.orEmpty()
            val basics = identity.mapNotNull(BASICS::get).ifEmpty { listOf("Wastes").filter { registry.getCard(it) != null }.ifEmpty { listOf("Plains") } }
            return list + List(99 - list.size) { basics[it % basics.size] }
        }
        if (missing.isNotEmpty()) notes += "Left out ${missing.size} card(s) Argentum can't run offline (${missing.take(4).joinToString()}${if (missing.size > 4) ", …" else ""}) and filled with basic lands. Your saved deck is unchanged."
        val you = LocalSeat(playerName, filled(supported, yourCommander), yourCommander)
        val ai = LocalSeat("Offline AI", filled(aiList, library.canonical(premade.commander)!!), library.canonical(premade.commander))
        return OfflineSetup(you, ai, notes, premade.name) to registry
    }

    data class Premade(val name: String, val commander: String, val cards: List<String>)

    fun parsePremades(json: String): List<Premade> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { deck ->
            val cards = deck.getJSONObject("cards")
            Premade(deck.optString("name"), deck.optString("commander"), cards.keys().asSequence().flatMap { name -> List(cards.optInt(name)) { name } }.toList())
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
