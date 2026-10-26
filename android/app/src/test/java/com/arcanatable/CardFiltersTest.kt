package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** The deck builder's FILTERS panel: every filter on the real offline library, sorting, presets. */
class CardFiltersTest {
    companion object {
        lateinit var lib: CardLibrary

        @BeforeClass @JvmStatic fun load() {
            val assets = File("src/main/assets")
            assumeTrue("full Argentum library asset present", File(assets, "argentum/all-cards.jsonl").exists())
            lib = CardLibrary(SupportedCardCatalog.load { File(assets, it).takeIf(File::exists)?.inputStream() })
        }

        fun names(f: CardFilters, q: String = "", limit: Int = 5000) = lib.search(q, f, limit).cards
    }

    @Test fun oracleAndTypeTermsAllMustMatch() {
        val cards = names(CardFilters(oracleTerms = listOf("flying", "draw a card"), typeTerms = listOf("Creature")))
        assertTrue(cards.isNotEmpty())
        cards.forEach {
            val text = it.oracleText.orEmpty().lowercase()
            assertTrue(it.name, "flying" in text && "draw a card" in text && "creature" in it.typeLine.lowercase())
        }
    }

    @Test fun manaCostContainsSymbols() {
        val cards = names(CardFilters(manaCosts = listOf("2{G}{W}"), colors = setOf('G', 'W'), colorMatch = ColorMatch.EXACTLY))
        assertTrue(cards.isNotEmpty())
        cards.forEach { c ->
            val cost = ManaCost.parse(c.manaCost.substringBefore(" // "))!!
            assertTrue(c.name, cost.generic >= 2 && (cost.symbols["G"] ?: 0) >= 1 && (cost.symbols["W"] ?: 0) >= 1)
        }
        assertEquals(ManaCost.parse("2GW"), ManaCost.parse("{2}{G}{W}"))
        assertEquals(mapOf("G/W" to 2), ManaCost.parse("{2}{G/W}{G/W}")!!.symbols)
        assertNull("a typo is reported, not guessed", ManaCost.parse("2{G"))
    }

    @Test fun colorModesAndCounts() {
        val exactly = names(CardFilters(colors = setOf('U', 'B'), colorMatch = ColorMatch.EXACTLY), limit = 400)
        assertTrue(exactly.isNotEmpty())
        exactly.forEach { assertEquals(it.name, setOf('U', 'B'), colorsOf(it.manaCost.substringBefore(" // "))) }
        names(CardFilters(colors = setOf('G'), colorMatch = ColorMatch.AT_MOST, useIdentity = true), limit = 400).forEach {
            assertTrue(it.name, it.colors.all { c -> c == 'G' })
        }
        names(CardFilters(minColors = 3), limit = 400).forEach { assertTrue(it.name, colorsOf(it.manaCost.substringBefore(" // ")).size >= 3) }
        names(CardFilters(colorless = true), limit = 400).forEach { assertTrue(it.name, colorsOf(it.manaCost.substringBefore(" // ")).isEmpty()) }
    }

    @Test fun rarityStatsAndSets() {
        val mythicCheap = names(CardFilters(rarities = setOf("MYTHIC"), stats = listOf(StatRange(StatField.MANA_VALUE, StatOp.LTE, 2.0))))
        assertTrue(mythicCheap.isNotEmpty())
        mythicCheap.forEach { assertTrue(it.name, it.rarity == "MYTHIC" && it.manaValue <= 2.0) }
        names(CardFilters(stats = listOf(StatRange(StatField.POWER, StatOp.BETWEEN, 5.0, 6.0))), limit = 400).forEach {
            assertTrue(it.name, it.power!!.toDouble() in 5.0..6.0)
        }
        val set = lib.setCodes.first()
        val inSet = names(CardFilters(sets = listOf(set.lowercase())), limit = 400)
        assertTrue(inSet.isNotEmpty())
        inSet.forEach { assertTrue(it.setCode.equals(set, true)) }
        names(CardFilters(sets = listOf(set), excludeSets = true), limit = 400).forEach { assertFalse(it.setCode.equals(set, true)) }
    }

    @Test fun commanderLegalOnlyKeepsCardsWithoutLegalityData() {
        val all = lib.search("", CardFilters(commanderLegalOnly = false), Int.MAX_VALUE).total
        val legal = lib.search("", CardFilters(), Int.MAX_VALUE).cards
        assertTrue(legal.none { it.commanderLegal == false })
        assertEquals(all - lib.cards.count { it.card.commanderLegal == false }, legal.size)
    }

    @Test fun sortsByChosenKey() {
        val byMv = names(CardFilters(typeTerms = listOf("creature"), sort = CardSort.MANA_VALUE, descending = true), limit = 200)
        assertEquals(byMv.map { it.manaValue }.sortedDescending(), byMv.map { it.manaValue })
        val byPower = names(CardFilters(typeTerms = listOf("creature"), sort = CardSort.POWER, descending = true), limit = 50)
        assertTrue(byPower.first().power!!.toDouble() >= byPower.last().power!!.toDouble())
        // Name relevance still leads when a name is typed and the sort is by name.
        assertEquals("Sol Ring", lib.search("sol ring", CardFilters(), 5).cards.first().name)
    }

    @Test fun typedQueryAndFiltersCombine() {
        val cards = lib.search("t:creature", CardFilters(colors = setOf('R'), colorMatch = ColorMatch.EXACTLY), 200).cards
        assertTrue(cards.isNotEmpty())
        cards.forEach { assertTrue("creature" in it.typeLine.lowercase() && colorsOf(it.manaCost.substringBefore(" // ")) == setOf('R')) }
    }

    @Test fun presetsRoundTripAndSummaries() {
        val f = CardFilters(oracleTerms = listOf("draw a card"), typeTerms = listOf("Angel"), manaCosts = listOf("1U"), colors = setOf('U', 'W'),
            colorMatch = ColorMatch.AT_MOST, useIdentity = true, minColors = 1, maxColors = 2, sets = listOf("CMM"), excludeSets = true,
            rarities = setOf("RARE", "MYTHIC"), keywords = listOf("flying"), stats = listOf(StatRange(StatField.MANA_VALUE, StatOp.BETWEEN, 1.0, 3.0)),
            commanderLegalOnly = false, sort = CardSort.RARITY, descending = true)
        assertEquals(f, CardFilters.decode(f.encode()))
        assertEquals(CardFilters(), CardFilters.decode(""))
        val presets = FilterPresets.upsert(listOf(FilterPreset("Blue", f)), FilterPreset("blue", CardFilters(typeTerms = listOf("Land"))))
        assertEquals(1, presets.size)
        assertEquals(presets, FilterPresets.decode(FilterPresets.encode(presets)))
        assertEquals(10, f.activeCount)
        // Every summary chip removes exactly its own part.
        f.summaryChips().forEach { (_, without) -> assertEquals(9, without.activeCount) }
        assertEquals(0, f.cleared().activeCount)
        assertNotNull(f.summaryChips().firstOrNull { it.first == "Text: draw a card" })
    }
}
