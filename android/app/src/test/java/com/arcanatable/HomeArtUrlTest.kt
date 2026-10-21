package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeArtUrlTest {
    @Test fun rewritesScryfallCardPictureToArtworkCrop() {
        assertEquals("https://cards.scryfall.io/art_crop/front/a/b/abc.jpg", homeArtUrl("https://cards.scryfall.io/normal/front/a/b/abc.jpg", null, "Card"))
    }

    @Test fun replacesCustomImageWithNameLookup() {
        assertEquals("https://api.scryfall.com/cards/named?exact=Card+Name&format=image&version=art_crop", homeArtUrl("https://custom.example/card.jpg", null, "Card Name"))
    }

    @Test fun usesBundledArtWhenTableHasNone() {
        assertEquals("https://cards.scryfall.io/art_crop/front/a/b/abc.jpg", homeArtUrl(null, "https://cards.scryfall.io/normal/front/a/b/abc.jpg", "Card"))
    }

    @Test fun fallsThroughUnsupportedTableImageToBundledArt() {
        assertEquals("https://cards.scryfall.io/art_crop/front/a/b/abc.jpg", homeArtUrl("https://custom.example/card.jpg", "https://cards.scryfall.io/normal/front/a/b/abc.jpg", "Card"))
    }

    @Test fun forcesApiImageLookupToUseArtCrop() {
        assertEquals("https://api.scryfall.com/cards/abc?format=image&version=art_crop", homeArtUrl("https://api.scryfall.com/cards/abc?format=image&version=normal", null, "Card"))
    }
}
