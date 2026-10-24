package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression fixtures for import zones and safety boundaries, not a gameplay-legality certificate. */
class DeckImportParserTest {
    private fun supported(name: String, type: String = "Legendary Creature — Human", text: String = "") =
        SupportedCard(name = name, typeLine = type, manaCost = "{2}", imageUrl = "https://example.org/img.png",
            custom = false, oracleText = text)

    private val catalog = SupportedCardCatalog(listOf(
        supported("Alpha", text = "Partner (You can have two commanders if both have partner.)"),
        supported("Beta", text = "Partner (You can have two commanders if both have partner.)"),
        supported("Rocco, Cabaretti Caterer"),
        supported("Not Partnered"),
        supported("Choose A Background", text = "Choose a Background (You can have a Background as a second commander.)"),
        supported("Noble Heritage", "Legendary Enchantment — Background"),
        supported("Sol Ring", "Artifact", ""),
        supported("Cid", "Legendary Creature — Human", "Some ability."),
        supported("Island", "Basic Land — Island"),
        supported("Lightning Bolt", "Instant", "Deal 3 damage."),
    ))

    @Test fun retainsTwoExplicitCommandersAndBlocksSingleSlotGameplay() {
        val parsed = parseDeckListDetailed("Commander (2)\n1 Alpha\n1 Beta\nDeck\n10 Cid\n1 Sol Ring", catalog)
        assertEquals(listOf("Alpha", "Beta"), parsed.commanders.map { it.name })
        assertEquals(true, parsed.partnerPairEligible)
        assertEquals(10, parsed.main.first { it.name == "Cid" }.count)
        assertTrue(parsed.gameBlockers.any { it.contains("Two commanders") })
        assertEquals(2, parseDeckList("Commander\n1 Alpha\n1 Beta").count { it.commander })
    }

    @Test fun illegalPairIsNotTreatedAsLegalPartnerPair() {
        val parsed = parseDeckListDetailed("Commander\n1 Alpha\n1 Not Partnered", catalog)
        assertEquals(false, parsed.partnerPairEligible)
        assertTrue(parsed.gameBlockers.isNotEmpty())
    }

    @Test fun companionAndSideboardStayOutsideMainDeck() {
        val parsed = parseDeckListDetailed(
            "Commander\n1 Rocco, Cabaretti Caterer\nCompanion\n1 Lurrus of the Dream-Den\n" +
                "Deck\n2 Sol Ring\nSideboard (15)\n3 Island\nMaybeboard\n1 Lightning Bolt"
        )
        assertEquals(listOf("Sol Ring"), parsed.main.map { it.name })
        assertEquals(listOf("Lurrus of the Dream-Den"), parsed.companion.map { it.name })
        assertEquals(3, parsed.sideboard.single().count)
        assertEquals("Lightning Bolt", parsed.excluded.single().name)
        assertTrue(parseDeckList("Companion\n1 Lurrus of the Dream-Den\nDeck\n1 Sol Ring")
            .none { it.name.startsWith("Lurrus") })
    }

    @Test fun decorationAndPrintingFixturesRemainIntact() {
        val parsed = parseDeckListDetailed(
            """
            Name My Deck
            About
            [Ramp]
            1 Sol Ring [Ramp]
            1 Island {noDeck}
            1 Alpha [Commander{top}]
            1 Lightning Bolt [M10#146]
            1 Beta *F* *A* #featured
            1 Rocco, Cabaretti Caterer *CMDR*
            """.trimIndent()
        )
        assertEquals("My Deck", parsed.deckName)
        assertEquals(listOf("Alpha", "Rocco, Cabaretti Caterer"), parsed.commanders.map { it.name })
        assertEquals("Island", parsed.excluded.single().name)
        assertEquals("M10", parsed.main.first { it.name == "Lightning Bolt" }.setCode)
        assertEquals("146", parsed.main.first { it.name == "Lightning Bolt" }.collectorNumber)
        assertTrue(parsed.main.any { it.name == "Beta" })
        assertTrue(parsed.diagnostics.isEmpty())
    }

    @Test fun malformedLinesHaveOneBasedSourceDiagnosticsButBareNamesRemainValid() {
        val parsed = parseDeckListDetailed("1 Sol Ring\n8\nFormat: Commander\nbad:value\nIsland")
        assertEquals(2, parsed.diagnostics.size)
        assertEquals(2, parsed.diagnostics.first().line)
        assertEquals("8", parsed.diagnostics.first().original)
        assertEquals(4, parsed.diagnostics.last().line)
        assertEquals(listOf("Sol Ring", "Island"), parsed.main.map { it.name })
    }

    @Test fun doesNotInferFromOrdinaryConstructedSideboard() {
        val text = "Deck\n60 Island\nSideboard\n15 Lightning Bolt"
        val parsed = parseDeckListDetailed(text, catalog)
        assertTrue(parsed.commanders.isEmpty())
        assertEquals(15, parsed.sideboard.single().count)
    }

    @Test fun conservativelyInfersOnlyCatalogEligibleMtgoSideboardCommander() {
        val parsed = parseDeckListDetailed("Deck\n99 Island\nSideboard\n1 Rocco, Cabaretti Caterer", catalog)
        assertEquals("Rocco, Cabaretti Caterer", parsed.commanders.single().name)
        assertTrue(parsed.sideboard.isEmpty())
        val notFound = parseDeckListDetailed("Deck\n99 Island\nSideboard\n1 Unknown Leader", catalog)
        assertTrue(notFound.commanders.isEmpty())
        assertEquals("Unknown Leader", notFound.sideboard.single().name)
    }

    @Test fun conservativelyInfersEligibleFinalBlockOnly() {
        val parsed = parseDeckListDetailed("99 Island\n\n1 Rocco, Cabaretti Caterer", catalog)
        assertEquals("Rocco, Cabaretti Caterer", parsed.commanders.single().name)
        assertEquals(99, parsed.main.single().count)
    }

    @Test fun noNetworkOfflineLookupCarriesOriginalMetadataWithoutFakePrinting() {
        val entry = DeckEntry("Lightning Bolt", 1, "M10", "146")
        val offline = offlineImportMatches(listOf(entry, DeckEntry("Missing", 1)), catalog)
        assertEquals(1, offline.size)
        val card = offline[entry]!!
        assertEquals("Deal 3 damage.", card.oracleText)
        assertEquals("https://example.org/img.png", card.imageUrl)
        assertNull(card.setCode)
        assertNull(card.collectorNumber)
    }

    @Test fun legacyCsvAndPrintedQuantitiesArePreserved() {
        val parsed = parseDeckListDetailed(
            "Count,Name,Set,Collector Number,Board\n" +
                "1,\"Rocco, Cabaretti Caterer\",SNC,190,commander\n" +
                "3,Island,UST,213,main\n" +
                "1,Lightning Bolt,M10,146,sideboard"
        )
        assertEquals("Rocco, Cabaretti Caterer", parsed.commanders.single().name)
        assertEquals("190", parsed.commanders.single().collectorNumber)
        assertEquals(3, parsed.main.single().count)
        assertEquals("M10", parsed.sideboard.single().setCode)
        val plain = parseDeckList("10 Cid\n1 Lightning Bolt (M10) 146")
        assertEquals(10, plain.first { it.name == "Cid" }.count)
        assertFalse(plain.any { it.count > 10 })
    }
}
