package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import com.arcanatable.engine.OfflineGameSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfflineGameSetupTest {
    private val assets = File("src/main/assets")
    private fun library() = OfflineCardLibrary({ path -> File(assets, path).takeIf { it.exists() }?.inputStream() })

    @Test fun premadeCommanderDecksAreBundled() {
        val decks = OfflineGameSetup.parsePremades(File(assets, "argentum/commander-decks.json").readText())
        assertTrue(decks.size >= 5)
        decks.forEach { assertEquals("${it.name} has 100 cards", 100, it.cards.size) }
    }

    @Test fun libraryListDropsOneCommanderCopy() {
        assertEquals(listOf("A", "Avatar Aang"), OfflineGameSetup.argentumLibraryList(listOf("Avatar Aang", "A", "Avatar Aang"), "avatar aang"))
    }

    @Test fun setupAlwaysProducesAPlayableGame() {
        val lib = library()
        val (setup, registry) = OfflineGameSetup.build(lib, "You", listOf("Forest", "Llanowar Elves"), "Avatar Aang",
            File(assets, "argentum/commander-decks.json").readText())
        if (!lib.isFullLibrary) {
            assertNull(setup.you.commander)
            assertTrue(setup.notes.isNotEmpty())
        }
        assertTrue(setup.you.deck.size >= 40 && setup.ai.deck.size >= 40)
        (setup.you.deck + setup.ai.deck).distinct().forEach { assertTrue("registry has $it", registry.getCard(it) != null) }
    }
}
