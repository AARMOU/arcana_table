package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SifterOfSkulls
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class SifterOfSkullsScenarioTest {
    @Test
    fun friendlyNontokenDeathCreatesOneManaCapableScion() {
        val d = customScenario(SifterOfSkulls, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val sifter = d.putCreatureOnBattlefield(p, "Sifter of Skulls")
        assertTrue(d.cardRegistry.getCard("Sifter of Skulls")!!.colors.isEmpty())
        assertNotNull(sifter)
        val victim = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, blade, listOf(victim)).error)
        d.resolveAll()
        val scions = d.permanentsNamed(p, "Eldrazi Scion")
        assertEquals(1, scions.size)
        assertTrue(d.abilityActions(p, scions.single()).isNotEmpty())
    }

    private fun doom(d: GameTestDriver, target: EntityId) {
        d.giveColorlessMana(d.player1, 1)
        d.giveMana(d.player1, Color.BLACK)
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "Doom Blade"), listOf(target)).error)
        d.resolveAll()
    }

    @Test fun itsOwnDeathDoesNotCreateAScion() {
        val d = customScenario(SifterOfSkulls, "Doom Blade")
        doom(d, d.putCreatureOnBattlefield(d.player1, "Sifter of Skulls"))
        assertTrue("Sifter is colorless and can be destroyed by Doom Blade",
            "Sifter of Skulls" in d.zoneNames(d.player1, Zone.GRAVEYARD))
        assertTrue(d.permanentsNamed(d.player1, "Eldrazi Scion").isEmpty())
    }

    @Test fun opposingNontokenDeathDoesNotCreateAScionForEitherPlayer() {
        val d = customScenario(SifterOfSkulls, "Grizzly Bears", "Doom Blade")
        d.putCreatureOnBattlefield(d.player1, "Sifter of Skulls")
        doom(d, d.putCreatureOnBattlefield(d.player2, "Grizzly Bears"))
        assertTrue("Grizzly Bears" in d.zoneNames(d.player2, Zone.GRAVEYARD))
        assertTrue(d.permanentsNamed(d.player1, "Eldrazi Scion").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Eldrazi Scion").isEmpty())
    }

    @Test fun friendlyCreatureTokenDeathDoesNotCreateAScion() {
        val d = customScenario(SifterOfSkulls, "Grizzly Bears", "Doom Blade")
        d.putCreatureOnBattlefield(d.player1, "Sifter of Skulls")
        val token = d.putToken(d.player1, "Grizzly Bears")
        doom(d, token)
        assertFalse(token in d.state.getBattlefield())
        assertTrue(d.permanentsNamed(d.player1, "Eldrazi Scion").isEmpty())
    }

    @Test fun simultaneousDeathWithTwoOtherNontokensStillCreatesExactlyTwoScions() {
        val d = customScenario(SifterOfSkulls, "Grizzly Bears", "Wrath of God")
        d.putCreatureOnBattlefield(d.player1, "Sifter of Skulls")
        repeat(2) { d.putCreatureOnBattlefield(d.player1, "Grizzly Bears") }
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putToken(d.player1, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 2)
        d.giveMana(d.player1, Color.WHITE, 2)
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "Wrath of God")).error)
        d.resolveAll()
        assertNull(d.findPermanent(d.player1, "Sifter of Skulls"))
        assertNull(d.findPermanent(d.player1, "Grizzly Bears"))
        assertNull(d.findPermanent(d.player2, "Grizzly Bears"))
        assertEquals(2, d.permanentsNamed(d.player1, "Eldrazi Scion").size)
        assertTrue(d.permanentsNamed(d.player2, "Eldrazi Scion").isEmpty())
    }

    @Test fun createdScionSacrificesForImmediateSpendableManaWithoutRetriggeringSifter() {
        val d = customScenario(SifterOfSkulls, "Grizzly Bears", "Doom Blade", "Sol Ring")
        d.putCreatureOnBattlefield(d.player1, "Sifter of Skulls")
        doom(d, d.putCreatureOnBattlefield(d.player1, "Grizzly Bears"))
        val token = d.permanentsNamed(d.player1, "Eldrazi Scion").single()
        assertTrue(d.abilityActions(d.player2, token).isEmpty())
        val action = d.abilityActions(d.player1, token).single().action
        assertNull(d.submit(action).error)
        assertTrue(d.state.stack.isEmpty())
        assertEquals(1, d.manaPool(d.player1).colorless)
        assertTrue(d.permanentsNamed(d.player1, "Eldrazi Scion").isEmpty())
        assertNotNull(d.submit(action).error)
        assertEquals(1, d.manaPool(d.player1).colorless)
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "Sol Ring")).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player1, "Sol Ring"))
        assertEquals(0, d.manaPool(d.player1).colorless)
        assertTrue(d.permanentsNamed(d.player1, "Eldrazi Scion").isEmpty())
    }
}
