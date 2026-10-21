package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
val AssaultIntercessor = card("Assault Intercessor") {
    manaCost = "{1}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Creature — Astartes Warrior"
    power = 3
    toughness = 2
    oracleText = "First strike, menace\nChainsword — Whenever a creature an opponent controls dies, that player loses 2 life."
    keywords(Keyword.FIRST_STRIKE, Keyword.MENACE)
    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.opponentControls()).dies()
        effect = Effects.LoseLife(2, EffectTarget.PlayerRef(Player.TriggeringPlayer))
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "104"; artist = "Steve Argyle" }
}
