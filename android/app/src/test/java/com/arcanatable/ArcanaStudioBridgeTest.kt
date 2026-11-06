package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArcanaStudioBridgeTest {
    private val sample = """
        {"version":1,"layouts":{"hand":[],"board":[],"combat":[]},"theme":{
        "background":"#0B1419","panel":"#17252B","raised":"#26383E","accent":"#85C6CF",
        "text":"#E9F2F4","muted":"#9BB2B8","gold":"#D9C28C"}}
    """.trimIndent()

    @Test fun validStudioThemeMapsAllSevenColorsIncludingGoldToCounter() {
        val parsed = readStudioTheme(sample)
        assertNotNull(parsed)
        assertEquals("#0B1419", tableColorHex(parsed!!.background))
        assertEquals("#17252B", tableColorHex(parsed.panel))
        assertEquals("#26383E", tableColorHex(parsed.raised))
        assertEquals("#85C6CF", tableColorHex(parsed.accent))
        assertEquals("#E9F2F4", tableColorHex(parsed.text))
        assertEquals("#9BB2B8", tableColorHex(parsed.muted))
        assertEquals("#D9C28C", tableColorHex(parsed.counter))
    }

    @Test fun nativePaletteCanBeWrittenBackAsStudioThemeJson() {
        val palette = tablePaletteByName("Slate")
        val project = """{"version":1,"layouts":{"hand":[],"board":[],"combat":[]},"theme":""" +
            studioThemeJson(palette) + "}"
        assertEquals(palette, readStudioTheme(project))
    }

    @Test fun malformedOrUnsafeProjectsCannotChangeTheNativeTheme() {
        assertNull(readStudioTheme("not json"))
        assertNull(readStudioTheme(sample.replace("\"version\":1", "\"version\":2")))
        assertNull(readStudioTheme(sample.replace("\"hand\":[]", "\"hand\":\"oops\"")))
        assertNull(readStudioTheme(sample.replace("#85C6CF", "red")))
        assertNull(readStudioTheme(sample.replace("\"gold\":\"#D9C28C\"", "\"gold\":null")))
        assertNull(readStudioTheme(sample.replace("\"combat\":[]", "\"garbage\":[]")))
    }
}
