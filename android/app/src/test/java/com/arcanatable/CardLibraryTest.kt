package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Offline card search over the library shipped in the APK. Runs against the real assets: if a
 * search works here, it works on the phone with no network (nothing in CardLibrary touches it).
 */
class CardLibraryTest {
    companion object {
        private val assets = File("src/main/assets")
        lateinit var catalog: SupportedCardCatalog
        lateinit var lib: CardLibrary

        @BeforeClass @JvmStatic fun load() {
            assumeTrue("full Argentum library asset present", File(assets, "argentum/all-cards.jsonl").exists())
            catalog = SupportedCardCatalog.load { File(assets, it).takeIf(File::exists)?.inputStream() }
            lib = CardLibrary(catalog)
        }
    }

    private fun names(q: String, limit: Int = 2000) = lib.search(q, limit).cards.map { it.name }

    @Test fun libraryIsTheShippedCatalogWithoutTokens() {
        assertEquals(catalog.size, lib.size)
        assertTrue(lib.size > 16_000)
        assertTrue("tokens are not deck cards", names("t:token").none { it == "Treasure" })
    }

    @Test fun exactPartialAndCaseInsensitiveNames() {
        assertEquals("Sol Ring", names("Sol Ring").first())
        assertEquals("Sol Ring", names("sOl rInG").first())
        assertTrue(names("mob boss").contains("Krenko, Mob Boss"))
        assertTrue("word order free", names("boss krenko").contains("Krenko, Mob Boss"))
    }

    @Test fun typoTolerantFallbackIsFlagged() {
        val r = lib.search("Lightnig Bolt")
        assertTrue(r.fuzzy)
        assertEquals("Lightning Bolt", r.cards.first().name)
        assertFalse("exact hits are not called fuzzy", lib.search("Lightning Bolt").fuzzy)
    }

    @Test fun doubleFacedAndSplitCardsFoundByEitherName() {
        assertTrue(names("Insectile Aberration").contains("Delver of Secrets"))
        assertTrue(names("battery").contains("Assault // Battery"))
        assertTrue(names("is:dfc delver").contains("Delver of Secrets"))
    }

    @Test fun operatorsFilterAndCombine() {
        val cheapGreenCreatures = lib.search("t:creature c:g mv<=1", 5000).cards
        assertTrue(cheapGreenCreatures.any { it.name == "Llanowar Elves" })
        assertTrue(cheapGreenCreatures.all { "creature" in it.cardTypes && it.manaValue <= 1.0 && 'G' in colorsOf(it.manaCost) })

        val draw = names("o:\"draw a card\" t:instant c:u")
        assertTrue(draw.isNotEmpty())
        assertTrue(draw.all { catalog.find(it)!!.oracleText!!.contains("draw a card", ignoreCase = true) })

        assertTrue(names("is:legendary t:creature id:r").contains("Krenko, Mob Boss"))
        assertFalse("id: means fits inside", names("id:u t:creature is:legendary").contains("Krenko, Mob Boss"))
    }

    @Test fun colorlessAndIdentityColorless() {
        val colorless = lib.search("c:c t:artifact", 5000).cards
        assertTrue(colorless.any { it.name == "Sol Ring" })
        assertTrue(colorless.all { colorsOf(it.manaCost).isEmpty() })
        assertTrue(names("id:c").contains("Sol Ring"))
        assertFalse(names("id:c").contains("Lightning Bolt"))
    }

    @Test fun negationRaritySetPowerKeyword() {
        assertTrue(names("-t:land t:artifact mv=1").contains("Sol Ring"))
        assertFalse(names("t:artifact -sol mv=1").contains("Sol Ring"))
        assertTrue(names("r:c s:lea").contains("Lightning Bolt"))
        assertTrue(names("r:u s:lea").contains("Sol Ring"))
        assertTrue(lib.search("pow>=10 t:creature", 500).cards.all { (it.power?.toDoubleOrNull() ?: 0.0) >= 10.0 })
        assertTrue(lib.search("k:flying t:creature c:w mv<=2", 500).cards.isNotEmpty())
    }

    @Test fun commanderLegalityAndEligibility() {
        assertTrue(names("is:commander krenko").contains("Krenko, Mob Boss"))
        assertFalse("banned in Commander", names("legal:commander fastbond").contains("Fastbond"))
        assertTrue(names("fastbond").contains("Fastbond"))
    }

    @Test fun emptyResultsAndUnknownOperatorsAreReported() {
        val none = lib.search("zzqqxxnotacard")
        assertTrue(none.cards.isEmpty())
        val odd = lib.search("frobnicate:7 sol ring")
        assertTrue(odd.errors.isNotEmpty())
    }

    @Test fun suggestionsArePrefixFirst() {
        val s = lib.suggestNames("sol r")
        assertEquals("Sol Ring", s.first())
        assertTrue(lib.suggestNames("s").isEmpty())
    }

    @Test fun searchIsFastEnoughForTyping() {
        lib.search("t:creature o:draw mv<=3") // warm
        val start = System.nanoTime()
        repeat(20) { lib.search("t:creature o:draw mv<=3 c:g") }
        val perQueryMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        assertTrue("per query $perQueryMs ms", perQueryMs < 150)
    }

    @Test fun replacementSuggestionsStayPlayableOnColorAndExplained() {
        // An unsupported green ramp creature, described the way Scryfall would describe it.
        val original = CardLibrary.Original("Imaginary Elf", "Creature — Elf Druid", 1.0, setOf('G'), "{T}: Add {G}.", emptyList())
        val picks = lib.suggestReplacements(original, allowedIdentity = setOf('G'))
        assertTrue(picks.isNotEmpty())
        picks.forEach { s ->
            assertTrue(catalog.find(s.card.name) != null)
            assertTrue(s.card.colors.all { it == 'G' })
            assertTrue("creature" in s.card.cardTypes)
            assertTrue(s.reasons.isNotEmpty())
        }
        assertTrue("mana elves rank high", picks.take(6).any { it.card.name == "Llanowar Elves" || it.card.oracleText.orEmpty().contains("Add {G}") })
    }

    @Test fun commanderReplacementOnlyOffersCommanders() {
        val original = CardLibrary.Original("Imaginary Goblin Boss", "Legendary Creature — Goblin Warrior", 4.0, setOf('R'), "Create goblin tokens.", emptyList())
        val picks = lib.suggestReplacements(original, allowedIdentity = null, forCommander = true)
        assertTrue(picks.isNotEmpty())
        assertTrue(picks.all { CardLibrary.isCommanderEligible(it.card) })
    }

    @Test fun editDistanceHandlesTranspositions() {
        assertEquals(1, CardLibrary.editDistance("bolt", "blot"))
        assertEquals(3, CardLibrary.editDistance("kitten", "sitting"))
    }
}
