package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * When Argentum and Arcana both have a card, Argentum's is played unless custom-overrides.txt lists it;
 * either way exactly one definition is loaded and Arcana's card is never lost.
 */
class CustomOverridesTest {
    private val assets = File("src/main/assets")
    private val customLine = File(assets, "argentum/custom-cards.jsonl").readLines().first(String::isNotBlank)
    private val customName = File(assets, "argentum/custom-cards.index.tsv").readLines().first().substringBefore('\t')

    /** A library that also has [customName] (same data), plus optional override text. */
    private fun library(overrides: String?) = OfflineCardLibrary({ path ->
        when (path) {
            "argentum/all-cards.index.tsv" -> "$customName\tcard\n".byteInputStream()
            "argentum/all-cards.jsonl" -> "$customLine\n".byteInputStream()
            "argentum/custom-overrides.txt" -> overrides?.let { ByteArrayInputStream(it.toByteArray()) }
            else -> File(assets, path).takeIf(File::exists)?.inputStream()
        }
    })

    @Test fun argentumsVersionIsPlayedByDefault() {
        val lib = library(null)
        assertFalse(lib.isCustom(customName))
        assertEquals(1, lib.load(setOf(customName)).count { it.name == customName })
    }

    @Test fun anOverrideKeepsArcanasVersionInPlay() {
        val lib = library("# kept by the weekly upgrade\n${customName.uppercase()}\n")
        assertTrue(lib.isCustom(customName))
        assertEquals(customName, lib.canonical(customName))
        assertEquals("exactly one definition, Arcana's", 1, lib.load(setOf(customName)).count { it.name == customName })
    }

    @Test fun overrideFileIgnoresCommentsAndBlankLines() {
        assertEquals(setOf("a card", "another"),
            OfflineCardLibrary.parseOverrides(sequenceOf("# header", "", "  A Card  ", "Another # why")))
    }
}
