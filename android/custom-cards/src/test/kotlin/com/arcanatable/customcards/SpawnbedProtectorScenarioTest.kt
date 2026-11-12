package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SpawnbedProtector
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

/**
 * M3C #36, real engine scenarios. Intentionally staged without registration/assets
 * until the exact SDK behavior of optional graveyard targets is demonstrated by CI.
 */
class SpawnbedProtectorScenarioTest {
    @Test
    fun endStepWithoutGraveyardTargetStillCreatesTwoSacrificeForColorlessScions() {
        val d = customScenario(SpawnbedProtector)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Spawnbed Protector")

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll(Choices(targets = emptyList()))

        val scions = d.permanentsNamed(p, "Eldrazi Scion")
        assertEquals(2, scions.size)
        scions.forEach {
            assertEquals(1, d.projected.getPower(it))
            assertEquals(1, d.projected.getToughness(it))
        }
        val manaAction = d.abilityActions(p, scions.first()).first().action as ActivateAbility
        assertNull(d.submit(manaAction).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Eldrazi Scion").size)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun returnsOneChosenOwnEldraziCreatureCardAndCreatesTwoScions() {
        val d = customScenario(SpawnbedProtector, "Bane of Bala Ged")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Spawnbed Protector")
        val target = d.putCardInGraveyard(p, "Bane of Bala Ged")
        d.putCardInGraveyard(p, "Bane of Bala Ged")

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll(Choices(targets = listOf(target)))

        assertEquals(1, d.zoneNames(p, Zone.HAND).count { it == "Bane of Bala Ged" })
        assertEquals(1, d.zoneNames(p, Zone.GRAVEYARD).count { it == "Bane of Bala Ged" })
        assertEquals(2, d.permanentsNamed(p, "Eldrazi Scion").size)
    }

    @Test
    fun cannotReturnNonEldraziCreatureOrEldraziNoncreatureCard() {
        val d = customScenario(SpawnbedProtector, "Grizzly Bears", "Eldrazi Conscription")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Spawnbed Protector")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Eldrazi Conscription")

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()

        assertFalse(d.zoneNames(p, Zone.HAND).contains("Grizzly Bears"))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Eldrazi Conscription"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Eldrazi Conscription"))
        assertEquals(2, d.permanentsNamed(p, "Eldrazi Scion").size)
    }

    @Test
    fun cannotReturnOpponentsEldraziFromOpponentsGraveyard() {
        val d = customScenario(SpawnbedProtector, "Bane of Bala Ged")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Spawnbed Protector")
        d.putCardInGraveyard(opponent, "Bane of Bala Ged")

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()

        assertFalse(d.zoneNames(p, Zone.HAND).contains("Bane of Bala Ged"))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Bane of Bala Ged"))
        assertEquals(2, d.permanentsNamed(p, "Eldrazi Scion").size)
        assertEquals(0, d.permanentsNamed(opponent, "Eldrazi Scion").size)
    }
}
