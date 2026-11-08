package com.arcanatable

import org.junit.Assert.*
import org.junit.Test

/** Owner, Nov 6 (batch 4): presets make really noticeable changes and still give a valid layout. */
class StudioPresetsTest {
    @Test fun everyPresetIsValidAndDifferent() {
        val start=StudioControlProject(theme=mapOf("accent" to "#FF0000"))
        val results=StudioPresets.all.map{StudioPresets.project(start,it,keepColours=false)}
        results.forEach{r->val back=StudioControlProject.parse(r.json());assertEquals(r.theme,back.theme);assertEquals(r.hiddenPieces,back.hiddenPieces);assertEquals(r.density,back.density)}
        assertEquals("no two presets look the same",StudioPresets.all.size,StudioPresets.all.map{it.look to results[StudioPresets.all.indexOf(it)]}.toSet().size)
        assertTrue("Classic goes back to the table's own colours",results.first().theme.isEmpty())
        assertEquals(StudioPresets.CLASSIC_LOOK,StudioPresets.all.first().look)
        StudioPresets.all.forEach{p->assertTrue("${p.name} hides only real pieces",p.hiddenPieces.all{it in SCREEN_PIECES})}
        StudioPresets.all.forEach{p->with(p.look){assertTrue(p.name,cardSize in .75f..1.4f && spacing in .5f..2f && buttons in .78f..1.3f && handCards in .7f..1.4f)}}
    }

    @Test fun keepMyColoursLeavesThePaletteAlone() {
        val start=StudioControlProject(theme=mapOf("accent" to "#FF0000"))
        val bigClear=StudioPresets.all.first{it.name=="Big & Clear"}
        assertEquals(start.theme,StudioPresets.project(start,bigClear,keepColours=true).theme)
        assertNotEquals(start.theme,StudioPresets.project(start,bigClear,keepColours=false).theme)
    }
}
