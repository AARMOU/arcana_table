package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Blood for the Blood God! — 40K #108.
 */
val BloodForTheBloodGod = card("Blood for the Blood God!") {
    manaCost = "{8}{B}{B}{R}"
    colorIdentity = "BR"
    typeLine = "Instant"
    oracleText = "This spell costs {1} less to cast for each creature that died this turn.\nDiscard your hand, then draw eight cards. Blood for the Blood God! deals 8 damage to each opponent. Exile Blood for the Blood God!"

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.SelfCast,
            modification = CostModification.ReduceGenericBy(
                CostReductionSource.Dynamic(DynamicAmounts.creaturesDiedThisTurn(Player.Each))
            )
        )
    }

    spell {
        selfExile()
        effect = Patterns.Hand.discardHand(EffectTarget.Controller) then
            Effects.DrawCards(8) then
            Effects.DealDamage(8, EffectTarget.PlayerRef(Player.EachOpponent))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "108"
        artist = "Artur Nakhodkin"
        imageUri = "https://cards.scryfall.io/normal/front/f/4/f41d59f6-7fbe-4e67-8098-190b01281fe0.jpg?1783920862"
    }
}
