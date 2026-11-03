package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TrazynTheInfinite
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Trazyn the Infinite (40K #65): Deathtouch; Prismatic Gallery — as long as Trazyn is on the battlefield, it
 * has all activated abilities of all artifact cards in your graveyard. Rulings (2022-10-07) quoted in
 * [TrazynTheInfinite]: only activated abilities, and self-references read as Trazyn (so a "sacrifice this"
 * cost sacrifices Trazyn and a {T} cost taps it). Biotransference makes creature cards in your graveyard
 * artifact cards, so they lend their abilities too.
 */
class TrazynTheInfiniteScenarioTest {

    private val trazynName = "Trazyn the Infinite"

    private fun game() = customScenario(TrazynTheInfinite, "Mind Stone", "Llanowar Elves", "Grizzly Bears")

    private fun GameTestDriver.trazyn(p: EntityId): EntityId =
        putCreatureOnBattlefield(p, trazynName).also { removeSummoningSickness(it) }

    private fun GameTestDriver.abilityId(card: String, index: Int) =
        cardRegistry.getCard(card)!!.script.activatedAbilities[index].id

    @Test
    fun deathtouchLegendaryArtifactCreature() {
        val d = game()
        val t = d.trazyn(d.player1)
        assertTrue(d.projected.hasKeyword(t, Keyword.DEATHTOUCH))
        assertTrue(d.projected.hasType(t, "ARTIFACT") && d.projected.isCreature(t))
        assertEquals(4, d.projected.getPower(t))
        assertEquals(6, d.projected.getToughness(t))
        assertTrue("no artifact card in the graveyard: no abilities", d.abilityActions(d.player1, t).isEmpty())
    }

    @Test
    fun anArtifactCardsManaAbilityTapsTrazyn() {
        val d = game()
        val p = d.player1
        val t = d.trazyn(p)
        d.putCardInGraveyard(p, "Mind Stone")

        assertNull(d.submit(ActivateAbility(p, t, d.abilityId("Mind Stone", 0))).error)
        d.resolveAll()
        assertEquals("Mind Stone's {T}: Add {C}", 1, d.manaPool(p).colorless)
        assertTrue("the {T} cost tapped Trazyn", d.isTapped(t))
        assertTrue("the donor card stays in the graveyard", d.zoneNames(p, Zone.GRAVEYARD).contains("Mind Stone"))
    }

    @Test
    fun aSacrificeThisCostSacrificesTrazyn() {
        val d = game()
        val p = d.player1
        val t = d.trazyn(p)
        val stone = d.putCardInGraveyard(p, "Mind Stone")
        val hand = d.getHandSize(p)
        d.giveColorlessMana(p, 1)

        assertNull(
            d.submit(ActivateAbility(p, t, d.abilityId("Mind Stone", 1), paymentStrategy = PaymentStrategy.FromPool)).error
        )
        d.resolveAll()
        assertEquals("drew a card", hand + 1, d.getHandSize(p))
        assertFalse("Trazyn was sacrificed", t in d.getPermanents(p))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(trazynName))
        assertTrue("Mind Stone itself is untouched", stone in d.getGraveyard(p))
    }

    @Test
    fun theAbilityGoesAwayWhenTheCardLeavesYourGraveyard() {
        val d = game()
        val p = d.player1
        val t = d.trazyn(p)
        val stone = d.putCardInGraveyard(p, "Mind Stone")
        assertTrue(d.abilityActions(p, t).isNotEmpty())

        d.moveToExile(stone)
        assertTrue(d.abilityActions(p, t).isEmpty())
        assertNotNull(d.submit(ActivateAbility(p, t, d.abilityId("Mind Stone", 0))).error)
        assertFalse(d.isTapped(t))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun opponentsGraveyardAndNonArtifactCardsDontCount() {
        val d = game()
        val p = d.player1
        val t = d.trazyn(p)
        d.putCardInGraveyard(d.player2, "Mind Stone")
        d.putCardInGraveyard(p, "Llanowar Elves")

        assertNotNull("opponent's artifact card", d.submit(ActivateAbility(p, t, d.abilityId("Mind Stone", 0))).error)
        assertNotNull("your non-artifact card", d.submit(ActivateAbility(p, t, d.abilityId("Llanowar Elves", 0))).error)
        assertTrue(d.abilityActions(p, t).isEmpty())
        assertFalse(d.isTapped(t))
    }

    @Test
    fun withBiotransferenceACreatureCardInYourGraveyardLendsItsAbilities() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Biotransference")
        val t = d.trazyn(p)
        val elves = d.putCardInGraveyard(p, "Llanowar Elves")
        assertTrue("Biotransference makes it an artifact card", "ARTIFACT" in d.projected.crossZoneGrantedCardTypes(elves))

        assertNull(d.submit(ActivateAbility(p, t, d.abilityId("Llanowar Elves", 0))).error)
        d.resolveAll()
        assertEquals("Llanowar Elves' {T}: Add {G}", 1, d.manaPool(p).green)
        assertTrue(d.isTapped(t))
    }

    @Test
    fun withBiotransferenceTheOpponentsCreatureCardsStillDontCount() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Biotransference")
        val t = d.trazyn(p)
        d.putCardInGraveyard(d.player2, "Llanowar Elves")

        assertNotNull(d.submit(ActivateAbility(p, t, d.abilityId("Llanowar Elves", 0))).error)
        assertFalse(d.isTapped(t))
    }
}
