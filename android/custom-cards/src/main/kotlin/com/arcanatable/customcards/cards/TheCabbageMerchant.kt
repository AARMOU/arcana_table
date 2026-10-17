package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

val TheCabbageMerchant = card("The Cabbage Merchant") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Human Citizen"
    power = 2
    toughness = 2
    oracleText = "Whenever an opponent casts a noncreature spell, create a Food token. (It's an artifact with " +
        "\"{2}, {T}, Sacrifice this token: You gain 3 life.\")\nWhenever a creature deals combat damage to you, " +
        "sacrifice a Food token.\nTap two untapped Foods you control: Add one mana of any color."

    triggeredAbility {
        trigger = Triggers.anOpponent.casts(GameObjectFilter.Noncreature)
        effect = Effects.CreateFood()
        description = "Whenever an opponent casts a noncreature spell, create a Food token."
    }

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature).dealsCombatDamage(Recipient.Player(Player.You))
        effect = Effects.Sacrifice(GameObjectFilter.Artifact.withSubtype("Food").token(), 1, EffectTarget.Controller)
        description = "Whenever a creature deals combat damage to you, sacrifice a Food token."
    }

    activatedAbility {
        cost = Costs.TapPermanents(2, GameObjectFilter.Artifact.withSubtype("Food").untapped().youControl())
        effect = Effects.AddAnyColorMana()
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "Tap two untapped Foods you control: Add one mana of any color."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "134"
        artist = "Patrick Gañas"
    }
}
