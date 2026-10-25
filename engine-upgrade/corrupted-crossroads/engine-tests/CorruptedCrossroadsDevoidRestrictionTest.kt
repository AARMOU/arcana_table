package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.engine.mechanics.mana.isSatisfiedBy
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * A colorless spell does not necessarily have devoid. DevoidSpellsOnly
 * matches the keyword, not the derived color of a spell.
 */
class CorruptedCrossroadsDevoidRestrictionTest : FunSpec({
    val restriction = ManaRestriction.DevoidSpellsOnly

    test("printed devoid creature is eligible for colored restricted mana") {
        restriction.isSatisfiedBy(SpellPaymentContext(
            cardTypes = setOf(CardType.CREATURE), isColorless = true, isDevoid = true
        )) shouldBe true
    }

    test("ordinary colorless artifact without devoid is not eligible") {
        restriction.isSatisfiedBy(SpellPaymentContext(
            cardTypes = setOf(CardType.ARTIFACT), isColorless = true
        )) shouldBe false
    }

    test("ordinary colored nondevoid spell is not eligible") {
        restriction.isSatisfiedBy(SpellPaymentContext(
            cardTypes = setOf(CardType.SORCERY), isColorless = false
        )) shouldBe false
    }

    test("even a devoid source does not license activating abilities") {
        restriction.isSatisfiedBy(SpellPaymentContext(
            isAbilityActivation = true, isDevoid = true
        )) shouldBe false
    }

    test("a face-down colorless spell without the devoid keyword is ineligible") {
        restriction.isSatisfiedBy(SpellPaymentContext.faceDownCast()) shouldBe false
    }

    test("failure to supply spell card types does not accidentally allow the restriction") {
        restriction.isSatisfiedBy(SpellPaymentContext(isDevoid = true)) shouldBe false
    }
})
