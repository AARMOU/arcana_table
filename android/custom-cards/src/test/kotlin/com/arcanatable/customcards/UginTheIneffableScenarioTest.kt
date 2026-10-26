package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UginTheIneffable
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.MayLookAtInExileComponent
import com.wingedsheep.sdk.core.Zone
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

    @Test fun multipleSpiritsKeepIndependentFaceDownLinksAcrossTurns() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val p = d.player1
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")

        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        val firstSpirit = d.permanentsNamed(p, "Spirit").single()
        val firstExile = d.getExile(p).single()

        // Loyalty abilities are limited to once per turn. Advance through the
        // opponent's turn before creating a second, independently linked Spirit.
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.END, activePlayer = p)
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, activePlayer = p)
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()

        val spirits = d.permanentsNamed(p, "Spirit")
        assertEquals(2, spirits.size)
        val secondSpirit = spirits.single { it != firstSpirit }
        val secondExile = d.getExile(p).single { it != firstExile }
        assertEquals(listOf(firstExile), d.state.getEntity(firstSpirit)!!
            .get<LinkedExileComponent>()?.exiledIds)
        assertEquals(listOf(secondExile), d.state.getEntity(secondSpirit)!!
            .get<LinkedExileComponent>()?.exiledIds)

        // Destroy only the first Spirit via a real engine spell. The other
        // token and its private exiled card must remain unaffected.
        val removal = com.wingedsheep.sdk.dsl.card("Ugin First Spirit Removal Probe") {
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
        assertNull(d.castSpell(p, spell, listOf(firstSpirit)).error)
        d.resolveAll()

        assertEquals(listOf(secondSpirit), d.permanentsNamed(p, "Spirit"))
        assertTrue(firstExile in d.getHand(p))
        assertFalse(firstExile in d.getExile(p))
        assertTrue(secondExile in d.getExile(p))
        assertFalse(secondExile in d.getHand(p))
        assertEquals(listOf(secondExile), d.state.getEntity(secondSpirit)!!
            .get<LinkedExileComponent>()?.exiledIds)

        // Resolve a separate leave event for the surviving token. Returning
        // the first card must not consume or overwrite the second token's link.
        val secondRemoval = d.putCardInHand(p, removal.name)
        assertNull(d.castSpell(p, secondRemoval, listOf(secondSpirit)).error)
        d.resolveAll()

        assertTrue("Both Spirit tokens must have left", d.permanentsNamed(p, "Spirit").isEmpty())
        assertTrue("Each face-down exile must have been consumed once", d.getExile(p).isEmpty())
        assertEquals("First token must return only its card once",
            1, d.getHand(p).count { it == firstExile })
        assertEquals("Second token must independently return its card once",
            1, d.getHand(p).count { it == secondExile })
    }



    @Test fun uginDiscountNeverAppliesToOpponentsColorlessSpells() {
        val probe = com.wingedsheep.sdk.dsl.card("Opponent Three Generic Cost Probe") {
            manaCost = "{3}"
            typeLine = "Artifact"
        }
        val d = customScenario(UginTheIneffable)
        d.registerCard(probe)
        d.putPermanentOnBattlefield(d.player1, "Ugin, the Ineffable")
        // An opponent receives priority during their own main phase. Ugin's
        // static reduction is controller-specific even for colorless spells.
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val opponentArtifact = d.putCardInHand(d.player2, probe.name)
        d.giveColorlessMana(d.player2, 1)
        assertNotNull("Opposing {3} artifact cannot use our Ugin's {2} discount",
            d.castSpell(d.player2, opponentArtifact).error)
        assertTrue(opponentArtifact in d.getHand(d.player2))
        assertNull(d.findPermanent(d.player2, probe.name))
        d.giveColorlessMana(d.player2, 2)
        assertNull("Opposing artifact still casts for its full {3}",
            d.castSpell(d.player2, opponentArtifact).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player2, probe.name))
    }

    @Test fun spiritWithNoExiledCardLeavesWithoutCreatingOrReturningOne() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        d.state.getLibrary(p).toList().forEach(d::moveToGraveyard)
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        val spirit = d.permanentsNamed(p, "Spirit").single()
        val handBefore = d.getHand(p).toList()
        val graveyardBefore = d.getGraveyardCardNames(p).toList()

        val removal = com.wingedsheep.sdk.dsl.card("Empty Link Spirit Removal Probe") {
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
        assertTrue("No linked exile must remain empty", d.getExile(p).isEmpty())
        assertEquals("Empty link must not invent a card in hand", handBefore, d.getHand(p))
        assertTrue("Original library cards must stay in graveyard",
            d.getGraveyardCardNames(p).containsAll(graveyardBefore))
    }

    @Test fun spiritExiledByRealSpellReturnsItsLinkedCardExactlyOnce() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val owner = d.player1
        val ugin = d.putPermanentOnBattlefield(owner, "Ugin, the Ineffable")
        assertNull(d.activate(owner, ugin, index = 0).error)
        d.resolveAll()
        val spirit = d.permanentsNamed(owner, "Spirit").single()
        val linkedCard = d.getExile(owner).single()
        val handBefore = d.getHand(owner).size

        // Exiling a token is a genuine leave-the-battlefield event, not a death.
        // The linked card must return even though the token cannot exist in exile.
        val exileSpell = com.wingedsheep.sdk.dsl.card("Ugin Spirit Exile Probe") {
            manaCost = "{0}"
            typeLine = "Instant"
            spell {
                effect = com.wingedsheep.sdk.dsl.Effects.Move(
                    target(com.wingedsheep.sdk.scripting.filters.unified.TargetFilter(
                        com.wingedsheep.sdk.scripting.GameObjectFilter.Creature
                    )),
                    Zone.EXILE
                )
            }
        }
        d.registerCard(exileSpell)
        val spell = d.putCardInHand(owner, exileSpell.name)
        assertNull(d.castSpell(owner, spell, listOf(spirit)).error)
        d.resolveAll()

        assertTrue("Spirit token must leave the battlefield", d.permanentsNamed(owner, "Spirit").isEmpty())
        assertFalse("Linked card must no longer be exiled", linkedCard in d.getExile(owner))
        assertEquals("Linked card must return to its owner's hand once",
            1, d.getHand(owner).count { it == linkedCard })
        assertEquals(handBefore + 1, d.getHand(owner).size)
    }

    @Test fun opponentRemovalReturnsLinkedCardToOriginalOwner() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val owner = d.player1
        val opponent = d.player2
        val ugin = d.putPermanentOnBattlefield(owner, "Ugin, the Ineffable")
        assertNull(d.activate(owner, ugin, index = 0).error)
        d.resolveAll()
        val spirit = d.permanentsNamed(owner, "Spirit").single()
        val linkedCard = d.getExile(owner).single()
        val ownerHandBefore = d.getHand(owner).size

        // The token's controller and the player removing it are different.
        // Ugin's "your hand" means the exiled card's owner, never the remover.
        val removal = com.wingedsheep.sdk.dsl.card("Opposing Ugin Spirit Removal Probe") {
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
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, activePlayer = opponent)
        // Advancing to the opponent's turn includes their normal draw. Snapshot
        // *after* that turn transition so the assertion isolates spell
        // consumption rather than mistakenly treating the draw as a leak.
        val opponentHandBefore = d.getHand(opponent).size
        val spell = d.putCardInHand(opponent, removal.name)
        assertNull("Opponent must be able to destroy the Spirit",
            d.castSpell(opponent, spell, listOf(spirit)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(owner, "Spirit").isEmpty())
        assertFalse(linkedCard in d.getExile(owner))
        assertEquals("The original owner receives exactly one linked card",
            1, d.getHand(owner).count { it == linkedCard })
        assertEquals(ownerHandBefore + 1, d.getHand(owner).size)
        assertFalse("The remover must never receive the private exiled card",
            linkedCard in d.getHand(opponent))
        assertEquals("Opposing removal consumes its own spell, not the linked card",
            opponentHandBefore, d.getHand(opponent).size)
    }

    @Test fun spiritBouncedByRealSpellReturnsLinkedCardToHand() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val owner = d.player1
        val ugin = d.putPermanentOnBattlefield(owner, "Ugin, the Ineffable")
        assertNull(d.activate(owner, ugin, index = 0).error)
        d.resolveAll()
        val spirit = d.permanentsNamed(owner, "Spirit").single()
        val linkedCard = d.getExile(owner).single()
        val handBefore = d.getHand(owner).size

        // Returning a token to hand is a leave-the-battlefield event, even
        // though the token itself must cease to exist outside the battlefield.
        // Its own exiled card must return once, independent of Ugin's presence.
        d.moveToGraveyard(ugin)
        val bounce = com.wingedsheep.sdk.dsl.card("Ugin Spirit Bounce Probe") {
            manaCost = "{0}"
            typeLine = "Instant"
            spell {
                effect = com.wingedsheep.sdk.dsl.Effects.Move(
                    target(com.wingedsheep.sdk.scripting.filters.unified.TargetFilter(
                        com.wingedsheep.sdk.scripting.GameObjectFilter.Creature
                    )),
                    Zone.HAND
                )
            }
        }
        d.registerCard(bounce)
        val spell = d.putCardInHand(owner, bounce.name)
        assertNull("A real bounce spell must resolve on the Spirit",
            d.castSpell(owner, spell, listOf(spirit)).error)
        d.resolveAll()

        assertTrue("Bounced Spirit token must leave the battlefield",
            d.permanentsNamed(owner, "Spirit").isEmpty())
        assertFalse("Linked card must leave face-down exile", linkedCard in d.getExile(owner))
        assertEquals("Linked card must return exactly once", 1,
            d.getHand(owner).count { it == linkedCard })
        assertEquals("Token must not become an extra card in hand",
            handBefore + 1, d.getHand(owner).size)
        assertFalse("A bounced token must not exist as a hand card",
            spirit in d.getHand(owner))
    }

    private fun GameTestDriver.loyalty(id: com.wingedsheep.sdk.model.EntityId) =
        state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.battlefield.CountersComponent>()
            ?.getCount(com.wingedsheep.sdk.core.CounterType.LOYALTY) ?: 0

    /** Casts Ugin from hand for its full {6} so it enters with its printed loyalty. */
    private fun GameTestDriver.castUgin(): com.wingedsheep.sdk.model.EntityId {
        val card = putCardInHand(player1, "Ugin, the Ineffable")
        giveColorlessMana(player1, 6)
        assertNull(castSpell(player1, card).error)
        resolveAll()
        val ugin = permanentsNamed(player1, "Ugin, the Ineffable").single()
        assertEquals("enters with printed loyalty 4", 4, loyalty(ugin))
        return ugin
    }

    @Test fun minusThreeDestroysAColoredPermanent() {
        val d = customScenario(UginTheIneffable, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val ugin = d.castUgin()
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Ugin, the Ineffable")!!.script.activatedAbilities[1]
        assertNull(d.submit(com.wingedsheep.engine.core.ActivateAbility(p, ugin, ability.id,
            targets = listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent(bear)))).error)
        d.resolveAll()
        assertEquals(1, d.loyalty(ugin))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertTrue(d.getGraveyardCardNames(d.player2).contains("Grizzly Bears"))
    }

    @Test fun minusThreeCannotTargetAColorlessPermanent() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val p = d.player1
        val ugin = d.castUgin()
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val ability = d.cardRegistry.getCard("Ugin, the Ineffable")!!.script.activatedAbilities[1]
        assertNotNull(d.submit(com.wingedsheep.engine.core.ActivateAbility(p, ugin, ability.id,
            targets = listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent(ring)))).error)
        assertEquals(1, d.permanentsNamed(d.player2, "Sol Ring").size)
    }

    @Test fun plusOneCannotBeActivatedTwiceInOneTurn() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val p = d.player1
        val ugin = d.castUgin()
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        assertEquals(5, d.loyalty(ugin))
        assertEquals(1, d.permanentsNamed(p, "Spirit").size)
        assertEquals(1, d.getExile(p).size)

        // The second attempt is illegal even though +1 would increase loyalty.
        assertNotNull("loyalty abilities may be activated only once per turn",
            d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        assertEquals("failed activation must not add loyalty", 5, d.loyalty(ugin))
        assertEquals("failed activation must not create a second Spirit", 1,
            d.permanentsNamed(p, "Spirit").size)
        assertEquals("failed activation must not exile another card", 1, d.getExile(p).size)
    }

    @Test fun minusThreeCannotFollowPlusOneInSameTurn() {
        val d = customScenario(UginTheIneffable, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val ugin = d.castUgin()
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        assertEquals(5, d.loyalty(ugin))

        // A valid colored target and sufficient loyalty must not override the
        // one-loyalty-activation-per-turn restriction.
        val ability = d.cardRegistry.getCard("Ugin, the Ineffable")!!.script.activatedAbilities[1]
        val second = d.submit(com.wingedsheep.engine.core.ActivateAbility(
            p, ugin, ability.id,
            targets = listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent(bear))
        ))
        assertNotNull("cannot use -3 after +1 in the same turn", second.error)
        d.resolveAll()
        assertEquals(5, d.loyalty(ugin))
        assertEquals(1, d.permanentsNamed(d.player2, "Grizzly Bears").size)
    }

    @Test fun colorlessThreeGenericSpellCostsExactlyOneWithUgin() {
        val probe = com.wingedsheep.sdk.dsl.card("Ugin Three Generic Cost Probe") {
            manaCost = "{3}"
            typeLine = "Artifact"
        }
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        d.registerCard(probe)
        d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val artifact = d.putCardInHand(p, probe.name)
        d.giveColorlessMana(p, 1)
        assertNull("Ugin must reduce {3} by exactly {2} to {1}",
            d.castSpell(p, artifact).error)
        d.resolveAll()
        assertNotNull("The discounted artifact must resolve", d.findPermanent(p, probe.name))
    }

    @Test fun uginDoesNotReduceFourGenericSpellToOne() {
        val probe = com.wingedsheep.sdk.dsl.card("Ugin Four Generic Cost Probe") {
            manaCost = "{4}"
            typeLine = "Artifact"
        }
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        d.registerCard(probe)
        d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val artifact = d.putCardInHand(p, probe.name)
        d.giveColorlessMana(p, 1)
        assertNotNull("Ugin reduces {4} to {2}, not {1}",
            d.castSpell(p, artifact).error)
        assertTrue("Rejected spell must remain in hand", artifact in d.getHand(p))
        assertNull(d.findPermanent(p, probe.name))
    }

    @Test fun uginDoesNotDiscountColoredSpellsGenericComponent() {
        val probe = com.wingedsheep.sdk.dsl.card("Ugin Colored Cost Probe") {
            manaCost = "{2}{G}"
            colorIdentity = "G"
            typeLine = "Creature — Bear"
            power = 2
            toughness = 2
        }
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        d.registerCard(probe)
        d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val bear = d.putCardInHand(p, probe.name)
        d.giveMana(p, com.wingedsheep.sdk.core.Color.GREEN, 1)
        assertNotNull("Colored {2}{G} still needs two generic mana",
            d.castSpell(p, bear).error)
        assertTrue(bear in d.getHand(p))
        d.giveColorlessMana(p, 2)
        assertNull("Full {2}{G} payment must cast normally", d.castSpell(p, bear).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, probe.name))
    }


    @Test fun stolenSpiritKeepsPrivateLinkAndReturnsCardToOriginalOwner() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val owner = d.player1
        val thief = d.player2
        val ugin = d.putPermanentOnBattlefield(owner, "Ugin, the Ineffable")
        assertNull(d.activate(owner, ugin, index = 0).error)
        d.resolveAll()
        val spirit = d.permanentsNamed(owner, "Spirit").single()
        val linkedCard = d.getExile(owner).single()
        val ownerHandBefore = d.getHand(owner).size

        val theft = com.wingedsheep.sdk.dsl.card("Ugin Spirit Control Change Probe") {
            manaCost = "{0}"
            typeLine = "Instant"
            spell {
                effect = com.wingedsheep.sdk.dsl.Effects.GainControl(
                    target(com.wingedsheep.sdk.scripting.filters.unified.TargetFilter(
                        com.wingedsheep.sdk.scripting.GameObjectFilter.Creature
                    )),
                    com.wingedsheep.sdk.scripting.Duration.Permanent
                )
            }
        }
        d.registerCard(theft)
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, activePlayer = thief)
        val theftSpell = d.putCardInHand(thief, theft.name)
        assertNull("Opponent must be able to steal the Spirit through a real spell",
            d.castSpell(thief, theftSpell, listOf(spirit)).error)
        d.resolveAll()

        // GainControl is a Layer.CONTROL floating effect. Battlefield ZoneKey
        // remains keyed to the original zone holder, so permanentsNamed(player)
        // is NOT a valid control-change assertion. Inspect projected control.
        assertEquals("The real engine must transfer Spirit control to the thief",
            thief, d.projected.getController(spirit))
        assertEquals("The stolen token must retain its original identity",
            "Spirit", d.getCardName(spirit))
        assertEquals("Control change cannot rewrite the per-token exile link",
            listOf(linkedCard), d.state.getEntity(spirit)!!
                .get<LinkedExileComponent>()?.exiledIds)
        assertTrue("The linked card stays in its owner's exile until the token leaves",
            linkedCard in d.getExile(owner))
        val privateViewers = d.state.getEntity(linkedCard)!!
            .get<MayLookAtInExileComponent>()?.playerIds.orEmpty()
        assertTrue("Original viewer retains permission after Spirit is stolen",
            owner in privateViewers)
        assertFalse("New controller must not gain permission to see face-down exile",
            thief in privateViewers)

        val removal = com.wingedsheep.sdk.dsl.card("Stolen Ugin Spirit Removal Probe") {
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
        val removalSpell = d.putCardInHand(thief, removal.name)
        assertNull(d.castSpell(thief, removalSpell, listOf(spirit)).error)
        d.resolveAll()

        assertFalse("Stolen Spirit must leave the battlefield after real removal",
            spirit in d.state.getZone(owner, Zone.BATTLEFIELD))
        assertFalse(linkedCard in d.getExile(owner))
        assertEquals("Stolen Spirit must return the linked card to its actual owner once",
            1, d.getHand(owner).count { it == linkedCard })
        assertEquals(ownerHandBefore + 1, d.getHand(owner).size)
        assertFalse("Thief must not receive the original owner's linked card",
            linkedCard in d.getHand(thief))
    }

    @Test fun uginDiscountCannotPayRequiredColorlessSymbol() {
        val probe = com.wingedsheep.sdk.dsl.card("Ugin Explicit Colorless Cost Probe") {
            manaCost = "{2}{C}"
            typeLine = "Artifact"
        }
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        d.registerCard(probe)
        d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val artifact = d.putCardInHand(p, probe.name)

        // The generic {2} is reduced to zero, but the printed {C} is not.
        assertNotNull("Ugin must not erase an explicit {C} requirement",
            d.castSpell(p, artifact).error)
        assertTrue("A rejected cast must leave the card in hand", artifact in d.getHand(p))
        assertNull(d.findPermanent(p, probe.name))

        d.giveColorlessMana(p, 1)
        assertNull("Exactly one real colorless mana must pay the remaining {C}",
            d.castSpell(p, artifact).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, probe.name))
    }

    @Test fun uginGenericDiscountStopsAtZero() {
        val probe = com.wingedsheep.sdk.dsl.card("Ugin One Generic Floor Probe") {
            manaCost = "{1}"
            typeLine = "Artifact"
        }
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        d.registerCard(probe)
        d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val artifact = d.putCardInHand(p, probe.name)

        // A {2} generic reduction on a {1} spell cannot create negative cost.
        assertNull("Ugin should make a one-generic colorless spell free",
            d.castSpell(p, artifact).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, probe.name))
    }
}
