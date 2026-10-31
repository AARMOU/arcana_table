package com.arcanatable.customcards.support

import com.arcanatable.customcards.CustomCards
import com.arcanatable.customcards.exportLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.tooling.CardLoader
import java.io.File

/**
 * The card pool a custom-card test plays with, exactly as the app gets it: Argentum's library from the
 * app's assets (all-cards.jsonl) and the custom cards after a trip through their JSON data form.
 *
 * Like the app, when Argentum also has a custom card, Argentum's version is the one in play unless
 * custom-overrides.txt lists it. So a custom card's scenario tests always exercise the version the
 * app actually plays, and the weekly upgrade uses their results to choose (see weekly.py reconcile).
 */
object CardPool {
    private val assets = File(System.getProperty("arcana.assets") ?: "../app/src/main/assets/argentum")

    private val index: List<Pair<String, String>> by lazy {
        File(assets, "all-cards.index.tsv").readLines().filter(String::isNotBlank).map {
            val tab = it.indexOf('\t'); it.substring(0, tab) to it.substring(tab + 1)
        }
    }

    private val libraryNames: Set<String> by lazy { index.filter { it.second == "card" }.map { it.first.lowercase() }.toSet() }

    /** Custom cards whose own version is played even though Argentum has the card (lowercase). */
    val overrides: Set<String> by lazy {
        File(assets, "custom-overrides.txt").takeIf(File::isFile)?.useLines { lines ->
            lines.map { it.substringBefore('#').trim() }.filter(String::isNotEmpty).map(String::lowercase).toSet()
        }.orEmpty()
    }

    private val customNames: Set<String> by lazy { CustomCards.all.map { it.name.lowercase() }.toSet() }

    /** True when Argentum's card of this name is played instead of Arcana's custom one. */
    fun superseded(name: String): Boolean =
        name.lowercase().let { it in customNames && it in libraryNames && it !in overrides }

    private val upstreamVersions = mutableMapOf<String, CardDefinition>()

    /** The definition the app plays for [card]: Argentum's when it supersedes the custom one. */
    fun inPlay(card: CardDefinition): CardDefinition {
        if (!superseded(card.name)) return card
        return synchronized(upstreamVersions) {
            upstreamVersions.getOrPut(card.name.lowercase()) {
                load(setOf(card.name)).firstOrNull { it.name.equals(card.name, ignoreCase = true) } ?: card
            }
        }
    }

    /** Library tokens and basic lands: always loaded, because cards create and fetch them by name. */
    private val alwaysLoaded: List<CardDefinition> by lazy { load(emptySet()) }

    /** Library cards named in [names] plus every token and basic land. */
    fun load(names: Set<String>): List<CardDefinition> {
        val wanted = names.map(String::lowercase).toSet()
        val out = ArrayList<CardDefinition>()
        File(assets, "all-cards.jsonl").useLines { lines ->
            lines.filter(String::isNotBlank).forEachIndexed { i, line ->
                val (name, kind) = index[i]
                if (kind != "card" || name.lowercase() in wanted) out += CardLoader.fromJsonPreservingIds(line)
            }
        }
        return out
    }

    /** The custom cards as data: exported to JSON and loaded back, like the app does. */
    val custom: List<CardDefinition> by lazy { CustomCards.all.map { CardLoader.fromJsonPreservingIds(exportLine(it)) } }

    /** A driver whose registry holds the custom cards, the library's tokens and basics, and [libraryCards]. */
    fun driver(vararg libraryCards: String): GameTestDriver = GameTestDriver().apply {
        registerCards(if (libraryCards.isEmpty()) alwaysLoaded else load(libraryCards.toSet()))
        registerCards(custom) // each one as the app plays it (see GameTestDriver.registerCard)
    }
}
