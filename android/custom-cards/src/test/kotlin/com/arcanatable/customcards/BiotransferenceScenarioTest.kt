package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Biotransference
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Biotransference (40K #30): your creatures, creature spells and creature cards are artifacts; whenever
 * you cast an artifact spell, lose 1 life and create a 2/2 black Necron Warrior artifact creature token.
 * Ruling quoted in [Biotransference]. The Imotekh the Stormlord interaction is in its own test.
 */
class BiotransferenceScenarioTest {

    private fun game() = customScenario(Biotransference, "Grizzly Bears", "Sol Ring", "Divination", "Buried Ruin")

    private fun GameTestDriver.isArtifact(id: EntityId) = projected.hasType(id, "ARTIFACT")

    private fun GameTestDriver.isArtifactCard(id: EntityId) = "ARTIFACT" in projected.crossZoneGrantedCardTypes(id)

    private fun GameTestDriver.warriors(player: EntityId) = permanentsNamed(player, "Necron Warrior")

    @Test
    fun yourCreaturesAndCreatureCardsAreArtifactsButNotOtherCardsOrTheOpponents() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Biotransference")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val theirBears = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val inHand = d.putCardInHand(p, "Grizzly Bears")
        val inGraveyard = d.putCardInGraveyard(p, "Grizzly Bears")
        val inExile = d.putCardInExile(p, "Grizzly Bears")
        val sorcery = d.putCardInHand(p, "Divination")
        val theirCard = d.putCardInGraveyard(d.player2, "Grizzly Bears")

        assertTrue(d.isArtifact(bears))
        assertTrue("still a creature", d.projected.isCreature(bears))
        assertFalse("opponent's creature", d.isArtifact(theirBears))
        assertTrue("creature card in hand", d.isArtifactCard(inHand))
        assertTrue("creature card in graveyard", d.isArtifactCard(inGraveyard))
        assertTrue("creature card in exile", d.isArtifactCard(inExile))
        assertFalse("noncreature card", d.isArtifactCard(sorcery))
        assertFalse("opponent's creature card", d.isArtifactCard(theirCard))
    }

    @Test
    fun aCreatureCardInYourGraveyardIsAnArtifactCardForBuriedRuin() {
        fun ruin(withBiotransference: Boolean): Pair<GameTestDriver, EntityId> {
            val d = game()
            val p = d.player1
            if (withBiotransference) d.putPermanentOnBattlefield(p, "Biotransference")
            val corpse = d.putCardInGraveyard(p, "Grizzly Bears")
            val ruin = d.putLandOnBattlefield(p, "Buried Ruin")
            d.giveColorlessMana(p, 2)
            val ability = d.cardRegistry.getCard("Buried Ruin")!!.script.activatedAbilities[1].id
            val result = d.submit(
                ActivateAbility(
                    p, ruin, ability,
                    targets = listOf(ChosenTarget.Card(corpse, p, Zone.GRAVEYARD)),
                    paymentStrategy = PaymentStrategy.FromPool,
                )
            )
            if (result.error == null) d.resolveAll()
            return d to corpse
        }

        val (withBio, corpse) = ruin(withBiotransference = true)
        assertTrue("returned as an artifact card", corpse in withBio.getHand(withBio.player1))

        val (without, plain) = ruin(withBiotransference = false)
        assertFalse("not an artifact card without Biotransference", plain in without.getHand(without.player1))
    }

    @Test
    fun castingACreatureSpellTriggersBecauseItIsAnArtifactSpell() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Biotransference")
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN, 2)
        assertNull(d.castSpell(p, bears).error)
        assertTrue("the creature spell on the stack is an artifact", "ARTIFACT" in d.projected.crossZoneGrantedCardTypes(bears))
        d.resolveAll()

        assertEquals(19, d.getLifeTotal(p))
        val token = d.warriors(p).single()
        assertTrue(d.isArtifact(token))
        assertTrue(d.projected.isCreature(token))
        assertEquals(2, d.projected.getPower(token))
        assertEquals(2, d.projected.getToughness(token))
        assertTrue(d.projected.hasSubtype(token, "Necron"))
        assertTrue(d.projected.hasSubtype(token, "Warrior"))
        assertEquals(setOf("BLACK"), d.projected.getColors(token))
        assertTrue("the creature itself resolved as an artifact", d.isArtifact(d.permanentsNamed(p, "Grizzly Bears").single()))
    }

    @Test
    fun castingARealArtifactSpellTriggers() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Biotransference")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()

        assertEquals(19, d.getLifeTotal(p))
        assertEquals(1, d.warriors(p).size)
    }

    @Test
    fun castingANoncreatureNonartifactSpellDoesNotTrigger() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Biotransference")
        val divination = d.putCardInHand(p, "Divination")
        d.giveMana(p, Color.BLUE, 3)
        assertNull(d.castSpell(p, divination).error)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.warriors(p).isEmpty())
    }

    @Test
    fun withoutBiotransferenceCreaturesAreNotArtifacts() {
        val d = game()
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val card = d.putCardInGraveyard(p, "Grizzly Bears")
        assertFalse(d.isArtifact(bears))
        assertFalse(d.isArtifactCard(card))
    }
}
