package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CardRequestsTest {
    @Test fun requestNamesNormalizeWhitespaceAndDedupeSplitFaces() {
        val names = CardRequestApi.normalizeNames(listOf(
            "  Ratchet, Field Medic // Ratchet, Rescue Racer ",
            "Ratchet, Field Medic",
            "Goblin Welder",
            "Goblin Welder"
        ))
        assertEquals(listOf("Ratchet, Field Medic // Ratchet, Rescue Racer", "Goblin Welder"), names)
    }

    @Test fun requestNamesCapAtTenAndNeverImportOrMutateCards() {
        val names = CardRequestApi.normalizeNames((1..25).map { "Missing Card $it" })
        assertEquals(10, names.size)
        assertEquals("Missing Card 10", names.last())
    }

    @Test fun noDefaultPublicRequestsUntilIndependentlyConfigured() {
        assertFalse("No deployed request service is configured by default", CardRequestApi.isConfigured)
    }
}
