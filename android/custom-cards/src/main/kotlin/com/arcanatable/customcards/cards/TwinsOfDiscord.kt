package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.EffectChoice
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Twins of Discord — Modern Horizons 3 Commander #37 (Eldrazi Incursion). {7} Creature — Eldrazi 8/6.
 *
 * **Attack line.** `Triggers.you.attacks()` is "whenever you attack": once per declaration of one or
 * more attackers, whether or not the Twins attacks. Odd or even is chosen as the ability resolves
 * (`Effects.ChooseAction`, two labelled options). Each option is a `CantBlockGroup` until end of
 * turn over *all* creatures (both players') whose mana value has that parity; `manaValueIsEven()`
 * reads the current mana value, so zero (tokens, face-down creatures) is even. "Can't block"
 * changes no characteristic, so per CR 611.2c it is a rule-modifying effect whose affected set is
 * not locked in on resolution: the engine's `CantBlockGroup` keeps a dynamic group filter, so a
 * creature that enters (or changes mana value) later that turn is covered too.
 *
 * **Bloodthirst grant.** Bloodthirst N (CR 702.54a) is the replacement effect "If an opponent was
 * dealt damage this turn, this permanent enters with N +1/+1 counters on it." The engine has no
 * bloodthirst keyword, so the granted ability is authored as the replacement it stands for, applied
 * from the Twins to each *other* colorless creature entering under its controller's control —
 * the same battlefield-source shape as Metallic Mimic. Per CR 614.12 the grant from a permanent
 * already on the battlefield applies to a creature as it enters, so this is the same event:
 * counters are there on entry (no trigger), it works for spells, tokens and reanimation, two Twins
 * add 2 + 2, and colour is read from the entering creature's characteristics (devoid counts as
 * colorless). `otherOnly = true` keeps the Twins from countering itself. "An opponent" is relative
 * to the Twins' controller, who is also the entering creature's controller.
 */
private val colorlessCreatureYouControl: GameObjectFilter =
    GameObjectFilter.Creature.withCardPredicate(CardPredicate.IsColorless).youControl()

val TwinsOfDiscord = card("Twins of Discord") {
    manaCost = "{7}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 8
    toughness = 6
    oracleText = "Whenever you attack, choose odd or even. Creatures with mana value of that quality can't block " +
        "this turn. (Zero is even.)\nEach other colorless creature you control has bloodthirst 2. (If an opponent " +
        "was dealt damage this turn, that creature enters with two additional +1/+1 counters on it.)"

    triggeredAbility {
        trigger = Triggers.you.attacks()
        effect = Effects.ChooseAction(
            listOf(
                EffectChoice("Odd", Effects.CantBlockGroup(GroupFilter(GameObjectFilter.Creature.manaValueIsOdd()))),
                EffectChoice("Even", Effects.CantBlockGroup(GroupFilter(GameObjectFilter.Creature.manaValueIsEven()))),
            )
        )
        description = "Whenever you attack, choose odd or even. Creatures with mana value of that quality can't " +
            "block this turn. (Zero is even.)"
    }

    // Each other colorless creature you control has bloodthirst 2.
    replacementEffect(
        EntersWithCounters(
            count = 2,
            otherOnly = true,
            condition = Conditions.CompareAmounts(
                DynamicAmounts.damageReceivedThisTurn(Player.EachOpponent),
                ComparisonOperator.GTE,
                1,
            ),
            appliesTo = EventPattern.ZoneChangeEvent(filter = colorlessCreatureYouControl, to = Zone.BATTLEFIELD),
        )
    )

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "37"
        artist = "Lorenzo Lanfranconi"
        imageUri = "https://cards.scryfall.io/normal/front/b/3/b3c237a5-1d2a-457a-be4e-c07483b31799.jpg?1783911427"
    }
}
