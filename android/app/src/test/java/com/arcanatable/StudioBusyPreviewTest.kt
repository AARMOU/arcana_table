package com.arcanatable

import org.junit.Assert.*
import org.junit.Test

/** Owner note 29, "Fill it up". */
class StudioBusyPreviewTest {
    @Test fun fillsFourCrowdedSeatsAndKeepsYourSeat() {
        val real=listOf(TablePlayer("TESTER",id="s0"),TablePlayer("AI",id="s1",isComputer=true))
        val busy=StudioBusyPreview.fill(real,0)
        assertEquals(4,busy.size)
        assertEquals("your seat keeps your name and id","TESTER",busy[0].name);assertEquals("s0",busy[0].id)
        assertEquals("s1",busy[1].id)
        assertTrue(busy.all{it.battlefield.size==18 && it.graveyard.size==30 && it.commanderCard!=null})
        assertTrue("long names",busy.flatMap{it.battlefield}.any{it.name.length>40})
        assertEquals("card ids are unique",busy.flatMap{it.battlefield+it.hand+it.graveyard+it.exile}.size,
            busy.flatMap{it.battlefield+it.hand+it.graveyard+it.exile}.map{it.id}.toSet().size)
        assertEquals(setOf("s1",busy[2].id),busy[0].commanderDamageReceived.keys)
    }

    @Test fun neverMoreThanFourSeats() {
        val real=(0 until 6).map{TablePlayer("P$it",id="p$it")}
        assertEquals(4,StudioBusyPreview.fill(real,3).size)
    }
}
