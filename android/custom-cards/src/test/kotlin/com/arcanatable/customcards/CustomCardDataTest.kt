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

    @Test
    fun noCustomCardShadowsAnArgentumCard() {
        val library = File(assets, "all-cards.index.tsv").readLines().map { it.substringBefore('\t').lowercase() }.toSet()
        val clashes = CustomCards.all.map { it.name }.filter { it.lowercase() in library }
        assertTrue("Argentum now has these; drop the custom versions: $clashes", clashes.isEmpty())
    }

    @Test
    fun registeredDecksAreCovered() {
        assertEquals(114, CustomCards.all.size)
        assertEquals(114, CardPool.custom.map { it.name }.toSet().size)
    }
}
