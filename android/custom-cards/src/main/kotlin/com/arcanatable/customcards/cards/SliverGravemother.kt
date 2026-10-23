package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

private fun encoreEffect(): Effect = Effects.ForEachPlayer(
    players = Player.EachOpponent,
    effect = Effects.CreateTokenCopyOfTarget(
        target = EffectTarget.Self,
        count = DynamicAmount.Fixed(1),
        attacking = true,
        addedKeywords = setOf(Keyword.HASTE),
        sacrificeAtStep = Step.END,
        controller = EffectTarget.PlayerRef(Player.OwnerOfSource),
        attackedPlayer = EffectTarget.PlayerRef(Player.You),
    ),
)

private fun graveyardEncore(cost: String, xDefinedAs: DynamicAmount? = null) = ActivatedAbility(
    cost = Costs.Composite(Costs.Mana(cost), Costs.ExileSelf),
    effect = encoreEffect(),
    timing = TimingRule.SorcerySpeed,
    activateFromZone = Zone.GRAVEYARD,
    xDefinedAs = xDefinedAs,
)

/** Sliver Gravemother. Encore's token copies are generated once per opponent, each attacking
 * that opponent with haste and a delayed sacrifice trigger. */
val SliverGravemother = card("Sliver Gravemother") {
    manaCost = "{W}{U}{B}{R}{G}"
    colorIdentity = "WUBRG"
    typeLine = "Legendary Creature — Sliver"
    power = 6
    toughness = 6
    oracleText = "The \"legend rule\" doesn't apply to Slivers you control. Each Sliver creature " +
        "card in your graveyard has encore {X}, where X is its mana value. Encore {5}"

    staticAbility {
        ability = LegendRuleDoesNotApplyTo(GameObjectFilter.Permanent.withSubtype("Sliver").youControl())
    }

    staticAbility {
        ability = GrantActivatedAbility(
            ability = graveyardEncore("{X}", DynamicAmounts.sourceManaValue()),
            filter = GroupFilter(
                GameObjectFilter.Creature.withSubtype("Sliver").ownedByYou(),
            ),
            recipientZone = Zone.GRAVEYARD,
        )
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{5}"), Costs.ExileSelf)
        effect = encoreEffect()
        timing = TimingRule.SorcerySpeed
        activateFromZone = Zone.GRAVEYARD
        description = "Encore {5}"
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "707"
        artist = "Chris Rahn"
        imageUri = "https://api.scryfall.com/cards/named?exact=Sliver%20Gravemother&format=image"
    }
}
