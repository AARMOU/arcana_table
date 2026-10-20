package com.arcanatable.customcards.support

import com.arcanatable.customcards.CustomCards
import com.arcanatable.customcards.exportLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.tooling.CardLoader
import java.io.File

/**
 * The card pool a custom-card test plays with, exactly as the app gets it: Argentum's library from the
 * app's assets (all-cards.jsonl) and the custom cards after a trip through their JSON data form.
 */
object CardPool {
    private val assets = File(System.getProperty("arcana.assets") ?: "../app/src/main/assets/argentum")

    private val index: List<Pair<String, String>> by lazy {
        File(assets, "all-cards.index.tsv").readLines().filter(String::isNotBlank).map {
            val tab = it.indexOf('\t'); it.substring(0, tab) to it.substring(tab + 1)
        }
    }

    /** Library tokens and basic lands: always loaded, because cards create and fetch them by name. */
    private val alwaysLoaded: List<CardDefinition> by lazy { load(emptySet()) }

    private val overrides: Map<String, CardDefinition> by lazy {
        File(assets, "card-overrides.jsonl").takeIf(File::exists)?.useLines { lines ->
            lines.filter(String::isNotBlank).map(CardLoader::fromJsonPreservingIds)
                .associateBy { it.name.lowercase() }
        }.orEmpty()
    }

    /** Library cards named in [names] plus every token and basic land. */
    fun load(names: Set<String>): List<CardDefinition> {
        val wanted = names.map(String::lowercase).toSet()
        val out = ArrayList<CardDefinition>()
        File(assets, "all-cards.jsonl").useLines { lines ->
            lines.filter(String::isNotBlank).forEachIndexed { i, line ->
                val (name, kind) = index[i]
                if (kind != "card" || name.lowercase() in wanted) {
                    val card = CardLoader.fromJsonPreservingIds(line)
                    out += overrides[card.name.lowercase()] ?: card
                }
            }
        }
        return out
    }

    /** The custom cards as data: exported to JSON and loaded back, like the app does. */
    val custom: List<CardDefinition> by lazy { CustomCards.all.map { CardLoader.fromJsonPreservingIds(exportLine(it)) } }

    /** A driver whose registry holds the custom cards, the library's tokens and basics, and [libraryCards]. */
    fun driver(vararg libraryCards: String): GameTestDriver = GameTestDriver().apply {
        registerCards(if (libraryCards.isEmpty()) alwaysLoaded else load(libraryCards.toSet()))
        registerCards(custom)
    }
}
