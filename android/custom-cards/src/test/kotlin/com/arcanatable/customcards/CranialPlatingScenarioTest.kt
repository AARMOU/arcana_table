package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CranialPlating
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class CranialPlatingScenarioTest {
    @Test
    fun equipAndBlackAttachmentAbilityScalePowerByArtifactsYouControl() {
        val d = customScenario(CranialPlating, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val firstBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val secondBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(p, "Cranial Plating")
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val abilities = d.cardRegistry.requireCard("Cranial Plating").script.activatedAbilities

        d.giveColorlessMana(p, 1)
        assertNull(
            d.submit(
                ActivateAbility(
                    p,
                    plating,
                    abilities[1].id,
                    targets = listOf(ChosenTarget.Permanent(firstBear)),
                ),
            ).error,
        )
        d.resolveAll()
        assertEquals(4, d.projected.getPower(firstBear))

        d.giveMana(p, Color.BLACK, 2)
        assertNull(
            d.submit(
                ActivateAbility(
                    p,
                    plating,
                    abilities[0].id,
                    targets = listOf(ChosenTarget.Permanent(secondBear)),
                ),
            ).error,
        )
        d.resolveAll()

        assertEquals(2, d.projected.getPower(firstBear))
        assertEquals(4, d.projected.getPower(secondBear))
        d.putPermanentOnBattlefield(p, "Sol Ring")
        assertEquals(5, d.projected.getPower(secondBear))
    }
}
