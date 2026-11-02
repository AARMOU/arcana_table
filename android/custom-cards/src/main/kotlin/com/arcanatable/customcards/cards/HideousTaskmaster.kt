package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Hideous Taskmaster (M3C #57).
 *
 * The cast trigger uses the corpus's one-per-opponent distribution (In the Darkness Bind Them
 * chapter IV, Tempted by the Oriq): `dynamicMaxCount = PlayerCount(EachOpponent)` +
 * `differentControllers = true`, `optional` for each "up to one". It is a cast trigger, so it is
 * on the stack above Taskmaster and resolves even if Taskmaster is countered. Engine patch 0032
 * binds each target to the opponent it was chosen for: a creature that opponent no longer controls
 * at resolution is an illegal target (CR 608.2b) and is skipped, while the others still resolve.
 *
 * Each target is stolen until end of turn, untapped, and given trample, haste and annihilator 1
 * until end of turn. Annihilator N is display-only in the SDK (as on Ulamog's Crusher and Azlask),
 * so the keyword flag is granted for display and the real "defending player sacrifices a
 * permanent" attack trigger is granted alongside it; Taskmaster's own annihilator 1 is the same
 * trigger printed on the card. Devoid makes it colorless; the {R} must still be paid.
 */
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

    // Annihilator 1 as a grantable attack trigger (built inside card { } so it gets an ability id).
    val annihilatorOne = TriggeredAbility.create(
        trigger = Triggers.self.attacks(),
        effect = Effects.Sacrifice(GameObjectFilter.Permanent, 1, EffectTarget.PlayerRef(Player.DefendingPlayer)),
        descriptionOverride = "Annihilator 1",
    )

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
            Effects.GrantKeyword(Keyword.TRAMPLE, EffectTarget.ContextTarget(0), Duration.EndOfTurn),
            Effects.GrantKeyword(Keyword.HASTE, EffectTarget.ContextTarget(0), Duration.EndOfTurn),
            Effects.GrantKeyword(Keyword.ANNIHILATOR, EffectTarget.ContextTarget(0), Duration.EndOfTurn),
            Effects.GrantTriggeredAbility(annihilatorOne, EffectTarget.ContextTarget(0), Duration.EndOfTurn),
        )
        description = "When you cast this spell, for each opponent, gain control of up to one target " +
            "creature that player controls until end of turn. Untap those creatures. They gain " +
            "trample, haste, and annihilator 1 until end of turn."
    }

    // Annihilator 1 — the lowering of the display-only keyword ability above.
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
