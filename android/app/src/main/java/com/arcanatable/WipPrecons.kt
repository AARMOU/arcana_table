package com.arcanatable

import java.io.InputStream
import java.util.Locale

/**
 * The owner's six original Wizards Commander precons, still in progress.
 * Only list a WIP deck when its actual face commander is indexed in the
 * installed runtime; the complete 100-card list itself is retained unchanged.
 * Index presence does not establish rule completeness for the other 99 cards.
 */
internal object WipPrecons {
    data class Definition(val id: String, val name: String, val commander: String, val file: String)

    val definitions = listOf(
        Definition("forces_of_the_imperium", "Forces of the Imperium", "Inquisitor Greyfax", "forces_of_the_imperium.txt"),
        Definition("tyranid_swarm", "Tyranid Swarm", "The Swarmlord", "tyranid_swarm.txt"),
        Definition("necron_dynasties", "Necron Dynasties", "Szarekh, the Silent King", "necron_dynasties.txt"),
        Definition("the_ruinous_powers", "The Ruinous Powers", "Abaddon the Despoiler", "the_ruinous_powers.txt"),
        Definition("eldrazi_unbound", "Eldrazi Unbound", "Zhulodok, Void Gorger", "eldrazi_unbound.txt"),
        Definition("eldrazi_incursion", "Eldrazi Incursion", "Ulalek, Fused Atrocity", "eldrazi_incursion.txt")
    )

    fun available(open: (String) -> InputStream?): List<AppPreconDeck> {
        val faceNames = definitions.map { it.commander.lowercase(Locale.ROOT) }.toSet()
        val indexed = mutableSetOf<String>()
        for (file in listOf("argentum/all-cards.index.tsv", "argentum/custom-cards.index.tsv")) {
            open(file)?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    val name = line.substringBefore('\t').trim().lowercase(Locale.ROOT)
                    if (name in faceNames && line.substringAfter('\t', "") == "card") indexed.add(name)
                }
            }
        }
        return definitions.filter { it.commander.lowercase(Locale.ROOT) in indexed }.mapNotNull { deck ->
            val list = open("precons/${deck.file}")?.bufferedReader()?.use { it.readText() }
                ?: return@mapNotNull null
            AppPreconDeck(deck.id, "${deck.name} · WIP", deck.commander, list)
        }
    }
}
