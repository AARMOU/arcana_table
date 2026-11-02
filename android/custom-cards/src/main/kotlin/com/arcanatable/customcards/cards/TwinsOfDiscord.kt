package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.EffectChoice
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

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
 * **Bloodthirst grant.** A real, grantable bloodthirst keyword from engine patch 0030:
 * `GrantKeyword(KeywordAbility.grantedBloodthirst(2), …)` over other (`excludeSelf`) colorless
 * creatures you control. The engine reads the entering creature's projected keywords as it would
 * exist on the battlefield (CR 614.12), so a colorless creature entering while the Twins is out has
 * bloodthirst 2 and, if an opponent was dealt damage this turn, enters with two +1/+1 counters (no
 * trigger). Colour is the entering creature's projected colour (devoid counts as colorless); two
 * Twins are two instances (2 + 2); a lose-all-abilities effect that applies to the creature removes
 * the grant in the usual layer order; "an opponent" is relative to the creature's controller.
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
    staticAbility {
        ability = GrantKeyword(
            KeywordAbility.grantedBloodthirst(2),
            GroupFilter(colorlessCreatureYouControl, excludeSelf = true),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "37"
        artist = "Lorenzo Lanfranconi"
        imageUri = "https://cards.scryfall.io/normal/front/b/3/b3c237a5-1d2a-457a-be4e-c07483b31799.jpg?1783911427"
    }
}
