package com.arcanatable.customcards

import com.arcanatable.customcards.support.CardPool
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The custom-card data shipped in the app matches these definitions and stays apart from Argentum's library. */
class CustomCardDataTest {
    private val assets = File(System.getProperty("arcana.assets") ?: "../app/src/main/assets/argentum")

    @Test
    fun shippedDataMatchesTheDefinitions() {
        val (jsonl, index) = exportData()
        assertEquals("Run ./gradlew :custom-cards:exportCards", jsonl, File(assets, "custom-cards.jsonl").readText())
        assertEquals("Run ./gradlew :custom-cards:exportCards", index, File(assets, "custom-cards.index.tsv").readText())
    }

    @Test
    fun everyCardLoadsBackFromItsDataWithRulesText() {
        File(assets, "custom-cards.jsonl").readLines().filter(String::isNotBlank).forEach { line ->
            val card = CardLoader.fromJsonPreservingIds(line)
            assertTrue(card.name, card.oracleText.isNotBlank())
            assertNotNull(card.name, card.setCode)
        }
    }

    /**
     * Every Arcana card is kept. When Argentum also has one, exactly one version is in play: Argentum's
     * (its scenario tests then run against Argentum's card, see CardPool.inPlay), or Arcana's when
     * custom-overrides.txt lists it. The weekly upgrade maintains that list (weekly.py reconcile).
     */
    @Test
    fun everyOverrideIsACustomCardThatArgentumAlsoHas() {
        val library = File(assets, "all-cards.index.tsv").readLines().map { it.substringBefore('\t').lowercase() }.toSet()
        val custom = CustomCards.all.map { it.name.lowercase() }.toSet()
        val notCustom = CardPool.overrides.filter { it !in custom }
        assertTrue("custom-overrides.txt names cards Arcana doesn't define: $notCustom", notCustom.isEmpty())
        val stale = CardPool.overrides.filter { it !in library }
        assertTrue("custom-overrides.txt lists cards Argentum doesn't have (remove them): $stale", stale.isEmpty())
    }

    @Test
    fun everyCustomCardIsStillPlayable() {
        // Superseded or overridden, each Arcana card resolves to exactly one definition in play.
        for (card in CustomCards.all) {
            val played = CardPool.inPlay(card)
            assertTrue("${card.name} has no playable version", played.name.equals(card.name, ignoreCase = true))
        }
    }

    @Test
    fun registeredDecksAreCovered() {
        // The registry grows as missing precon cards are implemented. Test completeness
        // and uniqueness rather than pinning a number that changes with each batch.
        val registeredNames = CustomCards.all.map { it.name }
        val scenarioNames = CardPool.custom.map { it.name }
        assertTrue("No custom card definitions registered", registeredNames.isNotEmpty())
        assertEquals("Duplicate custom-card names", registeredNames.size, registeredNames.toSet().size)
        assertEquals("Scenario card pool differs from exported registry",
            registeredNames.toSet(), scenarioNames.toSet())
    }
}
