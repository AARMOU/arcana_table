package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BronzebeakForagers
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BronzebeakForagersScenarioTest {
    @Test
    fun entersExileTargetAndXAbilityOnlyMovesMatchingLinkedCardToOwnersGraveyard() {
        val d = customScenario(BronzebeakForagers, "Sol Ring")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val foragersCard = d.putCardInHand(d.player1, "Bronzebeak Foragers")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 3)

        assertNull(d.castSpell(d.player1, foragersCard).error)
        d.resolveAll(Choices(targets = listOf(ring)))
        assertEquals(listOf("Sol Ring"), d.zoneNames(d.player2, Zone.EXILE))

        val foragers = d.findPermanent(d.player1, "Bronzebeak Foragers")!!
        val ability = d.cardRegistry.getCard("Bronzebeak Foragers")!!.script.activatedAbilities.single()

        // X=0 must not allow the linked Sol Ring (mana value 1) as a target.
        val wrongX = ActivateAbility(
            d.player1,
            foragers,
            ability.id,
            targets = listOf(ChosenTarget.Card(ring, d.player2, Zone.EXILE)),
            xValue = 0,
        )
        assertNotNull("a target whose mana value differs from X is illegal", d.submit(wrongX).error)

        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 1)
        val matchingX = wrongX.copy(xValue = 1)
        assertNull(d.submit(matchingX).error)
        d.resolveAll()

        assertEquals("the exiled card goes to its owner's graveyard", listOf("Sol Ring"), d.zoneNames(d.player2, Zone.GRAVEYARD))
    }
}
