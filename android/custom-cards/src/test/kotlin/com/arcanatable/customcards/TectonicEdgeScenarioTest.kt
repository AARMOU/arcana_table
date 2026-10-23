package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TectonicEdge
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class TectonicEdgeScenarioTest {
    @Test fun opponentNeedsFourLandsIndividuallyBeforeActivationIsLegal() {
        val d = customScenario(TectonicEdge, "Tendo Ice Bridge")
        val p = d.player1
        val edge = d.putPermanentOnBattlefield(p, "Tectonic Edge")
        val target = d.putPermanentOnBattlefield(d.player2, "Tendo Ice Bridge")
        repeat(2) { d.putPermanentOnBattlefield(d.player2, "Forest") }
        val ability = d.cardRegistry.getCard("Tectonic Edge")!!.script.activatedAbilities[1]
        d.giveColorlessMana(p, 1)
        val a = ActivateAbility(p, edge, ability.id, targets = listOf(ChosenTarget.Permanent(target)))
        assertNotNull("only 3 opponent lands must block activation", d.submit(a).error)
        assertTrue(edge in d.getPermanents(p))
        assertTrue(target in d.getPermanents(d.player2))

        d.putPermanentOnBattlefield(d.player2, "Forest")
        assertNull("a single opponent at 4 lands enables activation", d.submit(a).error)
        d.resolveAll()
        assertFalse(edge in d.getPermanents(p))
        assertFalse(target in d.getPermanents(d.player2))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Tendo Ice Bridge"))
    }

    @Test fun anyNonbasicMayBeTargetedAfterThresholdButBasicLandMayNot() {
        val d = customScenario(TectonicEdge, "Tendo Ice Bridge")
        val p = d.player1
        val edge = d.putPermanentOnBattlefield(p, "Tectonic Edge")
        val ownNonbasic = d.putPermanentOnBattlefield(p, "Tendo Ice Bridge")
        val basic = d.putPermanentOnBattlefield(d.player2, "Forest")
        repeat(3) { d.putPermanentOnBattlefield(d.player2, "Forest") }
        d.giveColorlessMana(p, 1)
        val ability = d.cardRegistry.getCard("Tectonic Edge")!!.script.activatedAbilities[1]
        assertNotNull(d.submit(ActivateAbility(p, edge, ability.id,
            targets = listOf(ChosenTarget.Permanent(basic)))).error)
        assertNull(d.submit(ActivateAbility(p, edge, ability.id,
            targets = listOf(ChosenTarget.Permanent(ownNonbasic)))).error)
        d.resolveAll()
        assertFalse(ownNonbasic in d.getPermanents(p))
        assertTrue(basic in d.getPermanents(d.player2))
    }
}
