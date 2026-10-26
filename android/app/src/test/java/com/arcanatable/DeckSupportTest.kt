package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Import-time Argentum support: parsing, card identity, classification, suggestions and saved-list swaps. */
class DeckSupportTest {

    // ---- Parsing (existing importer, now with direct coverage) ----

    @Test fun parsesManaBoxAndMoxfieldStyleListsWithCommanderAndSideboard() {
        val entries = parseDeckList(
            """
            Commander
            1 Rocco, Cabaretti Caterer (SNC) 190
            Deck
            2x Sol Ring (C21) 263
            1 Fable of the Mirror-Breaker // Reflection of Kiki-Jiki
            [INSTANTS]
            1 Lightning Bolt [M10] 146
            Sideboard
            1 Counterspell
            """.trimIndent()
        )
        val commander = entries.single { it.commander }
        assertEquals("Rocco, Cabaretti Caterer", commander.name)
        assertEquals("SNC", commander.setCode)
        assertEquals("190", commander.collectorNumber)
        assertEquals(2, entries.single { it.name == "Sol Ring" }.count)
        assertEquals("M10", entries.single { it.name == "Lightning Bolt" }.setCode)
        assertTrue(entries.none { it.name == "Counterspell" })
        assertTrue(entries.none { it.name.contains("INSTANTS", ignoreCase = true) })
    }

    @Test fun parsesCsvExportWithZoneColumn() {
        val entries = parseDeckList(
            """
            Count,Name,Set,Collector Number,Board
            1,"Miles ""Tails"" Prower",SLD,1,commander
            3,Island,UST,213,main
            """.trimIndent()
        )
        assertEquals("Miles \"Tails\" Prower", entries.single { it.commander }.name)
        assertEquals(3, entries.single { it.name == "Island" }.count)
    }

    // ---- Library line decoding ----

    @Test fun decodesLibraryLineFieldsAndEscapes() {
        val line = """{"name":"Test \"Quoted\" Card","manaCost":"{1}{W/U}","typeLine":"Legendary Creature — Human Wizard","colorIdentityOverride":["WHITE","BLUE"],"metadata":{"imageUri":"https://img/x.jpg"}}"""
        val card = SupportedCardCatalog.parseLibraryLine(line, custom = false, kind = "card")!!
        assertEquals("Test \"Quoted\" Card", card.name)
        assertEquals(2.0, card.manaValue, 0.0)
        assertEquals(setOf('W', 'U'), card.colors)
        assertEquals(setOf("creature"), card.cardTypes)
        assertEquals(setOf("human", "wizard"), card.subtypes)
        assertEquals("https://img/x.jpg", card.imageUrl)
        assertEquals("café", SupportedCardCatalog.jsonString("""{"name":"café"}""", "name"))
    }

    @Test fun manaValueAndColorsFromCost() {
        assertEquals(1.0, manaValueOf("{X}{X}{G}"), 0.0)
        assertEquals(6.0, manaValueOf("{2/W}{2/W}{2/W}"), 0.0)
        assertEquals(1.0, manaValueOf("{G/P}"), 0.0)
        assertEquals(setOf('B', 'G'), colorsOf("{1}{B/G}{G}"))
        assertTrue(colorsOf("{4}").isEmpty())
    }

    // ---- Classification and identity ----

    private fun card(name: String, type: String, cost: String, custom: Boolean = false, kind: String = "card", identity: Set<Char>? = null) =
        SupportedCard(name, type, cost, null, custom, identity, kind)

    private val sample = SupportedCardCatalog(listOf(
        card("Shared Name", "Creature — Elf", "{G}"),
        card("Shared Name", "Creature — Goblin", "{R}", custom = true),
        card("Custom Only", "Sorcery", "{2}{R}", custom = true),
        card("Fable of the Mirror-Breaker", "Enchantment — Saga", "{2}{R}"),
        card("Treasure", "Artifact — Treasure", "", kind = "token"),
        card("Forest", "Basic Land — Forest", "", kind = "basic", identity = setOf('G')),
        card("Llanowar Elves", "Creature — Elf Druid", "{G}"),
        card("Elvish Mystic", "Creature — Elf Druid", "{G}"),
        card("Grizzly Bears", "Creature — Bear", "{1}{G}"),
        card("Hill Giant", "Creature — Giant", "{3}{R}"),
        card("Giant Growth", "Instant", "{G}"),
    ))

    @Test fun argentumDefinitionWinsOverCustomOfSameName() {
        assertEquals(CardSupport.ARGENTUM, sample.support("shared name"))
        assertEquals("Creature — Elf", sample.find("Shared Name")!!.typeLine)
        assertEquals(CardSupport.CUSTOM_UNVERIFIED, sample.support("Custom Only"))
        assertEquals(CardSupport.UNSUPPORTED, sample.support("Not A Real Card"))
    }

    @Test fun doubleFacedNamesMatchFrontFaceAndTokensAreNotDeckCards() {
        assertEquals(CardSupport.ARGENTUM, sample.support("Fable of the Mirror-Breaker // Reflection of Kiki-Jiki"))
        assertEquals(CardSupport.UNSUPPORTED, sample.support("Treasure"))
        assertTrue(sample.search("treas").isEmpty())
    }

    @Test fun reportCountsCopiesAndNeverClaimsVerification() {
        val report = sample.check(listOf(
            DeckEntry("Mystery Commander", 1, commander = true),
            DeckEntry("Grizzly Bears", 3),
            DeckEntry("Custom Only", 2),
            DeckEntry("Nope", 1),
        ))
        assertEquals(3, report.argentumCopies)
        assertEquals(2, report.customCopies)
        assertEquals(2, report.unsupportedCopies)
        assertEquals(listOf("Mystery Commander", "Nope"), report.unsupportedNames)
        assertTrue(report.commanderUnsupported)
        assertTrue(report.summary().contains("not gameplay-verified"))
        assertFalse(CardSupport.entries.any { it.label.contains("verified") && !it.label.contains("not verified") })
    }

    @Test fun searchRanksExactThenPrefixThenWordStart() {
        assertEquals(listOf("Elvish Mystic", "Llanowar Elves"), sample.search("elv").map { it.name })
        assertEquals("Hill Giant", sample.search("hill giant").first().name)
        assertTrue(sample.search("g").isEmpty())
    }

    @Test fun suggestionsStayInTypeAndCommanderIdentityAndSkipDeckCards() {
        val picks = sample.suggestReplacements(
            typeLine = "Creature — Elf Warrior", manaValue = 1.0, targetColors = setOf('G'),
            allowedIdentity = setOf('G'), exclude = setOf("Llanowar Elves"),
        ).map { it.name }
        // Same mana value, color and Elf subtype rank first; ties break by name.
        assertEquals(listOf("Elvish Mystic", "Shared Name"), picks.take(2))
        assertFalse("Llanowar Elves" in picks)
        assertFalse("Hill Giant" in picks) // red is outside the commander's identity
        assertFalse("Giant Growth" in picks) // not a creature
        assertFalse("Forest" in picks)
        assertTrue(sample.suggestReplacements("NEEDS REVIEW", null, emptySet(), null).isEmpty())
        assertTrue(sample.suggestReplacements("Creature — Elf", 1.0, setOf('G'), null, requireLegendary = true).isEmpty())
    }

    @Test fun offlineSearchResultsRetainCreatureStatsOracleTextAndKeywords() {
        val line = """{"name":"Emberwing Test","manaCost":"{2}{W}","typeLine":"Creature — Angel","oracleText":"Flying\n{R}: Emberwing Test gains haste until end of turn.","creatureStats":{"power":2,"toughness":3},"keywords":["FLYING","VIGILANCE"]}"""
        val card = SupportedCardCatalog.parseLibraryLine(line, custom = false, kind = "card")!!
        val result = card.toScryfallCard()
        assertEquals("Flying\n{R}: Emberwing Test gains haste until end of turn.", result.oracleText)
        assertEquals("2", result.power)
        assertEquals("3", result.toughness)
        assertEquals(listOf("Flying", "Vigilance"), result.keywords)
        assertEquals(3.0, result.manaValue!!, 0.0)
        // With no explicit identity override, a red activated ability must not be offered
        // as a replacement in a white-only Commander deck.
        assertEquals(setOf('W', 'R'), card.colors)
        val catalog = SupportedCardCatalog(listOf(card))
        assertTrue(catalog.suggestReplacements("Creature — Angel", 3.0, setOf('W'), setOf('W')).isEmpty())
        assertEquals("Emberwing Test",
            catalog.suggestReplacements("Creature — Angel", 3.0, setOf('W'), setOf('W', 'R')).single().name)
    }

    @Test fun explicitLibraryColorIdentityOverridesTextHeuristics() {
        val line = """{"name":"Custom Override","manaCost":"{G}","typeLine":"Creature — Wizard","oracleText":"{R}: Deal damage.","creatureStats":{"power":"*","toughness":"2"},"colorIdentityOverride":["BLUE"]}"""
        val card = SupportedCardCatalog.parseLibraryLine(line, custom = true, kind = "card")!!
        assertEquals(setOf('U'), card.colors)
        assertEquals("*", card.toScryfallCard().power)
        assertEquals("2", card.toScryfallCard().toughness)
    }


    @Test fun shippedCustomCardMetadataMatchesActualJsonlDefinitions() {
        val library = File(assets, "argentum/custom-cards.jsonl")
        assumeTrue("custom-card JSONL present", library.isFile)
        val raw = library.useLines { lines ->
            lines.firstOrNull { JSONObject(it).optString("name") == "Ancient Copper Dragon" }
        } ?: error("Ancient Copper Dragon missing from custom export")
        val card = SupportedCardCatalog.parseLibraryLine(raw, custom = true, kind = "card")!!
        val preview = card.toScryfallCard()
        assertEquals("6", preview.power)
        assertEquals("5", preview.toughness)
        assertTrue(preview.oracleText.orEmpty().contains("d20"))
        assertTrue("Flying" in preview.keywords)
    }

    @Test fun missingCreatureStatsDoNotInventPowerOrToughness() {
        val line = """{"name":"Statless Test","manaCost":"{1}{U}","typeLine":"Instant","oracleText":"Draw a card.","keywords":[]}"""
        val preview = SupportedCardCatalog.parseLibraryLine(line, custom = false, kind = "card")!!.toScryfallCard()
        assertNull(preview.power)
        assertNull(preview.toughness)
        assertEquals(emptyList<String>(), preview.keywords)
        assertEquals("Draw a card.", preview.oracleText)
    }

    @Test fun cardsDoNotInheritKeywordLabelsFromCreatedTokensOrAnimationEffects() {
        val file = File(assets, "argentum/custom-cards.jsonl")
        assumeTrue("custom-card JSONL present", file.isFile)
        val names = setOf(
            "Sai, Master Thopterist", "Celestial Colonnade", "Springleaf Parade",
            "Black Market Connections", "Tyranid Invasion",
        )
        val rows = file.useLines { lines ->
            lines.mapNotNull { line ->
                JSONObject(line).optString("name").takeIf { it in names }?.let { it to line }
            }.toMap()
        }
        assertEquals(names, rows.keys)
        rows.forEach { (name, line) ->
            val preview = SupportedCardCatalog.parseLibraryLine(line, custom = true, kind = "card")!!.toScryfallCard()
            assertEquals("$name must not inherit token or animation keyword labels", emptyList<String>(), preview.keywords)
        }
    }

    @Test fun nestedEffectStatisticsAreNotOriginalCardStatistics() {
        val line = """{"name":"Token Creator","manaCost":"{2}{G}","typeLine":"Sorcery","oracleText":"Create a 3/3 token.","script":{"spellEffect":{"type":"CreateToken","creatureStats":{"power":3,"toughness":3},"keywords":["TRAMPLE"]}}}"""
        val preview = SupportedCardCatalog.parseLibraryLine(line, custom = false, kind = "card")!!.toScryfallCard()
        assertNull(preview.power)
        assertNull(preview.toughness)
        assertEquals(emptyList<String>(), preview.keywords)
    }

    // ---- Saved deck list swaps ----

    @Test fun swapRewritesOnlyTheMatchingLineAndKeepsStructure() {
        val text = "[Commander]\n1 Old Commander\n[Mainboard]\n1 Lightning Bolt (M10) 146\n2 Sol Ring"
        val swapped = replaceCardInDeckText(text, "Lightning Bolt", "Shock")!!
        assertEquals("[Commander]\n1 Old Commander\n[Mainboard]\n1 Shock\n2 Sol Ring", swapped)
        val entries = parseDeckList(swapped)
        assertEquals("Old Commander", entries.single { it.commander }.name)
        assertEquals(4, entries.sumOf { it.count })
    }

    @Test fun swapSplitsMultipleCopiesAndRewritesCommanderLines() {
        val split = replaceCardInDeckText("Deck\n3x Sol Ring\n1 Island", "Sol Ring", "Arcane Signet")!!
        val entries = parseDeckList(split)
        assertEquals(2, entries.single { it.name == "Sol Ring" }.count)
        assertEquals(1, entries.single { it.name == "Arcane Signet" }.count)
        assertEquals(4, entries.sumOf { it.count })

        val commander = replaceCardInDeckText("Commander: Old One\n1 Island", "Old One", "New One")!!
        assertEquals("New One", parseDeckList(commander).single { it.commander }.name)
    }

    @Test fun swapRefusesCsvAndMissingLines() {
        assertNull(replaceCardInDeckText("Count,Name\n1,Sol Ring", "Sol Ring", "Arcane Signet"))
        assertNull(replaceCardInDeckText("1 Island", "Sol Ring", "Arcane Signet"))
        assertNull(replaceCardInDeckText("1 Island", "Island", " "))
    }

    // ---- Shipped library: same answers as the offline engine ----

    private val assets = File("src/main/assets")
    private fun open(path: String) = File(assets, path).takeIf { it.exists() }?.inputStream()

    @Test fun shippedLibraryMatchesOfflineEngineForEveryCustomCardAndPreconEntry() {
        assumeTrue("full Argentum library asset present", File(assets, "argentum/all-cards.index.tsv").exists())
        val catalog = SupportedCardCatalog.load(::open)
        val engine = OfflineCardLibrary(::open)
        val nonTokenRows = File(assets, "argentum/all-cards.index.tsv").readLines().count { it.isNotBlank() && !it.endsWith("\ttoken") }
        assertTrue("catalog covers every non-token Argentum card", catalog.size >= nonTokenRows)

        val names = linkedSetOf<String>()
        File(assets, "argentum/custom-cards.index.tsv").readLines().filter { it.isNotBlank() }.mapTo(names) { it.substringBefore('\t') }
        File(assets, "precons").listFiles { f -> f.extension == "txt" }.orEmpty().forEach { f -> parseDeckList(f.readText()).mapTo(names) { it.name } }
        val manifest = File("../../investigation/argentum-six-precons/deck-manifest.json")
        if (manifest.exists()) {
            val decks = JSONObject(manifest.readText()).getJSONArray("decks")
            for (d in 0 until decks.length()) {
                val cards = decks.getJSONObject(d).getJSONArray("cards")
                for (c in 0 until cards.length()) names += cards.getJSONObject(c).getString("name")
            }
        }
        names += listOf("Not A Real Card", "Sol Ring", "Island")
        assertTrue(names.size > 300)
        names.forEach { name ->
            val canonical = engine.canonical(name)
            val support = catalog.support(name)
            assertEquals("indexed? $name", canonical != null, support.isIndexed)
            if (canonical != null) {
                assertEquals("custom? $name", engine.isCustom(name), support == CardSupport.CUSTOM_UNVERIFIED)
                assertEquals("canonical spelling for $name", canonical, catalog.find(name)!!.name)
            }
        }
    }

    @Test fun shippedLibrarySuggestionsAreSupportedAndCarryImages() {
        assumeTrue("full Argentum library asset present", File(assets, "argentum/all-cards.index.tsv").exists())
        val catalog = SupportedCardCatalog.load(::open)
        val picks = catalog.suggestReplacements("Creature — Elf Druid", 1.0, setOf('G'), setOf('G'), exclude = setOf("Llanowar Elves"))
        assertEquals(5, picks.size)
        picks.forEach { pick ->
            assertTrue(pick.name, catalog.support(pick.name).isIndexed)
            assertTrue(pick.name, "creature" in pick.cardTypes)
            assertTrue(pick.name, pick.colors.all { it == 'G' })
            assertNotNull(pick.name, pick.toScryfallCard().imageUrl)
        }
        assertEquals("Llanowar Elves", catalog.search("llanowar elves").first().name)
    }
}
