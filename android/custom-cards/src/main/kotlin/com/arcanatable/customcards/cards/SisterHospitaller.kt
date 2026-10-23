package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
val SisterHospitaller = card("Sister Hospitaller") {
    manaCost = "{4}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Creature — Human Cleric"
    power = 3
    toughness = 2
    oracleText = "Medicus Ministorum — When this creature enters, return target creature card from your graveyard to the battlefield. You gain life equal to its mana value."
    triggeredAbility {
        trigger = Triggers.self.enters()
        val card = target(TargetFilter.CreatureInYourGraveyard)
        effect = Effects.Move(card, Zone.BATTLEFIELD, fromZone = Zone.GRAVEYARD) then
            Effects.GainLife(DynamicAmounts.manaValueOf(card))
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "141"; artist = "Games Workshop" }
}
