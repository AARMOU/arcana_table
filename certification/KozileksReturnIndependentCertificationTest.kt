package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KozileksReturn
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class KozileksReturnIndependentCertificationTest {
    @Test
    fun costsTwoGenericAndRedAndDealsTwoToEachCreatureAtInstantSpeed() {
        val d = customScenario(KozileksReturn, "Savannah Lions", "Grizzly Bears", "Giant Spider", "Hill Giant", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        val spell = d.putCardInHand(p, "Kozilek's Return")
        assertTrue(d.projected.getColors(spell).isEmpty())
        val lions = d.putCreatureOnBattlefield(p, "Savannah Lions")
        val bears = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spider = d.putCreatureOnBattlefield(opponent, "Giant Spider")
        val giant = d.putCreatureOnBattlefield(p, "Hill Giant")
        val ring = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.RED)

        assertNull(d.castSpell(p, spell).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).red)
        d.resolveAll()

        assertEquals(
            mapOf(lions to 2, bears to 2, spider to 2, giant to 2),
            d.events.filterIsInstance<DamageDealtEvent>()
                .filter { it.sourceId == spell }
                .associate { it.targetId to it.amount },
        )
        assertFalse(d.getPermanents(p).contains(lions))
        assertFalse(d.getPermanents(opponent).contains(bears))
        assertTrue(d.getPermanents(opponent).contains(spider))
        assertTrue(d.getPermanents(p).contains(giant))
        assertTrue(d.getPermanents(opponent).contains(ring))
        assertTrue(d.getGraveyardCardNames(p).contains("Kozilek's Return"))
    }

    @Test
    fun threeColorlessManaCannotPayTheRequiredRedMana() {
        val d = customScenario(KozileksReturn, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Kozilek's Return")
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, spell).error)

        assertTrue(d.getHand(p).contains(spell))
        assertEquals(3, d.manaPool(p).colorless)
        assertTrue(d.getPermanents(d.player2).contains(bear))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun castingAnEldraziCreatureWithManaValueAtLeastSevenMayExileReturnAndDealFive() {
        val d = customScenario(KozileksReturn, "Ulamog, the Ceaseless Hunger", "Grizzly Bears", "Colossal Dreadmaw")
        val p = d.player1
        val returnCard = d.putCardInGraveyard(p, "Kozilek's Return")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val dreadmaw = d.putCreatureOnBattlefield(d.player2, "Colossal Dreadmaw")
        val eldrazi = d.putCardInHand(p, "Ulamog, the Ceaseless Hunger")
        d.giveColorlessMana(p, 10)

        assertNull(d.castSpell(p, eldrazi).error)
        assertEquals(2, d.stackSize)
        d.resolveAll(Choices(yes = true))

        assertTrue(d.getExileCardNames(p).contains("Kozilek's Return"))
        assertFalse(d.getGraveyard(p).contains(returnCard))
        assertEquals(
            mapOf(bear to 5, dreadmaw to 5),
            d.events.filterIsInstance<DamageDealtEvent>()
                .filter { it.sourceId == returnCard }
                .associate { it.targetId to it.amount },
        )
        assertFalse(d.getPermanents(p).contains(bear))
        assertTrue(d.getPermanents(d.player2).contains(dreadmaw))
        assertTrue(d.permanentsNamed(p, "Ulamog, the Ceaseless Hunger").isNotEmpty())
    }

    @Test
    fun decliningToExileReturnLeavesItInTheGraveyardAndDealsNoDamage() {
        val d = customScenario(KozileksReturn, "Ulamog, the Ceaseless Hunger", "Grizzly Bears")
        val p = d.player1
        val returnCard = d.putCardInGraveyard(p, "Kozilek's Return")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val eldrazi = d.putCardInHand(p, "Ulamog, the Ceaseless Hunger")
        d.giveColorlessMana(p, 10)

        assertNull(d.castSpell(p, eldrazi).error)
        assertEquals(2, d.stackSize)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.getGraveyard(p).contains(returnCard))
        assertFalse(d.getExileCardNames(p).contains("Kozilek's Return"))
        assertTrue(d.getPermanents(d.player2).contains(bear))
        assertTrue(d.events.filterIsInstance<DamageDealtEvent>().none { it.sourceId == returnCard })
    }

    @Test
    fun aHighManaValueNonEldraziCreatureDoesNotTriggerTheGraveyardAbility() {
        val d = customScenario(KozileksReturn, "Platinum Angel", "Grizzly Bears")
        val p = d.player1
        val returnCard = d.putCardInGraveyard(p, "Kozilek's Return")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val angel = d.putCardInHand(p, "Platinum Angel")
        d.giveColorlessMana(p, 7)

        assertNull(d.castSpell(p, angel).error)
        assertEquals(1, d.stackSize)
        d.resolveAll()

        assertTrue(d.getGraveyard(p).contains(returnCard))
        assertTrue(d.getPermanents(d.player2).contains(bear))
        assertTrue(d.permanentsNamed(p, "Platinum Angel").isNotEmpty())
        assertTrue(d.events.filterIsInstance<DamageDealtEvent>().none { it.sourceId == returnCard })
    }

    @Test
    fun anEldraziCreatureWithManaValueBelowSevenDoesNotTriggerTheGraveyardAbility() {
        val d = customScenario(KozileksReturn, "Eldrazi Skyspawner", "Grizzly Bears")
        val p = d.player1
        val returnCard = d.putCardInGraveyard(p, "Kozilek's Return")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val eldrazi = d.putCardInHand(p, "Eldrazi Skyspawner")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLUE)

        assertNull(d.castSpell(p, eldrazi).error)
        assertEquals(1, d.stackSize)
        d.resolveAll()

        assertTrue(d.getGraveyard(p).contains(returnCard))
        assertTrue(d.getPermanents(d.player2).contains(bear))
        assertTrue(d.permanentsNamed(p, "Eldrazi Skyspawner").isNotEmpty())
        assertTrue(d.events.filterIsInstance<DamageDealtEvent>().none { it.sourceId == returnCard })
    }

    @Test
    fun theGraveyardAbilityDoesNotTriggerWhenReturnIsInHand() {
        val d = customScenario(KozileksReturn, "Ulamog, the Ceaseless Hunger", "Grizzly Bears")
        val p = d.player1
        val returnCard = d.putCardInHand(p, "Kozilek's Return")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val eldrazi = d.putCardInHand(p, "Ulamog, the Ceaseless Hunger")
        d.giveColorlessMana(p, 10)

        assertNull(d.castSpell(p, eldrazi).error)
        assertEquals(1, d.stackSize)
        d.resolveAll()

        assertTrue(d.getHand(p).contains(returnCard))
        assertTrue(d.getPermanents(d.player2).contains(bear))
        assertTrue(d.events.filterIsInstance<DamageDealtEvent>().none { it.sourceId == returnCard })
    }
}
