package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.mechanics.layers.ProjectedValues
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.engine.mechanics.mana.isSatisfiedBy
import com.wingedsheep.engine.mechanics.mana.buildAbilityPaymentContext
import com.wingedsheep.engine.state.components.player.RestrictedManaEntry
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * #152: the second ability of Eldrazi Temple must not pay for a colored Eldrazi's
 * activated ability, even though the source has subtype Eldrazi.
 * Source-color is a separate fact from spell-color, and it must be projected.
 *
 * This is an ENGINE primitive regression suite, not proof that Eldrazi Temple
 * is implemented, registered, exported or playable in Arcana.
 */
class EldraziTempleManaRestrictionTest : FunSpec({
    val restricted = ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly("Eldrazi")
    val colorlessEldraziSpell = SpellPaymentContext(
        cardTypes = setOf(CardType.CREATURE), subtypes = setOf("Eldrazi"), isColorless = true
    )
    val coloredEldraziSpell = colorlessEldraziSpell.copy(isColorless = false)
    val colorlessOtherSpell = colorlessEldraziSpell.copy(subtypes = setOf("Human"))
    val colorlessEldraziAbility = SpellPaymentContext(
        isAbilityActivation = true,
        abilitySourceCardTypes = setOf(CardType.CREATURE),
        subtypes = setOf("Eldrazi"),
        isAbilitySourceColorless = true
    )
    val coloredEldraziAbility = colorlessEldraziAbility.copy(isAbilitySourceColorless = false)
    val colorlessOtherAbility = colorlessEldraziAbility.copy(subtypes = setOf("Human"))

    test("restricted colorless mana accepts a colorless Eldrazi spell and source ability") {
        restricted.isSatisfiedBy(colorlessEldraziSpell) shouldBe true
        restricted.isSatisfiedBy(colorlessEldraziAbility) shouldBe true
        restricted.isSatisfiedBy(colorlessEldraziSpell.copy(subtypes = setOf("ELDRAZI"))) shouldBe true
    }

    test("restricted colorless mana refuses colored Eldrazi spells and source abilities") {
        restricted.isSatisfiedBy(coloredEldraziSpell) shouldBe false
        restricted.isSatisfiedBy(coloredEldraziAbility) shouldBe false
        // The tempting old subtype-only restriction wrongly admits the colored ability.
        ManaRestriction.SubtypeSpellsOrAbilitiesOnly("Eldrazi")
            .isSatisfiedBy(coloredEldraziAbility) shouldBe true
    }

    test("restricted mana refuses other colorless spells, other abilities and contextless costs") {
        restricted.isSatisfiedBy(colorlessOtherSpell) shouldBe false
        restricted.isSatisfiedBy(colorlessOtherAbility) shouldBe false
        restricted.isSatisfiedBy(SpellPaymentContext()) shouldBe false
        restricted.isSatisfiedBy(SpellPaymentContext(isAbilityActivation = true)) shouldBe false
    }

    test("real mana pool pays exact two colorless symbols only for qualifying spend") {
        val pool = ManaPool(restrictedMana = List(2) { RestrictedManaEntry(null, restricted) })
        val cost = ManaCost.parse("{C}{C}")
        pool.canPay(cost, colorlessEldraziSpell) shouldBe true
        pool.canPay(cost, colorlessEldraziAbility) shouldBe true
        pool.canPay(cost, coloredEldraziSpell) shouldBe false
        pool.canPay(cost, coloredEldraziAbility) shouldBe false
        pool.canPay(cost, colorlessOtherSpell) shouldBe false
        pool.canPay(cost, colorlessOtherAbility) shouldBe false
        pool.canPay(cost) shouldBe false
    }

    test("mixed unrestricted and Temple mana cannot launder an ineligible payment") {
        val oneOfEach = ManaPool(
            colorless = 1,
            restrictedMana = listOf(RestrictedManaEntry(null, restricted))
        )
        val twoColorless = ManaCost.parse("{C}{C}")
        // An ineligible payment has only one usable {C}; it cannot spend the
        // Temple unit merely because an unrestricted unit is also present.
        oneOfEach.canPay(twoColorless, coloredEldraziSpell) shouldBe false
        oneOfEach.canPay(twoColorless, coloredEldraziAbility) shouldBe false
        oneOfEach.canPay(twoColorless, colorlessOtherSpell) shouldBe false
        oneOfEach.pay(twoColorless, coloredEldraziAbility) shouldBe null
        // The same two units are legal for a colorless Eldrazi spell or ability.
        oneOfEach.canPay(twoColorless, colorlessEldraziSpell) shouldBe true
        oneOfEach.canPay(twoColorless, colorlessEldraziAbility) shouldBe true
    }

    test("the restriction survives serialization and retains subtype identity") {
        val json = kotlinx.serialization.json.Json
        val serialized = json.encodeToString(ManaRestriction.serializer(), restricted)
        val restored = json.decodeFromString(ManaRestriction.serializer(), serialized)
        restored shouldBe restricted
        restricted.description.contains("colorless Eldrazi") shouldBe true
    }
    test("battlefield subtype replacement rejects mana spent on a former Eldrazi's ability") {
        val sourceCard = card("Temple Projected Eldrazi Source Probe") {
            manaCost = "{2}"
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
        }
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(sourceCard))
        driver.initMirrorMatch(Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val sourceId = driver.putCreatureOnBattlefield(driver.player1, sourceCard.name)
        val printed = driver.state.getEntity(sourceId)!!.get<CardComponent>()!!

        // Model the authoritative post-layer-4 characteristic directly. An
        // Eldrazi that became only a Frog is not an Eldrazi ability source.
        fun projectedContext(subtypes: Set<String>, colors: Set<String> = emptySet()): SpellPaymentContext {
            val projection = ProjectedState(
                driver.state,
                mapOf(sourceId to ProjectedValues(
                    types = setOf("CREATURE"), subtypes = subtypes, colors = colors
                ))
            )
            return buildAbilityPaymentContext(printed, projection, sourceId, ability = null)
        }
        restricted.isSatisfiedBy(projectedContext(setOf("Eldrazi"))) shouldBe true
        restricted.isSatisfiedBy(projectedContext(setOf("Frog"))) shouldBe false
        restricted.isSatisfiedBy(projectedContext(emptySet())) shouldBe false
        // Printed as colorless Eldrazi, but a continuous color-changing effect
        // makes the battlefield ability source BLUE: Temple mana must be denied.
        val nowBlue = projectedContext(setOf("Eldrazi"), setOf("BLUE"))
        nowBlue.isAbilitySourceColorless shouldBe false
        restricted.isSatisfiedBy(nowBlue) shouldBe false
        ManaPool(restrictedMana = List(2) { RestrictedManaEntry(null, restricted) })
            .canPay(ManaCost.parse("{C}{C}"), nowBlue) shouldBe false
    }

})
