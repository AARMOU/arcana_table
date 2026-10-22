package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Modal ETB makes the target choice when the trigger is placed on the stack. */
val PrimarisEliminator = card("Primaris Eliminator") {
    manaCost = "{4}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Astartes Warrior"
    power = 3
    toughness = 2
    oracleText = "When this creature enters, choose one —\n" +
        "• Executioner Round — Destroy target creature.\n" +
        "• Hyperfrag Round — Creatures target player controls get -2/-2 until end of turn."

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = ModalEffect.chooseOne(
            mode("Executioner Round — Destroy target creature.") {
                val victim = target(TargetFilter.Creature)
                effect = Effects.Destroy(victim)
            },
            mode("Hyperfrag Round — Creatures target player controls get -2/-2 until end of turn.") {
                val player = target(Targets.Player)
                effect = Effects.ForEachInGroup(
                    GroupFilter(GameObjectFilter.Creature.targetPlayerControls(player)),
                    Effects.ModifyStats(-2, -2, EffectTarget.IterationEntity),
                )
            },
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "50"
        artist = "Logan Feliciano"
    }
}
