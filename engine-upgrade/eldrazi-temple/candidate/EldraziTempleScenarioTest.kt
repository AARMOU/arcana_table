package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziTemple
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

/**
 * These scenarios must run against fresh, locally built SDK/rules jars with the
 * exact #158 mana restriction. Merely compiling the card is insufficient.
 */
class EldraziTempleScenarioTest {
    @Test fun bothModesExistAndUnrestrictedModePaysForOrdinaryArtifact() {
        val d = customScenario(EldraziTemple, "Sol Ring")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val abilities = d.cardRegistry.getCard("Eldrazi Temple")!!.script.activatedAbilities
        assertEquals(2, abilities.size)
        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        val solRing = d.putCardInHand(p, "Sol Ring")
        assertNull(d.castSpell(p, solRing).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
    }

    @Test fun restrictedModeYieldsExactlyTwoAndCannotTapAgain() {
        val d = customScenario(EldraziTemple)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        val pool = d.manaPool(p)
        assertEquals(0, pool.colorless)
        assertEquals(2, pool.restrictedMana.size)
        assertTrue(d.isTapped(land))
        assertNotNull("One land cannot activate both tap modes in a turn", d.activate(p, land, index = 0).error)
    }

    @Test fun restrictedManaPaysForColorlessEldraziCreature() {
        val d = customScenario(EldraziTemple, "Ulamog's Crusher")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val crusher = d.putCardInHand(p, "Ulamog's Crusher")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        d.giveColorlessMana(p, 6)
        assertNull(d.castSpell(p, crusher).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Ulamog's Crusher").size)
    }

    @Test fun restrictedManaDoesNotPayForOtherColorlessSpells() {
        val d = customScenario(EldraziTemple, "Sol Ring")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        assertNotNull("Sol Ring is colorless but not an Eldrazi", d.castSpell(p, ring).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
    }

    @Test fun mixedPoolNeverLaundersRestrictedManaIntoUnrelatedCosts() {
        val d = customScenario(EldraziTemple, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val ring = d.putCardInHand(p, "Sol Ring")
        val bears = d.putCardInHand(p, "Grizzly Bears")

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // A non-Eldrazi colorless spell must not borrow even one restricted {C}.
        assertNotNull(d.castSpell(p, ring).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // An unrestricted {C} pays for Sol Ring; both restricted units survive.
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // The same restriction holds for a colored spell's generic portion.
        d.giveMana(p, Color.GREEN)
        assertNotNull(d.castSpell(p, bears).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Grizzly Bears"))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
    }

    @Test fun restrictedManaPaysForColorlessNoncreatureEldraziKindredSpell() {
        // Echoes of Eternity is a {3}{C}{C}{C} Kindred Enchantment — Eldrazi.
        // The Temple restriction applies to colorless Eldrazi spells, not only creatures.
        val d = customScenario(EldraziTemple, "Echoes of Eternity")
        val p = d.player1
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val echoes = d.putCardInHand(p, "Echoes of Eternity")

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // Three unrestricted {C} plus two restricted {C} cannot pay six total.
        d.giveColorlessMana(p, 3)
        assertNotNull("One mana is still missing for the Kindred Eldrazi spell", d.castSpell(p, echoes).error)
        assertEquals(3, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // The exact same restricted mana must now be eligible to fund this
        // noncreature Eldrazi spell; neither generic nor {C} costs may be waived.
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, echoes).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Echoes of Eternity"))
    }

    @Test fun twoTempleRestrictedPoolsCombineForOneEldraziSpell() {
        // Each Temple independently generates two restricted units. They must
        // combine to pay an Eldrazi spell, never waive any part of its {8} cost.
        val d = customScenario(EldraziTemple, "Ulamog's Crusher")
        val p = d.player1
        val first = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val second = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val crusher = d.putCardInHand(p, "Ulamog's Crusher")

        assertNull(d.activate(p, first, index = 1).error)
        d.resolveAll()
        assertNull(d.activate(p, second, index = 1).error)
        d.resolveAll()
        assertEquals(4, d.manaPool(p).restrictedMana.size)

        // Four restricted + three unrestricted is still one short of {8}.
        d.giveColorlessMana(p, 3)
        assertNotNull(d.castSpell(p, crusher).error)
        assertEquals(4, d.manaPool(p).restrictedMana.size)
        assertEquals(3, d.manaPool(p).colorless)

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, crusher).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Ulamog's Crusher").size)
    }

    @Test fun restrictedManaCannotFundGenericPartOfColoredSpell() {
        val d = customScenario(EldraziTemple, "Grizzly Bears")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        d.giveMana(p, Color.GREEN)
        assertNotNull("Restricted {C} must not launder generic payment for colored spells", d.castSpell(p, bear).error)
    }

    @Test fun restrictedManaPaysRealColorlessEldraziActivatedAbility() {
        val probe = com.wingedsheep.sdk.dsl.card("Temple Colorless Eldrazi Ability Probe") {
            manaCost = "{3}"
            colorIdentity = ""
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            activatedAbility {
                cost = com.wingedsheep.sdk.dsl.Costs.Mana("{C}{C}")
                effect = com.wingedsheep.sdk.dsl.Effects.LoseLife(
                    1, com.wingedsheep.sdk.scripting.targets.EffectTarget.Controller
                )
            }
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        val eldrazi = d.putPermanentOnBattlefield(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val lifeBefore = d.getLifeTotal(p)

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertNull("A colorless Eldrazi activated ability may spend both restricted {C}",
            d.activate(p, eldrazi).error)
        d.resolveAll()
        assertEquals("Ability must resolve, not merely appear legal", lifeBefore - 1, d.getLifeTotal(p))
        assertTrue("Both restricted units must be consumed by the real ability",
            d.manaPool(p).restrictedMana.isEmpty())
    }

    @Test fun restrictedManaRejectsColoredEldraziActivatedAbilityWithoutLaundering() {
        val probe = com.wingedsheep.sdk.dsl.card("Temple Colored Eldrazi Ability Probe") {
            manaCost = "{G}"
            colorIdentity = "G"
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            activatedAbility {
                cost = com.wingedsheep.sdk.dsl.Costs.Mana("{C}{C}")
                effect = com.wingedsheep.sdk.dsl.Effects.LoseLife(
                    1, com.wingedsheep.sdk.scripting.targets.EffectTarget.Controller
                )
            }
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        val eldrazi = d.putPermanentOnBattlefield(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val lifeBefore = d.getLifeTotal(p)

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertNotNull("Eldrazi subtype alone cannot make a colored source eligible",
            d.activate(p, eldrazi).error)
        assertEquals("Rejected activation cannot spend restricted mana", 2,
            d.manaPool(p).restrictedMana.size)
        assertEquals(lifeBefore, d.getLifeTotal(p))

        d.giveColorlessMana(p, 2)
        assertNull("Unrestricted {C}{C} can activate the same colored Eldrazi",
            d.activate(p, eldrazi).error)
        d.resolveAll()
        assertEquals(lifeBefore - 1, d.getLifeTotal(p))
        assertEquals("Unrelated restricted mana must remain untouched", 2,
            d.manaPool(p).restrictedMana.size)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test fun restrictedManaRejectsNonEldraziColorlessActivatedAbility() {
        val probe = com.wingedsheep.sdk.dsl.card("Temple Colorless Golem Ability Probe") {
            manaCost = "{2}"
            colorIdentity = ""
            typeLine = "Artifact Creature — Golem"
            power = 1
            toughness = 1
            activatedAbility {
                cost = com.wingedsheep.sdk.dsl.Costs.Mana("{C}{C}")
                effect = com.wingedsheep.sdk.dsl.Effects.LoseLife(
                    1, com.wingedsheep.sdk.scripting.targets.EffectTarget.Controller
                )
            }
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        val golem = d.putPermanentOnBattlefield(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val lifeBefore = d.getLifeTotal(p)

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertNotNull("Colorless alone cannot satisfy Eldrazi-only ability spending",
            d.activate(p, golem).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertEquals(lifeBefore, d.getLifeTotal(p))
        d.giveColorlessMana(p, 2)
        assertNull(d.activate(p, golem).error)
        d.resolveAll()
        assertEquals(lifeBefore - 1, d.getLifeTotal(p))
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test fun restrictedManaPaysDevoidEldraziActivatedAbilityWithPrintedBlueCost() {
        val probe = com.wingedsheep.sdk.dsl.card("Temple Devoid Eldrazi Ability Probe") {
            manaCost = "{U}"
            colorIdentity = "U"
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            keywords(com.wingedsheep.sdk.core.Keyword.DEVOID)
            activatedAbility {
                cost = com.wingedsheep.sdk.dsl.Costs.Mana("{C}{C}")
                effect = com.wingedsheep.sdk.dsl.Effects.LoseLife(
                    1, com.wingedsheep.sdk.scripting.targets.EffectTarget.Controller
                )
            }
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        assertTrue("Devoid changes spell color, not commander color identity",
            d.cardRegistry.requireCard(probe.name).colors.isEmpty())
        val eldrazi = d.putPermanentOnBattlefield(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val lifeBefore = d.getLifeTotal(p)

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertNull("A Devoid Eldrazi's activated ability has a colorless source",
            d.activate(p, eldrazi).error)
        d.resolveAll()
        assertEquals(lifeBefore - 1, d.getLifeTotal(p))
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
    }

    @Test fun restrictedManaPaysGenericCostOfColorlessEldraziAbility() {
        // Oracle allows Temple mana to pay any portion of an eligible ability,
        // including generic {2}; it is not restricted to explicit {C} symbols.
        val probe = com.wingedsheep.sdk.dsl.card("Temple Generic Colorless Eldrazi Probe") {
            manaCost = "{3}"
            colorIdentity = ""
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            activatedAbility {
                cost = com.wingedsheep.sdk.dsl.Costs.Mana("{2}")
                effect = com.wingedsheep.sdk.dsl.Effects.LoseLife(
                    1, com.wingedsheep.sdk.scripting.targets.EffectTarget.Controller
                )
            }
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        val eldrazi = d.putPermanentOnBattlefield(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val lifeBefore = d.getLifeTotal(p)

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertNull("Temple's restricted mana may pay generic costs of colorless Eldrazi abilities",
            d.activate(p, eldrazi).error)
        d.resolveAll()
        assertEquals(lifeBefore - 1, d.getLifeTotal(p))
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
    }

    @Test fun restrictedManaRejectsGenericCostOfColoredEldraziAbility() {
        // A colored Eldrazi is still ineligible even when the ability's
        // payment is generic {2}, rather than an explicit {C}{C} requirement.
        val probe = com.wingedsheep.sdk.dsl.card("Temple Generic Colored Eldrazi Probe") {
            manaCost = "{G}"
            colorIdentity = "G"
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            activatedAbility {
                cost = com.wingedsheep.sdk.dsl.Costs.Mana("{2}")
                effect = com.wingedsheep.sdk.dsl.Effects.LoseLife(
                    1, com.wingedsheep.sdk.scripting.targets.EffectTarget.Controller
                )
            }
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        val eldrazi = d.putPermanentOnBattlefield(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val lifeBefore = d.getLifeTotal(p)

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertNotNull("Generic cost cannot launder restricted mana into a colored Eldrazi ability",
            d.activate(p, eldrazi).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertEquals(lifeBefore, d.getLifeTotal(p))

        d.giveColorlessMana(p, 2)
        assertNull("Unrestricted mana must still activate the colored Eldrazi ability",
            d.activate(p, eldrazi).error)
        d.resolveAll()
        assertEquals(lifeBefore - 1, d.getLifeTotal(p))
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertEquals(0, d.manaPool(p).colorless)
    }


    @Test fun restrictedManaPaysGenericCostOfDevoidEldraziSpellWithPrintedBlueCost() {
        // Devoid makes the SPELL colorless, despite its printed {U} and blue
        // commander color identity. Temple may pay {2}, never the {U} symbol.
        val probe = com.wingedsheep.sdk.dsl.card("Temple Devoid Eldrazi Spell Probe") {
            manaCost = "{2}{U}"
            colorIdentity = "U"
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
            keywords(com.wingedsheep.sdk.core.Keyword.DEVOID)
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        assertTrue(d.cardRegistry.requireCard(probe.name).colors.isEmpty())
        val spell = d.putCardInHand(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertNotNull("Restricted colorless cannot pay a printed {U} symbol",
            d.castSpell(p, spell).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        d.giveMana(p, Color.BLUE)
        assertNull("Devoid Eldrazi spell is colorless and may use Temple for generic {2}",
            d.castSpell(p, spell).error)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, probe.name).size)
    }

    @Test fun restrictedManaRejectsGenericCostOfColoredEldraziSpell() {
        // Eldrazi creature subtype is insufficient without Devoid/colorlessness.
        val probe = com.wingedsheep.sdk.dsl.card("Temple Colored Eldrazi Spell Probe") {
            manaCost = "{2}{U}"
            colorIdentity = "U"
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
        }
        val d = customScenario(EldraziTemple)
        val p = d.player1
        d.registerCard(probe)
        val spell = d.putCardInHand(p, probe.name)
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        d.giveMana(p, Color.BLUE)
        assertNotNull("Colored Eldrazi spell cannot spend Temple restricted mana for {2}",
            d.castSpell(p, spell).error)
        assertEquals("Rejected cast preserves both restricted mana units", 2,
            d.manaPool(p).restrictedMana.size)
        assertTrue(d.permanentsNamed(p, probe.name).isEmpty())

        d.giveColorlessMana(p, 2)
        assertNull("Two unrestricted mana plus blue must pay the same colored spell",
            d.castSpell(p, spell).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, probe.name).size)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertEquals(0, d.manaPool(p).colorless)
    }

}
