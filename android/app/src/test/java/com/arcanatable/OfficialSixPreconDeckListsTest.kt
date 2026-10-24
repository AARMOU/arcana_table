package com.arcanatable

import com.arcanatable.engine.OfflineGameSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * All six historical Wizards of the Coast precon lists are staged verbatim by name and quantity
 * from investigation/argentum-six-precons/deck-manifest.json.
 *
 * These files are intentionally NOT in the playable commander-decks.json catalog while many
 * individual cards and four face commanders are still absent from the pinned Argentum runtime.
 */
class OfficialSixPreconDeckListsTest {
    private val official = mapOf(
        "warhammer_forces_of_the_imperium.txt" to "Inquisitor Greyfax",
        "warhammer_tyranid_swarm.txt" to "The Swarmlord",
        "warhammer_necron_dynasties.txt" to "Szarekh, the Silent King",
        "warhammer_the_ruinous_powers.txt" to "Abaddon the Despoiler",
        "eldrazi_unbound.txt" to "Zhulodok, Void Gorger",
        "eldrazi_incursion.txt" to "Ulalek, Fused Atrocity",
    )

    @Test
    fun allSixOfficialListsHaveExactly100CardsAndTheirTrueCommander() {
        assertEquals(6, official.size)
        official.forEach { (fileName, commanderName) ->
            val file = File("src/main/assets/precons/$fileName")
            assertTrue("Missing original precon list: $fileName", file.isFile)
            val entries = parseDeckList(file.readText())
            assertEquals("$fileName must have 100 slots", 100, entries.sumOf { it.count })
            val commanders = entries.filter { it.commander }
            assertEquals("$fileName must have precisely one commander", 1, commanders.sumOf { it.count })
            assertEquals("$fileName must retain original face commander", commanderName, commanders.single().name)
            assertEquals("$fileName should have exactly 99 main deck slots", 99, entries.filterNot { it.commander }.sumOf { it.count })
            assertTrue("$fileName has invalid count/name", entries.all { it.name.isNotBlank() && it.count > 0 })
        }
    }

    @Test
    fun incompleteOfficialDecklistsMustNotBeAdvertisedAsPlayablePremades() {
        val catalog = File("src/main/assets/argentum/commander-decks.json").readText()
        val playable = OfflineGameSetup.parsePremades(catalog)
        val playableCommanders = playable.map { it.commander }.toSet()
        official.forEach { (fileName, commander) ->
            assertFalse("$fileName would be misleadingly exposed before 100/100 and engine checks", commander in playableCommanders)
        }
    }
}
