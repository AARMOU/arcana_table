package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * The targeted stack object's controller must lose life even when that object
 * cannot be countered. Compute this while the targeted object is still present;
 * countering it first would erase the controller reference.
 */
val DenyTheWitch = card("Deny the Witch") {
    manaCost = "{1}{W}{U}{B}"
    colorIdentity = "WUB"
    typeLine = "Instant"
    oracleText = "Counter target spell, activated ability, or triggered ability. " +
        "Its controller loses life equal to the number of creatures you control."

    spell {
        target(TargetFilter.SpellOrAbilityOnStack)
        effect = Effects.LoseLife(
            DynamicAmounts.battlefield(Player.You, GameObjectFilter.Creature).count(),
            EffectTarget.TargetController
        ) then Effects.CounterSpellOrAbility()
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "116"
        artist = "Games Workshop"
    }
}
