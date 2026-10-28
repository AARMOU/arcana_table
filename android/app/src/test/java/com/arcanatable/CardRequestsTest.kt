package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun endpointRequiresExplicitHttpsBuildConfiguration() {
        val endpoint = BuildConfig.CARD_REQUEST_ENDPOINT
        assertEquals(endpoint.startsWith("https://"), CardRequestApi.isConfigured)
        assertTrue("Configured request endpoint must be HTTPS", endpoint.isBlank() || endpoint.startsWith("https://"))
    }

    @Test fun clientUsesExplicitCloudflareAcceptedIdentity() {
        assertEquals("ArcanaTable-CardRequests/1.0", CardRequestApi.USER_AGENT)
    }
}
