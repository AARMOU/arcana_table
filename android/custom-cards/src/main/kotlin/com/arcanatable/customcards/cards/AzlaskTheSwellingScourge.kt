package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Azlask, the Swelling Scourge (M3C #5).
 *
 * Experience counters belong to the player, not the source permanent. The death trigger
 * intentionally includes Azlask itself and uses the dying creature's last-known controller/color.
 *
 * Annihilator N in the pinned SDK is display vocabulary; like Eldrazi Ravager/Artisan of Kozilek
 * its real behavior is an attack trigger. Grant that trigger to each Scion/Spawn present when the
 * ability resolves; granting the Keyword flag also makes the temporary keyword visible.
 * Both grants expire at end of turn. Nothing here creates a perpetual static anthem.
 */
val AzlaskTheSwellingScourge = card("Azlask, the Swelling Scourge") {
    manaCost = "{3}"
    colorIdentity = "WUBRG"
    typeLine = "Legendary Creature — Eldrazi"
    power = 2
    toughness = 2
    oracleText = "Whenever Azlask, the Swelling Scourge or another colorless creature you " +
        "control dies, you get an experience counter.\n" +
        "{W}{U}{B}{R}{G}: Creatures you control get +X/+X until end of turn, where X is the " +
        "number of experience counters you have. Scions and Spawns you control gain " +
        "indestructible and annihilator 1 until end of turn."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.exactlyColors(0).youControl()).dies()
        effect = Effects.AddCounters(CounterType.EXPERIENCE, 1, EffectTarget.Controller)
    }

    activatedAbility {
        cost = Costs.Mana("{W}{U}{B}{R}{G}")

        // Read experience counters as the ability resolves, then lock in the bonus for
        // the creatures currently controlled; creatures arriving later are not affected.
        val x = DynamicAmounts.playerCounterCount(CounterType.EXPERIENCE)
        val annihilatorOne = TriggeredAbility.create(
            trigger = Triggers.self.attacks(),
            effect = Effects.Sacrifice(
                GameObjectFilter.Permanent,
                1,
                EffectTarget.PlayerRef(Player.DefendingPlayer),
            ),
            descriptionOverride = "Annihilator 1",
        )
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.ModifyStats(x, x, EffectTarget.IterationEntity, Duration.EndOfTurn),
        ) then Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.withAnySubtype("Scion", "Spawn").youControl()),
            Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantKeyword(Keyword.ANNIHILATOR, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantTriggeredAbility(
                    ability = annihilatorOne,
                    target = EffectTarget.IterationEntity,
                    duration = Duration.EndOfTurn,
                ),
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "5"
        artist = "Alexander Mokhov"
    }
}
