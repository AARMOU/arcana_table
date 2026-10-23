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

val SneakAttack = card("Sneak Attack") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Enchantment"
    oracleText = "{R}: You may put a creature card from your hand onto the battlefield. That creature gains " +
        "haste. Sacrifice the creature at the beginning of the next end step."

    activatedAbility {
        cost = Costs.Mana("{R}")
        effect = Effects.Pipeline {
            val candidates = gather(CardSource.FromZone(Zone.HAND, Player.You, GameObjectFilter.Creature))
            val putting = chooseUpTo(1, from = candidates)
            move(putting, CardDestination.ToZone(Zone.BATTLEFIELD, Player.You))
            ifNotEmpty(putting) {
                run(Effects.GrantKeyword(keyword = Keyword.HASTE, target = putting.asTarget, duration = Duration.Permanent))
                run(Effects.CreateDelayedTrigger(step = Step.END, effect = Effects.SacrificeTarget(putting.asTarget)))
            }
        }
        description = "{R}: You may put a creature card from your hand onto the battlefield. That creature " +
            "gains haste. Sacrifice the creature at the beginning of the next end step."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "212"
        imageUri = "https://cards.scryfall.io/normal/front/d/0/d07dc95d-82a8-4a58-8ea2-d4513bd7316d.jpg?1783946324"
        artist = "Jerry Tiritilli"
    }
}
