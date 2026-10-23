package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 112: blockers strip, picture links, library-search picks, premade deck check skip. */
class Build112Test {
    private fun creature(id: Int, name: String, tapped: Boolean = false, keywords: List<String> = emptyList()) =
        GameCard(id, name, "Creature — Bear", Color.Green, tapped = tapped, power = "2", toughness = "2", keywords = keywords)

    @Test fun blockersStripListsEachOpponentsUntappedCreatures() {
        val you = TablePlayer("You", battlefield = listOf(creature(1, "Mine")))
        val red = TablePlayer("Red", battlefield = listOf(creature(2, "Ready", keywords = listOf("Flying", "Haste")), creature(3, "Tired", tapped = true),
            GameCard(4, "Forest", "Basic Land — Forest", Color.Green)))
        val blue = TablePlayer("Blue", battlefield = listOf(creature(5, "Tapped", tapped = true)))
        val gone = TablePlayer("Gone", battlefield = listOf(creature(6, "Ghost")), isEliminated = true)
        val strip = opponentBlockers(listOf(you, red, blue, gone), you = 0)
        assertEquals(listOf("Red"), strip.map { it.first })
        assertEquals(listOf("Ready"), strip.first().second.map { it.name })
        assertEquals(listOf("flying"), blockingKeywords(strip.first().second.first()))
        assertEquals(listOf("reach", "first strike"), blockingKeywords(creature(7, "X", keywords = listOf("REACH", "First Strike", "Trample"))))
    }

    @Test fun scryfallPictureLinksResolveThroughTheJsonApi() {
        val named = "https://api.scryfall.com/cards/named?exact=Fatal%20Push&format=image&version=art_crop"
        assertTrue(CardImages.isScryfallApi(named))
        assertEquals("https://api.scryfall.com/cards/named?exact=Fatal%20Push", CardImages.jsonLookupFor(named))
        assertEquals("art_crop", CardImages.versionOf(named))
        assertEquals("Fatal Push", CardImages.nameIn(named))
        assertEquals("https://api.scryfall.com/cards/fic/2", CardImages.jsonLookupFor("https://api.scryfall.com/cards/fic/2?format=image&version=normal"))
        assertEquals("normal", CardImages.versionOf("https://cards.scryfall.io/normal/front/a/b/x.jpg"))
        assertNull(CardImages.jsonLookupFor("https://cards.scryfall.io/normal/front/a/b/x.jpg"))
        assertEquals("https://api.scryfall.com/cards/named?exact=Brigone%2C+Soldier+of+Meletis", CardImages.nameLookupFor("Brigone, Soldier of Meletis"))
        assertEquals("https://cards.scryfall.io/art_crop/front/6/2/62c4.jpg?1", CardImages.atSize("https://cards.scryfall.io/normal/front/6/2/62c4.jpg?1", "art_crop"))
        val dfc = JSONObject("""{"card_faces":[{"image_uris":{"normal":"front.jpg","art_crop":"front-art.jpg"}},{"image_uris":{"normal":"back.jpg"}}]}""")
        assertEquals("front-art.jpg", CardImages.imageFromJson(dfc, "art_crop"))
        assertEquals("plain.jpg", CardImages.imageFromJson(JSONObject("""{"image_uris":{"normal":"plain.jpg"}}"""), "large"))
    }

    @Test fun libraryPicksUseTheDecisionsCardInfoThenNameArt() {
        val withArt = libraryPick("e1", JSONObject().put("name", "Forest").put("typeLine", "Basic Land — Forest").put("imageUri", "https://cards.scryfall.io/normal/front/f.jpg"), "Card e1")
        assertEquals("Forest", withArt.name)
        assertEquals("https://cards.scryfall.io/normal/front/f.jpg", withArt.imageUrl)
        assertEquals("Library", withArt.group)
        val noArt = libraryPick("e2", JSONObject().put("name", "Dryad Arbor").put("imageUri", JSONObject.NULL), "Card e2")
        assertEquals("https://api.scryfall.com/cards/named?exact=Dryad+Arbor&format=image&version=normal", noArt.imageUrl)
    }

    @Test fun premadeDecksSkipTheServerCheck() {
        val skipped = ArgentumDeckValidation.skipped(99)
        assertTrue(skipped.valid)
        assertEquals(99, skipped.totalCards)
        assertTrue(skipped.unsupportedCards.isEmpty())
    }
}
