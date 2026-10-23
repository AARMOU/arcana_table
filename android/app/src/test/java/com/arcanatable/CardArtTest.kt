package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test

class CardArtTest {
    @Test fun loadedArtIsLabelledWithTheCardName() {
        // Found on a PC where Scryfall art loads: the art replaced the name text and was labelled
        // only "Magic card art", so screen readers (and tests) could no longer tell cards apart.
        assertEquals("Rampaging Baloths", cardArtDescription(label = null, fallbackName = "Rampaging Baloths"))
        assertEquals("Bello, Bard of the Brambles", cardArtDescription(label = "Bello, Bard of the Brambles", fallbackName = null))
        assertEquals("explicit label wins", "Bello", cardArtDescription(label = "Bello", fallbackName = "Other"))
        assertEquals("never blank", "Magic card art", cardArtDescription(label = " ", fallbackName = null))
        assertEquals("Magic card art", cardArtDescription(label = null, fallbackName = null))
    }
}
