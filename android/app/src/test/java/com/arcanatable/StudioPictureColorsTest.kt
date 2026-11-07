package com.arcanatable

import org.junit.Assert.*
import org.junit.Test

/** Owner note 17: Match picture keeps the table dark and readable, and Shuffle gives another mix. */
class StudioPictureColorsTest {
    private fun hexLum(h:String):Float {val v=h.substring(1).toInt(16);return (0.2126f*((v shr 16) and 255)+0.7152f*((v shr 8) and 255)+0.0722f*(v and 255))/255f}
    private val sunset=IntArray(48*48){i->when{i<900->0xFF203070.toInt();i<1700->0xFFE06030.toInt();i<2100->0xFFF0C040.toInt();else->0xFF101010.toInt()}}

    @Test fun matchedColoursKeepTheTableDarkAndTheTextLight() {
        val t=StudioPictureColors.theme(sunset)
        assertEquals(setOf("background","panel","raised","accent","text","muted","gold"),t.keys)
        assertTrue(t.values.all{Regex("^#[0-9A-F]{6}$").matches(it)})
        assertTrue("dark table",hexLum(t["background"]!!)<0.15f)
        assertTrue("light text",hexLum(t["text"]!!)>0.8f)
        assertTrue("panel lighter than table",hexLum(t["panel"]!!)>hexLum(t["background"]!!))
        assertTrue("accent visible",hexLum(t["accent"]!!)>0.3f)
    }

    @Test fun shuffleGivesAnotherMixAndAGreyPictureStillWorks() {
        assertNotEquals(StudioPictureColors.theme(sunset,0),StudioPictureColors.theme(sunset,1))
        val grey=StudioPictureColors.theme(IntArray(100){0xFF808080.toInt()})
        assertTrue(hexLum(grey["background"]!!)<0.15f)
    }
}
