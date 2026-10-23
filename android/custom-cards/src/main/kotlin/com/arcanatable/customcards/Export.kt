package com.arcanatable.customcards

import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.tooling.CardExporter
import com.wingedsheep.sdk.tooling.CardLoader
import java.io.File

/** One custom card as a single JSON line, in Argentum's own card-data format. */
fun exportLine(card: CardDefinition): String = CardExporter.exportToCompactJson(card).replace("\n", "")

/** The data file contents (jsonl and index), checking that every line loads back as the same card. */
fun exportData(cards: List<CardDefinition> = CustomCards.all): Pair<String, String> {
    val lines = cards.map { card ->
        val line = exportLine(card)
        val back = CardLoader.fromJsonPreservingIds(line)
        check(back.name == card.name) { "Round trip changed ${card.name} into ${back.name}" }
        check(exportLine(back) == line) { "Round trip of ${card.name} is not stable" }
        line
    }
    val index = cards.joinToString("") { "${it.name}\tcard\n" }
    return lines.joinToString("\n", postfix = "\n") to index
}

/** Writes custom-cards.jsonl and custom-cards.index.tsv into the app's assets. */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: error("Pass the app's assets/argentum directory")).apply { mkdirs() }
    val (jsonl, index) = exportData()
    File(dir, "custom-cards.jsonl").writeText(jsonl)
    File(dir, "custom-cards.index.tsv").writeText(index)
    println("ARCANA-CUSTOM-CARDS cards=${CustomCards.all.size} bytes=${jsonl.length}")
}
