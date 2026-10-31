package com.arcanatable.customcards

import com.arcanatable.customcards.cards.IlluminorSzeras
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import org.junit.Assert.*
import org.junit.Test

class IlluminorSzerasScenarioTest {
    @Test
    fun sacrificesAnotherCreatureForBlackManaEqualToItsManaValue() {
        val d = customScenario(IlluminorSzeras, "Grizzly Bears")
        val p = d.player1
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(szeras)

        assertNull(d.activate(p, szeras).error)

        assertEquals(2, d.manaPool(p).black)
        assertTrue(d.getGraveyard(p).contains(bear))
        assertTrue(d.state.getEntity(szeras)!!.has<TappedComponent>())
    }

    @Test
    fun cannotActivateWhenOnlySzerasAndAnOpponentsCreatureAreAvailable() {
        val d = customScenario(IlluminorSzeras, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(szeras)

        assertNotNull(d.activate(p, szeras).error)

        assertEquals(0, d.manaPool(p).black)
        assertFalse(d.state.getEntity(szeras)!!.has<TappedComponent>())
        assertEquals(1, d.permanentsNamed(opponent, "Grizzly Bears").size)
    }
}
