package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/** WOTC originals stay intact and cannot appear without their face commander. */
class WipPreconsTest {
    private val assets = File("src/main/assets")
    private fun file(path: String) = File(assets, path).takeIf { it.isFile }?.inputStream()

    @Test fun originalSixWipListsHave100SlotsAndTheRightCommander() {
        assertEquals(6, WipPrecons.definitions.size)
        WipPrecons.definitions.forEach { deck ->
            val entries = parseDeckList(File(assets, "precons/${deck.file}").readText())
            assertEquals("${deck.name}: 100 original slots", 100, entries.sumOf { it.count })
            assertEquals("${deck.name}: one face commander", 1, entries.filter { it.commander }.sumOf { it.count })
            assertEquals("${deck.name}: named commander", deck.commander, entries.single { it.commander }.name)
        }
    }

    @Test fun installedWotcWipDecksRequireTheirCommanderInBundledRuntimeIndex() {
        val named = sequenceOf("argentum/all-cards.index.tsv", "argentum/custom-cards.index.tsv")
            .flatMap { path -> File(assets, path).useLines { it.toList() }.asSequence() }
            .filter { it.substringAfter('\t', "") == "card" }
            .map { it.substringBefore('\t').lowercase() }
            .toSet()
        val expected = WipPrecons.definitions.filter { it.commander.lowercase() in named }.map { it.id }
        val visible = WipPrecons.available(::file)
        assertEquals(expected, visible.map { it.id })
        assertTrue(visible.all { it.name.endsWith(" · WIP") })
    }

    @Test fun sixthDeckAppearsAutomaticallyOnceItsCommanderIsIndexed() {
        val all = WipPrecons.definitions.joinToString("\n") { "${it.commander}\tcard" } + "\n"
        val visible = WipPrecons.available { path ->
            if (path == "argentum/custom-cards.index.tsv") ByteArrayInputStream(all.toByteArray())
            else if (path == "argentum/all-cards.index.tsv") null
            else file(path)
        }
        assertEquals(WipPrecons.definitions.map { it.id }, visible.map { it.id })
    }
}
