package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/** All three printed modes are cast-time choices with the appropriate distinct target filters. */
val WarpingWail = card("Warping Wail") {
    manaCost = "{1}{C}"
    colorIdentity = ""
    typeLine = "Instant"
    oracleText = "Choose one —\n" +
        "• Exile target creature with power or toughness 1 or less.\n" +
        "• Counter target sorcery spell.\n" +
        "• Create a 1/1 colorless Eldrazi Scion creature token. It has \"Sacrifice this token: Add {C}.\""

    spell {
        effect = ModalEffect.chooseOne(
            mode("Exile target creature with power or toughness 1 or less.") {
                val creature = target(TargetFilter(GameObjectFilter.Creature.powerOrToughnessAtMost(1)))
                effect = Effects.Exile(creature)
            },
            Mode.withTarget(
                effect = Effects.CounterSpell(),
                target = TargetObject(filter = TargetFilter(GameObjectFilter.Sorcery, zone = Zone.STACK)),
                description = "Counter target sorcery spell."
            ),
            Mode.noTarget(
                effect = Effects.CreateToken(
                    count = 1,
                    power = 1,
                    toughness = 1,
                    name = "Eldrazi Scion",
                    creatureTypes = setOf("Eldrazi", "Scion"),
                    activatedAbilities = listOf(
                        ActivatedAbility(
                            id = AbilityId.next(),
                            cost = Costs.SacrificeSelf,
                            effect = Effects.AddColorlessMana(1),
                            isManaAbility = true,
                            timing = TimingRule.ManaAbility,
                        ),
                    ),
                ),
                description = "Create a 1/1 colorless Eldrazi Scion creature token with sacrifice for {C}."
            ),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "812"
        artist = "Jason Felix"
    }
}
