package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeathsPresence
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class DeathsPresenceScenarioTest {
    @Test fun friendlyDeathTransfersLastKnownPowerToChosenSurvivor() {
        val d = customScenario(DeathsPresence, "Grizzly Bears", "Craw Wurm", "Doom Blade", "Giant Growth")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Death's Presence")
        val dying = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val survivor = d.putCreatureOnBattlefield(p, "Craw Wurm")

        // Increase power before the death. X must use the last-known 5 power,
        // not the card's printed 2 power after leaving the battlefield.
        val growth = d.putCardInHand(p, "Giant Growth")
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, growth, listOf(dying)).error)
        d.resolveAll()
        assertEquals(5, d.projected.getPower(dying))

        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, blade, listOf(dying)).error)
        d.resolveAll(Choices(targets = listOf(survivor)))
        assertEquals("five +1/+1 counters must use departed creature's actual power",
            11, d.projected.getPower(survivor))
        assertNull(d.findPermanent(p, "Grizzly Bears"))
    }

    @Test fun opponentCreatureDyingMustNotBuffOurCreature() {
        val d = customScenario(DeathsPresence, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Death's Presence")
        val survivor = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, blade, listOf(enemy)).error)
        d.resolveAll()
        assertEquals(2, d.projected.getPower(survivor))
    }
}
