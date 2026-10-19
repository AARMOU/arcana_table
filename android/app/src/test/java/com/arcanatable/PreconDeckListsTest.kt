package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PreconDeckListsTest {
    @Test fun bundledFalloutAndBloomburrowListsParseAsCommanderDecks() {
        val expected = mapOf(
            "fallout_scrappy_survivors.txt" to "Dogmeat, Ever Loyal",
            "fallout_hail_caesar.txt" to "Caesar, Legion's Emperor",
            "fallout_mutant_menace.txt" to "The Wise Mothman",
            "fallout_science.txt" to "Dr. Madison Li",
            "bloomburrow_family_matters.txt" to "Zinnia, Valley's Voice",
            "bloomburrow_peace_offering.txt" to "Ms. Bumbleflower"
        )

        expected.forEach { (fileName, commanderName) ->
            val file = File("src/main/assets/precons/$fileName")
            val entries = parseDeckList(file.readText())
            assertEquals("$fileName should contain 100 cards", 100, entries.sumOf { it.count })
            val commanderEntries = entries.filter { it.commander }
            assertEquals("$fileName should have one commander entry", 1, commanderEntries.sumOf { it.count })
            assertEquals("$fileName commander", commanderName, commanderEntries.single().name)
        }
    }
}
