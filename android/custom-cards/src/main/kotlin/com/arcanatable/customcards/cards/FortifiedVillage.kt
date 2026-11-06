package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.OnEnterRun
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Fortified Village (Shadows over Innistrad #274) — the G/W member of the reveal-land cycle.
 *
 * Same shape as Argentum's Port Town / Furycalm Snarl: an "as this enters" replacement
 * ([OnEnterRun]) that offers to reveal a matching card from hand and otherwise makes it enter
 * tapped. It runs inline with the land entering — no stack, no priority — so it is decided before
 * anything can respond, and an instruction to put it onto the battlefield tapped still wins.
 */
val FortifiedVillage = card("Fortified Village") {
    typeLine = "Land"
    colorIdentity = "GW"
    oracleText = "As this land enters, you may reveal a Forest or Plains card from your hand. " +
        "If you don't, this land enters tapped.\n{T}: Add {G} or {W}."

    replacementEffect(
        OnEnterRun(
            Effects.MayRevealCardFromHand(
                filter = GameObjectFilter.Land.withAnySubtype("Forest", "Plains"),
                otherwise = Effects.EnterTapped(),
            )
        )
    )

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.WHITE)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "274"
        artist = "Cliff Childs"
        imageUri = "https://api.scryfall.com/cards/named?exact=Fortified%20Village&format=image"
    }
}
