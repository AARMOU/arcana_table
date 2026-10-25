package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** M3C #57. Target distribution follows pinned In the Darkness Bind Them chapter IV. */
val HideousTaskmaster = card("Hideous Taskmaster") {
    manaCost = "{6}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Eldrazi"
    power = 7
    toughness = 2
    oracleText = "Devoid (This card has no color.)\n" +
        "When you cast this spell, for each opponent, gain control of up to one target creature " +
        "that player controls until end of turn. Untap those creatures. They gain trample, haste, " +
        "and annihilator 1 until end of turn.\n" +
        "Trample, haste, annihilator 1 (Whenever this creature attacks, defending player sacrifices a permanent of their choice.)"
    keywords(Keyword.DEVOID, Keyword.TRAMPLE, Keyword.HASTE)
    keywordAbility(KeywordAbility.annihilator(1))

    triggeredAbility {
        trigger = Triggers.self.isCast()
        targets(
            TargetFilter.CreatureOpponentControls,
            optional = true,
            dynamicMaxCount = DynamicAmounts.playerCount(Player.EachOpponent),
            differentControllers = true,
        )
        effect = Effects.ForEachTarget(
            Effects.GainControl(EffectTarget.ContextTarget(0), Duration.EndOfTurn),
            Effects.Untap(EffectTarget.ContextTarget(0)),
            Effects.GrantKeyword(Keyword.TRAMPLE, EffectTarget.ContextTarget(0)),
            Effects.GrantKeyword(Keyword.HASTE, EffectTarget.ContextTarget(0)),
            Effects.GrantTriggeredAbility(
                grantedTriggeredAbility {
                    trigger = Triggers.self.attacks()
                    effect = Effects.Sacrifice(GameObjectFilter.Permanent, 1, EffectTarget.PlayerRef(Player.DefendingPlayer))
                    description = "Annihilator 1"
                },
                EffectTarget.ContextTarget(0),
                Duration.EndOfTurn,
            ),
        )
    }
    // As with Ulamog's Crusher, the numeric keyword's attack trigger must be executable.
    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.Sacrifice(GameObjectFilter.Permanent, 1, EffectTarget.PlayerRef(Player.DefendingPlayer))
        description = "Annihilator 1"
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "57"
        artist = "Helge C. Balzer"
        imageUri = "https://cards.scryfall.io/normal/front/b/a/ba7191d0-d567-4814-8a92-52e96aa7660f.jpg?1783911422"
    }
}
