package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaRestriction

/**
 * Eldrazi Temple (#152), Eldrazi Incursion and Eldrazi Unbound.
 *
 * Needs ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly from engine patch
 * engine-upgrade/eldrazi-temple/patches/0001. Registered for B140 together with
 * runtime jars rebuilt with that patch (engine-upgrade/build140); it does not load
 * on jars without it.
 */
val EldraziTemple = card("Eldrazi Temple") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{T}: Add {C}{C}. Spend this mana only to cast colorless Eldrazi spells or activate abilities of colorless Eldrazi."

    // Unrestricted mode: this colorless can fund any legal cost.
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C}."
    }

    // Restricted mode: includes the activated ability SOURCE's colorlessness,
    // not merely the spell color or the Eldrazi subtype.
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(
            2,
            restriction = ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly("Eldrazi")
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C}{C}. Spend this mana only to cast colorless Eldrazi spells or activate abilities of colorless Eldrazi."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "339"
        artist = "Jorge Jacinto"
        imageUri = "https://cards.scryfall.io/normal/front/4/f/4f0ee055-1b1e-4ffa-9eb5-9ce7e8965f71.jpg?1717646618"
    }
}
