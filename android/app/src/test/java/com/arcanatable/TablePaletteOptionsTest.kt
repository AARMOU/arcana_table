package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TablePaletteOptionsTest {
    @Test fun palettesMatchHtmlStudioExactly() {
        assertEquals(listOf("Arcana Original", "Slate", "Monochrome", "Deep Ocean"),
            tablePalettePresets.map { it.name })
        val expected = mapOf(
            "Arcana Original" to listOf("#101615", "#192220", "#26332F", "#89E3B0", "#EAF1ED", "#A4B1AB", "#FFD166"),
            "Slate" to listOf("#101416", "#1C2527", "#303A3D", "#B6D5D2", "#F0F5F3", "#9EADAA", "#D7C49A"),
            "Monochrome" to listOf("#0B0D0C", "#1A1C1B", "#2B302D", "#F2F2EB", "#F8F9F7", "#A4A9A4", "#C2C3BB"),
            "Deep Ocean" to listOf("#0B1419", "#17252B", "#26383E", "#85C6CF", "#E9F2F4", "#9BB2B8", "#D9C28C"),
        )
        for (preset in tablePalettePresets) {
            assertEquals(preset.name, expected[preset.name],
                tableColorRoles.map { tableColorHex(preset.colors.colorRole(it.first)) })
        }
    }

    @Test fun everyCustomColorRoleCanChangeIndependently() {
        val original = tablePaletteByName("Deep Ocean")
        for ((role, _) in tableColorRoles) {
            val updated = original.withColorRole(role, Color(0xFF123456))
            assertEquals(role, "#123456", tableColorHex(updated.colorRole(role)))
            for ((other, _) in tableColorRoles) {
                if (other != role) assertEquals(other, original.colorRole(other), updated.colorRole(other))
            }
        }
        assertEquals(7, tableColorRoles.size)
    }

    @Test fun hexColorValidationRejectsIncompleteInputAndRoundTrips() {
        for (bad in listOf("", "#12345", "#1234567", "123456", "#XXXXXX", "#12G456")) {
            assertNull(bad, parseTableColorHex(bad))
        }
        assertEquals("#0A2B3C", tableColorHex(parseTableColorHex("#0a2b3c")!!))
        assertTrue(tablePalettePresets.all { preset ->
            tableColorRoles.all { role -> parseTableColorHex(tableColorHex(preset.colors.colorRole(role.first))) != null }
        })
    }
}
