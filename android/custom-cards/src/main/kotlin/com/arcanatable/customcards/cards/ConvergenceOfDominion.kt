package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/** The existing amount evaluator gates this reduction at activation time.
 * Do not wrap it in a conditional static: this pinned cost scanner only reads bare reductions.
 * Zone and ownership filters keep battlefield and hand abilities at their normal prices.
 */
val ConvergenceOfDominion = card("Convergence of Dominion") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Dynastic Command Node — As long as you control your commander, activated abilities of cards in your graveyard cost {2} less to activate. This effect can't reduce the mana in that ability's activation cost to less than one mana.\nTranslocation Protocols — {3}, {T}: Mill three cards."
    staticAbility {
        ability = ReduceActivatedAbilityCost(
            filter = GroupFilter(GameObjectFilter.Any.currentlyIn(Zone.GRAVEYARD).ownedByYou()),
            amount = DynamicAmounts.conditional(
                Conditions.YouControl(GameObjectFilter.Permanent.commander().ownedByYou()), 2, 0,
            ), manaFloor = 1,
            reach = ActivatedAbilityReach.ACTIVATED_BY_YOU,
        )
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{3}"), Costs.Tap)
        effect = Effects.Pipeline { mill(3) }
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "154"
        artist = "David Álvarez"
        imageUri = "https://cards.scryfall.io/normal/front/e/0/e03d8676-f693-4155-b75f-dcbe2890ccb0.jpg?1783920823"
    }
}
