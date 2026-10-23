package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionOrderTest {
    @Test fun dropsLegacyActionsAndKeepsEverySupportedActionOnce() {
        assertEquals(
            listOf("EXILE", "HAND", "GRAVEYARD", "STACK", "LOG", "CAST_COMMANDER", "PASS_TO_TURN", "UNDO"),
            normalizeArgentumQuickActionOrder("LIBRARY|EXILE|HAND|DRAW|EXILE|GRAVEYARD")
        )
    }

    @Test fun emptySavedOrderRestoresDefaults() {
        assertEquals(argentumQuickActionIds, normalizeArgentumQuickActionOrder(null))
        assertEquals(argentumQuickActionIds, normalizeArgentumQuickActionOrder(""))
    }
}
