package com.arcanatable.customcards

// Independent real-Argentum tests for Hormagaunt Horde, queue #378 / candidate #380.
import com.arcanatable.customcards.cards.HormagauntHorde
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class HormagauntHordeIndependentCertificationTest {
    @Test fun ravenousXFiveDrawsExactlyOneCardAndMakesSixSix() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val card = d.putCardInHand(p, "Hormagaunt Horde")
        val before = d.getHandSize(p)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)
        assertNull(d.castXSpell(p, card, 5).error)
        d.resolveAll()
        assertEquals(6, d.projected.getPower(card))
        assertEquals(6, d.projected.getToughness(card))
        assertEquals("One card spent, one card drawn", before, d.getHandSize(p))
    }

    @Test fun ravenousXZeroIsOneOneWithoutCardDraw() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val card = d.putCardInHand(p, "Hormagaunt Horde")
        val before = d.getHandSize(p)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castXSpell(p, card, 0).error)
        d.resolveAll()
        assertEquals(1, d.projected.getPower(card))
        assertEquals(1, d.projected.getToughness(card))
        assertEquals(before - 1, d.getHandSize(p))
    }

    @Test fun landfallDoesNotReturnCardWhenPaymentDeclined() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Hormagaunt Horde")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)
        val land = d.putCardInHand(p, "Forest")
        assertNull(d.playLand(p, land).error)
        d.resolveAll(Choices(yes = false))
        assertTrue(d.getGraveyard(p).contains(card))
        assertFalse(d.getHand(p).contains(card))
    }
}
