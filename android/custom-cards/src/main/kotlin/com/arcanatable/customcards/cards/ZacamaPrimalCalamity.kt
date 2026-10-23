package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val ZacamaPrimalCalamity = card("Zacama, Primal Calamity") {
    manaCost = "{6}{R}{G}{G}{W}"
    colorIdentity = "RGW"
    typeLine = "Legendary Creature — Elder Dinosaur"
    power = 9
    toughness = 9
    oracleText = "Vigilance, reach, trample\nWhen Zacama enters, if you cast it, untap all lands you control.\n{2}{R}: Zacama deals 3 damage to target creature.\n{2}{G}: Destroy target artifact or enchantment.\n{2}{W}: You gain 3 life."

    keywords(Keyword.VIGILANCE, Keyword.REACH, Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = Conditions.TriggeringEntityWasCast
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Land.youControl()),
            Effects.Untap(EffectTarget.IterationEntity),
        )
        description = "When Zacama enters, if you cast it, untap all lands you control."
    }

    activatedAbility {
        cost = Costs.Mana("{2}{R}")
        val creature = target(TargetFilter.Creature)
        effect = Effects.DealDamage(3, creature, EffectTarget.Self)
        description = "{2}{R}: Zacama deals 3 damage to target creature."
    }

    activatedAbility {
        cost = Costs.Mana("{2}{G}")
        val permanent = target(TargetFilter.ArtifactOrEnchantment)
        effect = Effects.Destroy(permanent)
        description = "{2}{G}: Destroy target artifact or enchantment."
    }

    activatedAbility {
        cost = Costs.Mana("{2}{W}")
        effect = Effects.GainLife(3)
        description = "{2}{W}: You gain 3 life."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "174"
        imageUri = "https://api.scryfall.com/cards/named?exact=Zacama%2C%20Primal%20Calamity&format=image"
    }
}
