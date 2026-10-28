package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MorophonTheBoundless
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MorophonTheBoundlessScenarioTest {
    private fun setup() = customScenario(MorophonTheBoundless, "Grizzly Bears", "Craw Wurm", "Sol Ring")

    /** Casts Morophon for {7} and names [type] as it enters. */
    private fun GameTestDriver.castMorophon(type: String): EntityId {
        val card = putCardInHand(player1, "Morophon, the Boundless")
        giveColorlessMana(player1, 7)
        assertNull(castSpell(player1, card).error)
        repeat(20) {
            val decision = state.pendingDecision
            when {
                decision is ChooseOptionDecision -> {
                    val index = decision.options.indexOfFirst { it.equals(type, ignoreCase = true) }
                    assertTrue("creature type $type offered", index >= 0)
                    assertNull(submitDecision(decision.playerId, OptionChosenResponse(decision.id, index)).error)
                }
                decision != null -> decide()
                state.stack.isNotEmpty() -> bothPass()
                else -> return permanentsNamed(player1, "Morophon, the Boundless").single()
            }
        }
        error("Morophon did not resolve")
    }

    @Test fun chosenTypeSpellsLoseTheirColoredPipsOnly() {
        val d = setup()
        d.castMorophon("Bear")
        val bears = d.putCardInHand(d.player1, "Grizzly Bears") // {1}{G}
        d.giveColorlessMana(d.player1, 1)
        assertNull("{1}{G} Bear costs only {1}", d.castSpell(d.player1, bears).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(d.player1, "Grizzly Bears").size)
    }

    @Test fun genericIsNotReducedAndOtherTypesPayFull() {
        val d = setup()
        d.castMorophon("Bear")
        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        assertNotNull("the {1} is still owed", d.castSpell(d.player1, bears).error)
        val wurm = d.putCardInHand(d.player1, "Craw Wurm") // {4}{G}{G}
        d.giveColorlessMana(d.player1, 4)
        assertNotNull("a Wurm is not the chosen type", d.castSpell(d.player1, wurm).error)
    }

    @Test fun otherCreaturesOfTheChosenTypeYouControlGetPlusOne() {
        val d = setup()
        val morophon = d.castMorophon("Bear")
        val mine = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val wurm = d.putCreatureOnBattlefield(d.player1, "Craw Wurm")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertEquals(3, d.projected.getPower(mine)); assertEquals(3, d.projected.getToughness(mine))
        assertEquals("not a Bear", 6, d.projected.getPower(wurm))
        assertEquals("opponent's Bear", 2, d.projected.getPower(theirs))
        assertEquals("Morophon is every type but the anthem says other", 6, d.projected.getPower(morophon))
        assertTrue("changeling", d.projected.hasSubtype(morophon, "Bear"))
    }

    @Test fun noReductionForColoredNonTypeSpell() {
        val d = setup()
        d.castMorophon("Wurm")
        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 1)
        assertNotNull(d.castSpell(d.player1, bears).error)
        d.giveMana(d.player1, Color.GREEN)
        assertNull(d.castSpell(d.player1, bears).error)
    }
}
