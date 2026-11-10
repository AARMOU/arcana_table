package com.arcanatable

import org.junit.Assert.*
import org.junit.Test

/** Pure geometry regressions: phone layout must remain recoverable, even with extreme drags. */
class XpDesktopTest {
    @Test fun dragNeverLosesTheWindowOffscreen() {
        val window=XpRect(10f,20f,280f,400f)
        assertEquals(0f,window.moved(-900f,-900f,340f,700f,230f,250f).x)
        val lowerRight=window.moved(10000f,10000f,340f,700f,230f,250f)
        assertEquals(60f,lowerRight.x)
        assertEquals(300f,lowerRight.y)
    }

    @Test fun allEdgesAndCornersResizeWithinBounds() {
        XpEdge.values().forEach {edge->
            val resized=XpRect(25f,40f,260f,330f).resized(1000f,-1000f,edge,340f,700f,230f,250f)
            assertTrue("Width for $edge",resized.width in 230f..340f)
            assertTrue("Height for $edge",resized.height in 250f..700f)
            assertTrue("Left for $edge",resized.x>=0f)
            assertTrue("Top for $edge",resized.y>=0f)
            assertTrue("Right for $edge",resized.x+resized.width<=340f)
            assertTrue("Bottom for $edge",resized.y+resized.height<=700f)
        }
    }

    @Test fun incomingSavedLayoutIsClampedToFoldedDisplay() {
        val result=XpRect(600f,1000f,800f,900f).bounded(344f,650f,230f,250f)
        assertEquals(0f,result.x)
        assertEquals(0f,result.y)
        assertEquals(344f,result.width)
        assertEquals(650f,result.height)
    }

    @Test fun switchingPresetsDisablesXpAndRoundTripsSkin() {
        val xp=StudioPresets.all.single{it.name=="Windows XP"}
        val project=StudioPresets.project(StudioControlProject(),xp,false)
        assertEquals("Windows XP",project.skin)
        assertEquals(project.skin,StudioControlProject.parse(project.json()).skin)
        val classic=StudioPresets.project(project,StudioPresets.all.first{it.name=="Classic"},false)
        assertEquals("Arcana",classic.skin)
    }

    @Test(expected=IllegalArgumentException::class)
    fun unknownSkinCannotBeImported() {
        val json=StudioControlProject().json()
        StudioControlProject.parse(json.replace("\"Arcana\"","\"Not a real skin\""))
    }
}
