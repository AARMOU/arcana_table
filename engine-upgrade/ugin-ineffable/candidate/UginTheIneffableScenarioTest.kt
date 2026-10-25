package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UginTheIneffable
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.MayLookAtInExileComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import org.junit.Assert.*
import org.junit.Test

/** These assertions must execute on B131, not be inferred from SDK source similarities. */
class UginTheIneffableScenarioTest {
    @Test fun plusOneExilesOneFaceDownForControllerOnlyAndCreatesLinkedSpirit() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        assertEquals(2, d.cardRegistry.getCard("Ugin, the Ineffable")!!.script.activatedAbilities.size)
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()

        val spirits = d.permanentsNamed(p, "Spirit")
        assertEquals(1, spirits.size)
        val cardInExile = d.getExile(p).single()
        assertNotNull("The exiled card must really be face down",
            d.state.getEntity(cardInExile)!!.get<FaceDownComponent>())
        val viewers = d.state.getEntity(cardInExile)!!
            .get<MayLookAtInExileComponent>()?.playerIds.orEmpty()
        assertTrue("Ugin's controller may look at the private card", p in viewers)
        assertFalse("Opponent must not learn a face-down exile card", opponent in viewers)
        val links = d.state.getEntity(spirits.single())!!
            .get<LinkedExileComponent>()?.exiledIds.orEmpty()
        assertEquals("Link must be on the Spirit, not on Ugin", listOf(cardInExile), links)
        assertNull(d.state.getEntity(ugin)!!.get<LinkedExileComponent>())
    }

    @Test fun linkedSpiritReturnsItsOwnExiledCardAfterUginLeaves() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val p = d.player1
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        val spirit = d.permanentsNamed(p, "Spirit").single()
        val linkedCard = d.getExile(p).single()
        val handBefore = d.getHand(p).size

        // Deliberately remove the *source* independently. The Spirit's own leaves
        // trigger must retain the linked exile after Ugin has disappeared.
        d.moveToGraveyard(ugin)
        assertNull(d.findPermanent(p, "Ugin, the Ineffable"))
        assertEquals(listOf(linkedCard), d.state.getEntity(spirit)!!
            .get<LinkedExileComponent>()?.exiledIds)

        val removal = com.wingedsheep.sdk.dsl.card("Ugin Spirit Leave Probe") {
            manaCost = "{0}"
            typeLine = "Instant"
            spell {
                effect = com.wingedsheep.sdk.dsl.Effects.Destroy(
                    target(com.wingedsheep.sdk.scripting.filters.unified.TargetFilter(
                        com.wingedsheep.sdk.scripting.GameObjectFilter.Creature
                    ))
                )
            }
        }
        d.registerCard(removal)
        val spell = d.putCardInHand(p, removal.name)
        assertNull(d.castSpell(p, spell, listOf(spirit)).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Spirit").isEmpty())
        assertFalse(linkedCard in d.getExile(p))
        assertTrue("Correct linked card must be returned to hand", linkedCard in d.getHand(p))
        assertEquals(handBefore + 1, d.getHand(p).size)
    }

    @Test fun colorlessSpellDiscountDoesNotPayColoredMana() {
        val d = customScenario(UginTheIneffable, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull("One-generic-mana Sol Ring should cost zero", d.castSpell(p, ring).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
        val bear = d.putCardInHand(p, "Grizzly Bears")
        assertNotNull("Discount must not invent green mana", d.castSpell(p, bear).error)
    }

    @Test fun plusOneWithEmptyLibraryStillCreatesSpiritWithoutFakeLinkedCard() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        // Remove the library as a setup operation (not through game actions).
        d.state.getLibrary(p).toList().forEach(d::moveToGraveyard)
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Spirit").size)
        assertTrue(d.getExile(p).isEmpty())
        assertTrue(d.state.getEntity(d.permanentsNamed(p, "Spirit").single())!!
            .get<LinkedExileComponent>()?.exiledIds.orEmpty().isEmpty())
    }

    @Test fun minusThreeRejectsColorlessTargetsThenDestroysAColoredPermanent() {
        val d = customScenario(UginTheIneffable, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val minusThree = d.cardRegistry.requireCard("Ugin, the Ineffable")
            .script.activatedAbilities[1].id

        // Target legality must reject a colorless permanent without spending loyalty.
        val invalid = d.submit(ActivateAbility(
            p, ugin, minusThree, targets = listOf(ChosenTarget.Permanent(ring))
        ))
        assertNotNull("Ugin's -3 cannot target colorless Sol Ring", invalid.error)
        assertTrue(ring in d.state.getBattlefield())
        assertTrue(d.state.stack.isEmpty())

        // A colored permanent is a legal target for the same loyalty ability.
        assertNull(d.submit(ActivateAbility(
            p, ugin, minusThree, targets = listOf(ChosenTarget.Permanent(bear))
        )).error)
        d.resolveAll()
        assertTrue("The colored creature must be destroyed",
            bear in d.state.getZone(d.player2, Zone.GRAVEYARD))
        assertTrue("The colorless artifact must remain untouched",
            ring in d.state.getBattlefield())
    }
}
